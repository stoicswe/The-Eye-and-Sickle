package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The character directory, and the one place a signed home binding converges into local state — "DNS for
 * your character" ({@code docs/architecture/09-player-state-portability.md} §4).
 *
 * <h2>Two operations: publish (ingest) and resolve</h2>
 *
 * <ul>
 *   <li>{@link #ingest} takes an untrusted, published home record, <strong>verifies it, then</strong>
 *       converges it — always in that order, so a {@link CharacterHomeRecord} handed to {@link #accept}
 *       is already trusted, because the only way to obtain one is through {@link
 *       CharacterHomeRecordVerifier}.
 *   <li>{@link #resolveHomes} answers "where are DID {@code D}'s characters?" — the client-facing lookup a
 *       player on a new machine uses to find its homes from its DID alone. No state moves; a pointer is
 *       read (09 §4).
 * </ul>
 *
 * <h2>Convergence rule: last-writer-wins on a signed sequence, never a clock</h2>
 *
 * A home binding is self-asserted, non-adversarial location data (08 §2): only the home server can sign
 * it, so accepting the highest sequence it has signed just lets it update its own binding. {@link
 * #accept} implements exactly that — strictly-higher supersedes, equal-and-identical is a harmless
 * refresh, lower is refused as stale (a possible rollback). The comparison is on the signed sequence the
 * home controls; a wall clock, which an attacker controls and which self-hosted servers legitimately
 * disagree about, is never consulted.
 *
 * <p>One case a self-descriptor never faces: two <em>different</em> home servers asserting the same
 * {@code (account, slot)} at the same sequence. That is a fork, refused as {@link
 * HomeAcceptOutcome#IGNORED_CONFLICT} rather than overwritten — the stored binding stands. Which of a
 * genuine simultaneous over-creation ultimately wins, and how the loser is told, is open question
 * Q-cap-race (09 §9); this service takes the safe, non-destructive side of it.
 */
@Service
public class CharacterDirectoryService {

    private final CharacterDirectoryRepository repository;
    private final CharacterHomeRecordVerifier verifier;
    private final CharacterDirectoryProperties properties;

    CharacterDirectoryService(
            CharacterDirectoryRepository repository,
            CharacterHomeRecordVerifier verifier,
            CharacterDirectoryProperties properties) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.verifier = Objects.requireNonNull(verifier, "verifier");
        this.properties = Objects.requireNonNull(properties, "properties");
    }

    /**
     * Verifies a raw published home record and, if it is valid, converges it into the directory.
     *
     * @param rawEnvelope the received JSON, verbatim
     * @param now the instant to record against
     * @return the verification verdict and, if accepted, what convergence did with it
     */
    public IngestResult ingest(String rawEnvelope, Instant now) {
        Objects.requireNonNull(now, "now");
        CharacterHomeVerification verification = verifier.verify(rawEnvelope);
        if (!verification.isAccepted()) {
            return new IngestResult(verification, null);
        }
        HomeAcceptOutcome outcome = accept(verification.record(), now);
        return new IngestResult(verification, outcome);
    }

    /**
     * Converges a verified home binding into the directory by the sequence-number rule.
     *
     * <p>Runs in one transaction so the "is it known, and is it newer" decision and the write it implies
     * cannot interleave with a concurrent ingest of the same {@code (account, slot)} in a way that
     * regresses the stored sequence. The monotonic {@code WHERE sequence_number < :seq} predicate and the
     * database's anti-rollback trigger are the two independent guarantees that it never does.
     *
     * @param record a verified record
     * @param now the instant to record as last-seen
     * @return what happened
     */
    @Transactional
    public HomeAcceptOutcome accept(CharacterHomeRecord record, Instant now) {
        Objects.requireNonNull(record, "record");
        Objects.requireNonNull(now, "now");

        Optional<CharacterHomeEntry> stored = repository.findByAccountAndSlot(record.accountDid(), record.slot());
        if (stored.isEmpty()) {
            // Unknown binding. Bound directory growth from gossip flooding before inserting.
            if (repository.count() >= properties.maxDirectorySize()) {
                return HomeAcceptOutcome.IGNORED_AT_CAPACITY;
            }
            if (repository.insertNew(record, now) == 1) {
                return HomeAcceptOutcome.ACCEPTED_NEW;
            }
            // A concurrent ingest won the insert; fall through and treat it as an existing binding.
            stored = repository.findByAccountAndSlot(record.accountDid(), record.slot());
            if (stored.isEmpty()) {
                // Vanishingly rare: inserted-then-deleted between the two reads. Nothing to converge onto.
                return HomeAcceptOutcome.IGNORED_STALE;
            }
        }

        CharacterHomeEntry current = stored.get();
        long storedSequence = current.sequenceNumber();
        if (record.sequenceNumber() > storedSequence) {
            // Strictly newer: advance. If a concurrent writer advanced past us first, updateIfNewer matches
            // nothing and the newer stored binding rightly stands.
            return repository.updateIfNewer(record, now) == 1
                    ? HomeAcceptOutcome.ACCEPTED_UPDATED
                    : HomeAcceptOutcome.IGNORED_STALE;
        }
        if (record.sequenceNumber() == storedSequence) {
            // Equal sequence: a refresh only if it is byte-for-byte the same signed binding. A different
            // signature at the same sequence is a fork (two home servers claiming the slot); the stored one
            // stands rather than being silently overwritten by whoever announced last.
            if (Arrays.equals(record.signature(), current.signature())) {
                repository.touchLastSeen(record.accountDid(), record.slot(), now);
                return HomeAcceptOutcome.IGNORED_DUPLICATE;
            }
            return HomeAcceptOutcome.IGNORED_CONFLICT;
        }
        return HomeAcceptOutcome.IGNORED_STALE;
    }

    /**
     * Resolves an account's characters and their homes — the client-facing "where are my characters?"
     * lookup (09 §4).
     *
     * @param accountDid the account to resolve
     * @return the account's recognized home bindings, ordered by slot, bounded by the resolve cap
     */
    public List<CharacterHomeEntry> resolveHomes(String accountDid) {
        Objects.requireNonNull(accountDid, "accountDid");
        return repository.findByAccount(accountDid, properties.maxHomesPerResolve());
    }

    /**
     * How many characters the directory recognizes for an account — the federation-wide number the soft
     * slot cap is checked against (09 §2). Backs {@link DirectoryRecognizedCharacterCount}.
     *
     * @param accountDid the account
     * @return the count of recognized characters
     */
    public long recognizedCharacterCount(String accountDid) {
        Objects.requireNonNull(accountDid, "accountDid");
        return repository.countByAccount(accountDid);
    }

    /**
     * The result of {@link #ingest}: the verification verdict, plus what convergence did if it was
     * accepted.
     *
     * @param verification the verification verdict; {@link CharacterHomeVerification#isAccepted()} says
     *     whether the record was valid
     * @param outcome what {@link #accept} did, or {@code null} if the record was rejected
     */
    public record IngestResult(CharacterHomeVerification verification, HomeAcceptOutcome outcome) {

        /** @return whether the record verified and was converged into the directory */
        public boolean stored() {
            return outcome == HomeAcceptOutcome.ACCEPTED_NEW || outcome == HomeAcceptOutcome.ACCEPTED_UPDATED;
        }
    }
}

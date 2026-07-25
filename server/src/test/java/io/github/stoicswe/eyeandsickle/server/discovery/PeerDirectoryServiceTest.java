package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.crypto.X25519KeyExchange;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link PeerDirectoryService} — the one place self-asserted directory data converges.
 *
 * <p>The convergence rule is the load-bearing claim of the whole discovery doc ({@code
 * docs/architecture/08-discovery-and-sync.md} §3): last-writer-wins is safe here <em>only</em> because
 * it is decided on the signed monotonic sequence, never on a clock. These tests drive an in-memory
 * repository so they need no database, and they prove the four outcomes (new, updated, duplicate, stale)
 * plus the two refusals a hostile federation forces — capacity flooding and a rolled-back sequence — and
 * that a descriptor claiming a future time cannot win.
 */
class PeerDirectoryServiceTest {

    private static final byte[] KEY = X25519KeyExchange.encodePublicKey(
            X25519KeyExchange.generateKeyPair().getPublic());
    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final String DID_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DID_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";

    private final FakeFederationPeerRepository repository = new FakeFederationPeerRepository();

    private static DiscoveryProperties properties() {
        return new DiscoveryProperties(null, null, null, null, null, null, null, null, null, null);
    }

    private static DiscoveryProperties properties(int maxDirectorySize) {
        return new DiscoveryProperties(null, maxDirectorySize, null, null, null, null, null, null, null, null);
    }

    private PeerDirectoryService service(DiscoveryProperties properties) {
        return new PeerDirectoryService(repository, new ServerDescriptorVerifier(properties, kid -> null), properties);
    }

    private static ServerDescriptor descriptor(String did, long sequence) {
        return new ServerDescriptor(
                did,
                "https://" + did.replace(':', '-') + ".example.test",
                KEY,
                null,
                null,
                null,
                sequence,
                List.of(ServerDescriptor.CAPABILITY_FEDERATION),
                null,
                "{\"descriptor\":{}}");
    }

    // ==================================================================== accept — the four outcomes

    @Nested
    @DisplayName("accept — convergence on the signed sequence")
    class Accept {

        @Test
        @DisplayName("an unknown peer under the cap is added")
        void unknownIsAdded() {
            AcceptOutcome outcome = service(properties()).accept(descriptor(DID_A, 5), NOW);

            assertThat(outcome).isEqualTo(AcceptOutcome.ACCEPTED_NEW);
            assertThat(repository.currentSequence(DID_A)).contains(5L);
        }

        @Test
        @DisplayName("a strictly-higher sequence supersedes the stored descriptor")
        void higherSequenceUpdates() {
            PeerDirectoryService service = service(properties());
            service.accept(descriptor(DID_A, 5), NOW);

            AcceptOutcome outcome = service.accept(descriptor(DID_A, 6), NOW);

            assertThat(outcome).isEqualTo(AcceptOutcome.ACCEPTED_UPDATED);
            assertThat(repository.currentSequence(DID_A)).contains(6L);
        }

        @Test
        @DisplayName("an equal sequence is a harmless refresh: last-seen is touched, the descriptor is not changed")
        void equalSequenceIsDuplicate() {
            PeerDirectoryService service = service(properties());
            service.accept(descriptor(DID_A, 5), NOW);
            Instant later = NOW.plusSeconds(600);

            AcceptOutcome outcome = service.accept(descriptor(DID_A, 5), later);

            assertThat(outcome).isEqualTo(AcceptOutcome.IGNORED_DUPLICATE);
            assertThat(repository.currentSequence(DID_A)).contains(5L);
            assertThat(repository.findByDid(DID_A).orElseThrow().lastSeenAt())
                    .as("a same-sequence re-announcement still refreshes liveness")
                    .isEqualTo(later);
        }

        @Test
        @DisplayName("a lower sequence is refused as stale — the stored, newer descriptor stands")
        void lowerSequenceIsStale() {
            PeerDirectoryService service = service(properties());
            service.accept(descriptor(DID_A, 6), NOW);

            // A captured old descriptor replayed as if new: at worst a downgrade to a retired transport key.
            AcceptOutcome outcome = service.accept(descriptor(DID_A, 5), NOW);

            assertThat(outcome).isEqualTo(AcceptOutcome.IGNORED_STALE);
            assertThat(repository.currentSequence(DID_A)).contains(6L);
        }
    }

    // ==================================================================== the anti-clock property

    @Nested
    @DisplayName("the ordering key is the signed sequence, never a wall clock")
    class NoClockAuthority {

        @Test
        @DisplayName("a descriptor claiming a future time but a LOWER sequence still loses")
        void futureTimeDoesNotWin() {
            DescriptorFixture fixture = new DescriptorFixture();
            DiscoveryProperties properties = properties();
            PeerDirectoryService service = new PeerDirectoryService(
                    repository, new ServerDescriptorVerifier(properties, fixture.resolver()), properties);

            Instant farFuture = Instant.parse("3000-01-01T00:00:00Z");
            Instant past = Instant.parse("2020-01-01T00:00:00Z");
            String lowSeqFutureTime = fixture.signed(5, List.of(), farFuture);
            String highSeqPastTime = fixture.signed(6, List.of(), past);

            assertThat(service.ingest(lowSeqFutureTime, NOW).outcome()).isEqualTo(AcceptOutcome.ACCEPTED_NEW);
            assertThat(service.ingest(highSeqPastTime, NOW).outcome()).isEqualTo(AcceptOutcome.ACCEPTED_UPDATED);

            // Re-announcing the future-timestamped, lower-sequence descriptor must NOT roll the peer back.
            // If convergence keyed on issuedAt, the year-3000 descriptor would win here — it must not.
            assertThat(service.ingest(lowSeqFutureTime, NOW).outcome()).isEqualTo(AcceptOutcome.IGNORED_STALE);
            assertThat(repository.currentSequence(DescriptorFixture.PEER_DID)).contains(6L);
        }
    }

    // ==================================================================== capacity — anti-flood

    @Nested
    @DisplayName("directory capacity is bounded against gossip flooding")
    class Capacity {

        @Test
        @DisplayName("a NEW peer beyond the directory cap is refused")
        void newPeerAtCapacityRefused() {
            PeerDirectoryService service = service(properties(1));
            assertThat(service.accept(descriptor(DID_A, 1), NOW)).isEqualTo(AcceptOutcome.ACCEPTED_NEW);

            AcceptOutcome outcome = service.accept(descriptor(DID_B, 1), NOW);

            assertThat(outcome).isEqualTo(AcceptOutcome.IGNORED_AT_CAPACITY);
            assertThat(repository.findByDid(DID_B)).isEmpty();
        }

        @Test
        @DisplayName("an EXISTING peer still updates at capacity — the cap only blocks growth")
        void existingPeerUpdatesAtCapacity() {
            PeerDirectoryService service = service(properties(1));
            service.accept(descriptor(DID_A, 1), NOW);

            AcceptOutcome outcome = service.accept(descriptor(DID_A, 2), NOW);

            assertThat(outcome).isEqualTo(AcceptOutcome.ACCEPTED_UPDATED);
            assertThat(repository.currentSequence(DID_A)).contains(2L);
        }
    }

    // ==================================================================== insert-race fall-throughs

    @Nested
    @DisplayName("a lost insert race is resolved by the sequence rule, not by re-throwing")
    class InsertRace {

        @Test
        @DisplayName("if a concurrent writer inserted a higher sequence first, our lower one is stale")
        void concurrentInsertWithHigherSequenceLosesToIt() {
            FakeFederationPeerRepository.Stored competitor = new FakeFederationPeerRepository.Stored();
            competitor.descriptor = descriptor(DID_A, 20);
            competitor.sequence = 20;
            competitor.firstSeen = NOW;
            competitor.lastSeen = NOW;
            // The competing ingest lands between our currentSequence() read and our insert.
            repository.onInsertAttempt = () -> repository.byDid.put(DID_A, competitor);

            AcceptOutcome outcome = service(properties()).accept(descriptor(DID_A, 5), NOW);

            assertThat(outcome).isEqualTo(AcceptOutcome.IGNORED_STALE);
            assertThat(repository.currentSequence(DID_A)).contains(20L);
        }

        @Test
        @DisplayName("the vanishing-row edge (inserted then deleted) converges to stale, not an exception")
        void insertedThenDeletedIsStale() {
            // insertNew reports 0 (someone else won), yet the row is already gone again by the re-read.
            FederationPeerRepository repo = new FakeFederationPeerRepository() {
                @Override
                public Optional<Long> currentSequence(String peerDid) {
                    return Optional.empty();
                }

                @Override
                public long count() {
                    return 0;
                }

                @Override
                public int insertNew(ServerDescriptor descriptor, Instant now) {
                    return 0;
                }
            };
            DiscoveryProperties properties = properties();
            PeerDirectoryService service =
                    new PeerDirectoryService(repo, new ServerDescriptorVerifier(properties, kid -> null), properties);

            assertThat(service.accept(descriptor(DID_A, 5), NOW)).isEqualTo(AcceptOutcome.IGNORED_STALE);
        }
    }

    // ==================================================================== ingest = verify then converge

    @Nested
    @DisplayName("ingest verifies before it stores")
    class Ingest {

        @Test
        @DisplayName("a descriptor that fails verification is never stored")
        void rejectedDescriptorNotStored() {
            PeerDirectoryService.IngestResult result = service(properties()).ingest("this is not json", NOW);

            assertThat(result.verification().isAccepted()).isFalse();
            assertThat(result.verification().fault()).isEqualTo(DescriptorFault.MALFORMED_JSON);
            assertThat(result.outcome()).isNull();
            assertThat(result.stored()).isFalse();
            assertThat(repository.count()).isZero();
        }

        @Test
        @DisplayName("a valid descriptor is verified and converged in one call")
        void validDescriptorStored() {
            DescriptorFixture fixture = new DescriptorFixture();
            DiscoveryProperties properties = properties();
            PeerDirectoryService service = new PeerDirectoryService(
                    repository, new ServerDescriptorVerifier(properties, fixture.resolver()), properties);

            PeerDirectoryService.IngestResult result = service.ingest(fixture.signed(3), NOW);

            assertThat(result.verification().isAccepted()).isTrue();
            assertThat(result.outcome()).isEqualTo(AcceptOutcome.ACCEPTED_NEW);
            assertThat(result.stored()).isTrue();
            assertThat(repository.currentSequence(DescriptorFixture.PEER_DID)).contains(3L);
        }
    }

    // ==================================================================== reads pass configured bounds through

    @Nested
    @DisplayName("reads are bounded by configuration (untrusted-input caps)")
    class BoundedReads {

        private final DiscoveryProperties properties =
                new DiscoveryProperties(null, 10, 7, 3, null, null, null, null, null, null);

        @Test
        @DisplayName("gossipTargets pulls at most gossip-fanout peers")
        void gossipTargetsUsesFanout() {
            service(properties).gossipTargets();
            assertThat(repository.lastLivePeersLimit).isEqualTo(3);
        }

        @Test
        @DisplayName("probeCandidates and the exchange sample use max-peers-per-exchange")
        void probeAndExchangeUsePerExchangeCap() {
            PeerDirectoryService service = service(properties);
            service.probeCandidates();
            service.descriptorsForExchange();
            assertThat(repository.lastProbeLimit).isEqualTo(7);
            assertThat(repository.lastExchangeLimit).isEqualTo(7);
        }

        @Test
        @DisplayName("operatorView never returns more than the directory cap, even if asked for more")
        void operatorViewIsCappedToDirectorySize() {
            PeerDirectoryService service = service(properties);
            service.operatorView(1000);
            assertThat(repository.lastOperatorLimit)
                    .as("a caller asking for 1000 must not be able to scan past the configured cap")
                    .isEqualTo(10);

            service.operatorView(3);
            assertThat(repository.lastOperatorLimit).isEqualTo(3);
        }
    }

    // ==================================================================== contact recording delegates

    @Nested
    @DisplayName("contact recording feeds the liveness counters")
    class ContactRecording {

        @Test
        @DisplayName("a success clears the failure streak and marks successful contact")
        void successRecorded() {
            repository.seed(DID_A, 0, 0, 3, null);
            UUID peerId = repository.findByDid(DID_A).orElseThrow().peerId();

            service(properties()).recordContactSuccess(peerId, NOW);

            PeerRecord record = repository.findByDid(DID_A).orElseThrow();
            assertThat(record.contactSuccesses()).isEqualTo(1);
            assertThat(record.consecutiveFailures()).isZero();
            assertThat(record.lastSuccessfulContactAt()).isEqualTo(NOW);
        }

        @Test
        @DisplayName("a failure advances the streak but never marks successful contact")
        void failureRecorded() {
            repository.seed(DID_A, 0, 0, 0, null);
            UUID peerId = repository.findByDid(DID_A).orElseThrow().peerId();

            service(properties()).recordContactFailure(peerId, NOW);

            PeerRecord record = repository.findByDid(DID_A).orElseThrow();
            assertThat(record.contactFailures()).isEqualTo(1);
            assertThat(record.consecutiveFailures()).isEqualTo(1);
            assertThat(record.lastSuccessfulContactAt())
                    .as("an unreachable peer must never look reached")
                    .isNull();
        }

        @Test
        @DisplayName("findByDid delegates to the repository")
        void findByDidDelegates() {
            repository.seed(DID_A, 2, 1, 0, NOW);
            assertThat(service(properties()).findByDid(DID_A)).isPresent();
            assertThat(service(properties()).findByDid(DID_B)).isEmpty();
        }
    }
}

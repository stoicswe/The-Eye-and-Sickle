package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * A hand-written, in-memory {@link CharacterDirectoryRepository} for the Docker-free unit tests.
 *
 * <p>It models the sequence-convergence rule faithfully — {@link #insertNew} refuses a duplicate {@code
 * (account, slot)}, {@link #updateIfNewer} advances only on a strictly-higher sequence — so a service
 * test exercises the same decision the real SQL makes, without a container. The {@code lastXxxLimit}
 * fields capture the bound each read was called with, so a test can prove the service passes its
 * configured caps through.
 *
 * <p>{@code super(null)} is deliberate: the base constructor merely stores the {@code JdbcClient}, and
 * every method that would touch it is overridden here, so no query is ever issued.
 */
class FakeCharacterDirectoryRepository extends CharacterDirectoryRepository {

    /** Mutable in-memory binding state. */
    static final class Stored {
        final UUID entryId = UUID.randomUUID();
        CharacterHomeRecord record;
        Instant firstSeen;
        Instant lastSeen;
        long rowVersion;
    }

    final Map<String, Stored> byKey = new LinkedHashMap<>();

    Integer lastFindByAccountLimit;

    /** Runs at the top of {@link #insertNew}, letting a test simulate a concurrent inserter winning the race. */
    Runnable onInsertAttempt = () -> {};

    FakeCharacterDirectoryRepository() {
        super(null);
    }

    private static String key(String accountDid, int slot) {
        return accountDid + "#" + slot;
    }

    @Override
    public Optional<CharacterHomeEntry> findByAccountAndSlot(String accountDid, int slot) {
        Stored stored = byKey.get(key(accountDid, slot));
        return stored == null ? Optional.empty() : Optional.of(toEntry(stored));
    }

    @Override
    public int insertNew(CharacterHomeRecord record, Instant now) {
        onInsertAttempt.run();
        String key = key(record.accountDid(), record.slot());
        if (byKey.containsKey(key)) {
            return 0;
        }
        Stored stored = new Stored();
        stored.record = record;
        stored.firstSeen = now;
        stored.lastSeen = now;
        byKey.put(key, stored);
        return 1;
    }

    @Override
    public int updateIfNewer(CharacterHomeRecord record, Instant now) {
        Stored stored = byKey.get(key(record.accountDid(), record.slot()));
        if (stored == null || stored.record.sequenceNumber() >= record.sequenceNumber()) {
            return 0;
        }
        stored.record = record;
        stored.lastSeen = now;
        stored.rowVersion++;
        return 1;
    }

    @Override
    public int touchLastSeen(String accountDid, int slot, Instant now) {
        Stored stored = byKey.get(key(accountDid, slot));
        if (stored == null) {
            return 0;
        }
        stored.lastSeen = now;
        return 1;
    }

    @Override
    public long count() {
        return byKey.size();
    }

    @Override
    public long countByAccount(String accountDid) {
        return byKey.values().stream()
                .filter(s -> s.record.accountDid().equals(accountDid))
                .count();
    }

    @Override
    public List<CharacterHomeEntry> findByAccount(String accountDid, int limit) {
        lastFindByAccountLimit = limit;
        List<CharacterHomeEntry> out = new ArrayList<>();
        byKey.values().stream()
                .filter(s -> s.record.accountDid().equals(accountDid))
                .map(FakeCharacterDirectoryRepository::toEntry)
                .sorted(Comparator.comparingInt(CharacterHomeEntry::slot))
                .forEach(out::add);
        return out.size() > limit ? out.subList(0, limit) : out;
    }

    private static CharacterHomeEntry toEntry(Stored stored) {
        CharacterHomeRecord record = stored.record;
        return new CharacterHomeEntry(
                stored.entryId,
                record.accountDid(),
                record.characterId(),
                record.slot(),
                record.homeServerDid(),
                record.homeEndpoint(),
                record.homeTransportPublicKey(),
                record.signingKeyId(),
                record.sequenceNumber(),
                record.signature(),
                stored.firstSeen,
                stored.lastSeen,
                stored.rowVersion);
    }
}

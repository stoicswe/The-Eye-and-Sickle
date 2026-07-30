package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterSlotExceededException;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterStatus;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.Heat;
import io.github.stoicswe.eyeandsickle.server.identity.Player;
import io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An in-memory {@link MigrationCharacters} for the migration unit tests: the identity core's behaviour a
 * migration relies on, with no database.
 *
 * <p>It reproduces the two rules migration leans on — a fresh character is created at <em>base</em> economy
 * (zero balance, zero heat, no faction: the reset, §6), and {@code markMigrated} is one-way ({@code
 * active -> migrated}, refusing a second attempt: no double-play, §6.1). The slot counter and the optional
 * cap let tests drive the "would exceed the account's cap" refusal without a real directory.
 */
final class FakeMigrationCharacters implements MigrationCharacters {

    private static final Instant NOW = Instant.parse("2026-07-25T00:00:00Z");

    private final Map<UUID, Player> byId = new LinkedHashMap<>();
    private int nextSlot = 1;
    private int cap = Integer.MAX_VALUE;

    /** Registers a pre-existing DID-bound character, so export/commit tests have a source to act on. */
    Player seed(Did did, int slot, Faction faction, long balanceMinor, long heat) {
        Player player = new Player(
                UUID.randomUUID(),
                did,
                slot,
                "seeded",
                CharacterStatus.ACTIVE,
                faction,
                Heat.ZERO.plus(java.math.BigDecimal.valueOf(heat)),
                Ethecoin.ofWei(java.math.BigInteger.valueOf(balanceMinor)
                        .multiply(Ethecoin.WEI_PER_ETHECOIN).divide(java.math.BigInteger.valueOf(100))),
                NOW,
                NOW,
                0L);
        byId.put(player.playerId(), player);
        nextSlot = Math.max(nextSlot, slot + 1);
        return player;
    }

    /** Registers a local, DID-less character (exempt from federated migration, §1). */
    Player seedLocal() {
        Player player = new Player(
                UUID.randomUUID(),
                null,
                null,
                "local",
                CharacterStatus.ACTIVE,
                Faction.NONE,
                Heat.ZERO,
                Ethecoin.ZERO,
                NOW,
                NOW,
                0L);
        byId.put(player.playerId(), player);
        return player;
    }

    /** Caps how many characters {@link #createFreshCharacter} will mint before refusing, per account. */
    void capAt(int max) {
        this.cap = max;
    }

    Player current(UUID characterId) {
        return byId.get(characterId);
    }

    /** How many character rows exist in total — lets a test assert "nothing was created". */
    int total() {
        return byId.size();
    }

    /** How many {@code active} characters the account holds — the shape the cap counts. */
    long activeCount(Did did) {
        return byId.values().stream()
                .filter(p -> did.equals(p.did()) && p.status() == CharacterStatus.ACTIVE)
                .count();
    }

    @Override
    public Player requireCharacter(UUID characterId) {
        Player player = byId.get(characterId);
        if (player == null) {
            throw new PlayerNotFoundException(characterId);
        }
        return player;
    }

    @Override
    public Player createFreshCharacter(Did accountDid, String handle) {
        long active = byId.values().stream()
                .filter(p -> accountDid.equals(p.did()) && p.status() == CharacterStatus.ACTIVE)
                .count();
        if (active >= cap) {
            throw new CharacterSlotExceededException(accountDid, (int) active, cap);
        }
        Player fresh = new Player(
                UUID.randomUUID(),
                accountDid,
                nextSlot++,
                handle,
                CharacterStatus.ACTIVE,
                Faction.NONE, // reset
                Heat.ZERO, // reset
                Ethecoin.ZERO, // reset
                NOW,
                NOW,
                0L);
        byId.put(fresh.playerId(), fresh);
        return fresh;
    }

    @Override
    public void markMigrated(UUID characterId) {
        Player player = requireCharacter(characterId);
        if (player.status() != CharacterStatus.ACTIVE) {
            throw new CharacterNotActiveException(characterId, player.status());
        }
        byId.put(characterId, withStatusAndVersion(player, CharacterStatus.MIGRATED));
    }

    @Override
    public void restoreStanding(UUID characterId, Faction faction, Heat heat, long expectedRowVersion) {
        Player player = requireCharacter(characterId);
        if (player.rowVersion() != expectedRowVersion) {
            throw new org.springframework.dao.OptimisticLockingFailureException("stale version for " + characterId
                    + ": expected " + expectedRowVersion + ", had " + player.rowVersion());
        }
        Player restored = new Player(
                player.playerId(),
                player.did(),
                player.slot(),
                player.handle(),
                player.status(),
                faction,
                heat,
                player.ethecoinBalance(),
                player.createdAt(),
                player.lastSeenAt(),
                player.rowVersion() + 1);
        byId.put(characterId, restored);
    }

    private static Player withStatusAndVersion(Player player, CharacterStatus status) {
        return new Player(
                player.playerId(),
                player.did(),
                player.slot(),
                player.handle(),
                status,
                player.faction(),
                player.personalHeat(),
                player.ethecoinBalance(),
                player.createdAt(),
                player.lastSeenAt(),
                player.rowVersion() + 1);
    }
}

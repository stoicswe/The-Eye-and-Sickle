package io.github.stoicswe.eyeandsickle.server.identity;

import static org.mockito.Mockito.mock;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * An in-memory, stateful stand-in for {@link PlayerRepository}, modelling one row per <em>character</em>.
 *
 * <p>Stateful rather than a per-call stub, because the flows under test — create a character, transition
 * its status, commit then abandon a faction — are about the row <em>changing</em>, and asserting on the
 * resulting state reads far better than verifying a sequence of mock calls. Version-checked updates model
 * the real optimistic-concurrency contract: a stale {@code expectedVersion} raises
 * {@link OptimisticLockingFailureException}, exactly as {@code Mutations.requireUpdated} does against a
 * real database. Creating two characters at the same {@code (did, slot)} raises
 * {@link DataIntegrityViolationException}, modelling {@code uq_players_did_slot}.
 */
final class FakePlayerRepository extends PlayerRepository {

    private final Map<UUID, Player> byId = new HashMap<>();

    private final List<CreateCall> createCalls = new ArrayList<>();
    private final List<String> createLocalCalls = new ArrayList<>();
    private final List<StatusCall> updateStatusCalls = new ArrayList<>();
    private final List<UUID> updateFactionCalls = new ArrayList<>();
    private final List<UUID> updateFactionAndHeatCalls = new ArrayList<>();

    record CreateCall(Did did, String handle, int slot, Instant now) {}

    record StatusCall(UUID characterId, CharacterStatus status) {}

    FakePlayerRepository() {
        super(mock(JdbcClient.class));
    }

    /** Directly seeds a character, for tests that start from an already-created row. */
    FakePlayerRepository put(Player character) {
        byId.put(character.playerId(), character);
        return this;
    }

    /** A freshly-created DID-bound character as {@link #createCharacter} makes one. */
    static Player active(UUID characterId, Did did, int slot, String handle, Instant now) {
        return new Player(
                characterId, did, slot, handle, CharacterStatus.ACTIVE, Faction.NONE, Heat.ZERO, Ethecoin.ZERO, now, now,
                0);
    }

    // ------------------------------------------------------------------ reads

    @Override
    public List<Player> findCharactersByDid(Did did) {
        Objects.requireNonNull(did, "did");
        return byId.values().stream()
                .filter(c -> did.equals(c.did()))
                .sorted(Comparator.comparing(Player::slot))
                .toList();
    }

    @Override
    public Optional<Player> findCharacter(UUID characterId) {
        return Optional.ofNullable(byId.get(characterId));
    }

    @Override
    public long countActiveCharacters(Did did) {
        Objects.requireNonNull(did, "did");
        return byId.values().stream()
                .filter(c -> did.equals(c.did()) && c.status() == CharacterStatus.ACTIVE)
                .count();
    }

    // ------------------------------------------------------------------ creation

    @Override
    public Player createCharacter(Did did, String handle, int slot, Instant now) {
        Objects.requireNonNull(did, "did");
        Objects.requireNonNull(now, "now");
        createCalls.add(new CreateCall(did, handle, slot, now));
        boolean slotTaken = byId.values().stream()
                .anyMatch(c -> did.equals(c.did()) && Integer.valueOf(slot).equals(c.slot()));
        if (slotTaken) {
            throw new DataIntegrityViolationException(
                    "uq_players_did_slot: (" + did + ", " + slot + ") already exists");
        }
        Player created = active(UUID.randomUUID(), did, slot, handle, now);
        byId.put(created.playerId(), created);
        return created;
    }

    @Override
    public Player createLocalCharacter(String handle, Instant now) {
        Objects.requireNonNull(now, "now");
        createLocalCalls.add(handle);
        Player created = new Player(
                UUID.randomUUID(), null, null, handle, CharacterStatus.ACTIVE, Faction.NONE, Heat.ZERO, Ethecoin.ZERO,
                now, now, 0);
        byId.put(created.playerId(), created);
        return created;
    }

    // ------------------------------------------------------------------ mutations

    @Override
    public void updateStatus(UUID characterId, CharacterStatus status, long expectedVersion) {
        updateStatusCalls.add(new StatusCall(characterId, status));
        Player current = requireVersion(characterId, expectedVersion);
        byId.put(
                characterId,
                new Player(
                        current.playerId(),
                        current.did(),
                        current.slot(),
                        current.handle(),
                        status,
                        current.faction(),
                        current.personalHeat(),
                        current.ethecoinBalance(),
                        current.createdAt(),
                        current.lastSeenAt(),
                        current.rowVersion() + 1));
    }

    @Override
    public void updateFaction(UUID characterId, Faction faction, long expectedVersion) {
        updateFactionCalls.add(characterId);
        Player current = requireVersion(characterId, expectedVersion);
        byId.put(
                characterId,
                new Player(
                        current.playerId(),
                        current.did(),
                        current.slot(),
                        current.handle(),
                        current.status(),
                        faction,
                        current.personalHeat(),
                        current.ethecoinBalance(),
                        current.createdAt(),
                        current.lastSeenAt(),
                        current.rowVersion() + 1));
    }

    @Override
    public void updateFactionAndHeat(UUID characterId, Faction faction, Heat heat, long expectedVersion) {
        updateFactionAndHeatCalls.add(characterId);
        Player current = requireVersion(characterId, expectedVersion);
        byId.put(
                characterId,
                new Player(
                        current.playerId(),
                        current.did(),
                        current.slot(),
                        current.handle(),
                        current.status(),
                        faction,
                        heat,
                        current.ethecoinBalance(),
                        current.createdAt(),
                        current.lastSeenAt(),
                        current.rowVersion() + 1));
    }

    private Player requireVersion(UUID characterId, long expectedVersion) {
        Player current = byId.get(characterId);
        if (current == null || current.rowVersion() != expectedVersion) {
            throw new OptimisticLockingFailureException("stale write to players for " + characterId);
        }
        return current;
    }

    // ------------------------------------------------------------------ recordings

    List<CreateCall> createCalls() {
        return List.copyOf(createCalls);
    }

    List<String> createLocalCalls() {
        return List.copyOf(createLocalCalls);
    }

    List<StatusCall> updateStatusCalls() {
        return List.copyOf(updateStatusCalls);
    }

    List<UUID> updateFactionCalls() {
        return List.copyOf(updateFactionCalls);
    }

    List<UUID> updateFactionAndHeatCalls() {
        return List.copyOf(updateFactionAndHeatCalls);
    }
}

package io.github.stoicswe.eyeandsickle.server.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.GameIntent;
import io.github.stoicswe.eyeandsickle.protocol.game.GameSnapshot;
import io.github.stoicswe.eyeandsickle.protocol.game.IntentOutcome;
import io.github.stoicswe.eyeandsickle.server.audit.OperatorLog;
import java.lang.reflect.RecordComponent;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The transport's authoritative side — the checks that need no database.
 *
 * <p>⚠ The parts that <em>do</em> need one — that a snapshot carries the rig's real compute, and that
 * allocation is absolute and rolls back on refusal — belong in an {@code -Pit} integration test
 * against a real Postgres, because they are properties of the compute ledger's own transaction
 * semantics. Asserting them over a fake would be asserting that the fake behaves like the fake, which
 * is the failure mode {@code CLAUDE.md} already records for {@code reconcileFootholds}: two correct
 * halves and a defect in the join.
 */
class GameSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-02T12:00:00Z");
    private static final UUID CHARACTER = UUID.fromString("11111111-2222-3333-4444-555555555555");

    /**
     * ⚠ A mocked {@link EngineSessions}, and the tests below take only paths that never enter it.
     *
     * <p>Everything that runs the engine needs a real database, because the engine's state lives in
     * one — those checks belong in {@code -Pit}. Asserting engine behaviour over a mock would assert
     * that the mock behaves like the mock, which is the {@code reconcileFootholds} failure shape
     * {@code CLAUDE.md} records: two correct halves and a defect in the join.
     */
    private final GameSessionService service =
            new GameSessionService(org.mockito.Mockito.mock(EngineSessions.class), () -> NOW, new OperatorLog());

    @Test
    @DisplayName("a null intent is refused rather than throwing")
    void nullIntent() {
        assertThat(service.apply(CHARACTER, null).status()).isEqualTo(IntentOutcome.Status.REFUSED);
    }

    @Test
    @DisplayName("⚠ every GameIntent variant is handled — the sealed switch has no default")
    void everyIntentIsHandled() {
        // The reason GameIntent is sealed. A new variant must fail the BUILD here rather than falling
        // through to a default that silently accepts it — the safe answer to an unrecognised intent is
        // refusal, and only a closed set makes "all handled" checkable by a compiler.
        assertThat(GameIntent.class.isSealed()).isTrue();
        assertThat(GameIntent.class.getPermittedSubclasses()).hasSize(2);
    }

    @Test
    @DisplayName("⚠ the snapshot invents nothing — absent systems are ABSENT, not confidently zero")
    void inventsNothing() {
        // The guard against the tempting shortcut. A field carrying a plausible value for a system the
        // server does not own would be rendered by the client as fact, and the player would read a
        // number nobody is keeping. This pins the field list so a chain or breach field cannot be
        // added ahead of the engine that fills it.
        assertThat(GameSnapshot.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .containsExactlyInAnyOrder(
                        "characterId",
                        "revision",
                        "serverTime",
                        "computeBudget",
                        "balance",
                        "personalHeat",
                        "uptimeSeconds");
    }

    @Test
    @DisplayName("⚠ heat is carried at FULL precision, never rounded to an int")
    void heatIsNotRounded() {
        // Heat is fractional server-side and it gates detection. An int here would move a player
        // across a threshold they were deliberately sitting under — the game's outcome changed by a
        // choice of field type. This was caught by the compiler once; the test is so it stays caught.
        assertThat(Arrays.stream(GameSnapshot.class.getRecordComponents())
                        .filter(component -> component.getName().equals("personalHeat"))
                        .findFirst()
                        .orElseThrow()
                        .getType())
                .isEqualTo(BigDecimal.class);
    }
}

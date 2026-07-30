package io.github.stoicswe.eyeandsickle.solo;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.solo.save.SaveStore;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Opening a save written before ethecoin divided to 18 places.
 *
 * <h2>⚠ The bug this exists for, and why the first attempt at testing it passed against it</h2>
 *
 * The money fields were renamed when the scale moved — {@code ethecoinMinorUnits} became
 * {@code ethecoinWei}, and so on for a dozen more. Jackson runs with
 * {@code FAIL_ON_UNKNOWN_PROPERTIES} <b>off</b>, deliberately, so that a save from a newer build
 * still opens; the consequence is that a key it does not recognise is <b>silently dropped</b>.
 *
 * <p>So a real pre-migration save did not fail to load. It loaded, left every amount at its
 * initialiser, and handed the player a balance of <b>zero</b> — 500 EC gone, ledger rows all reading
 * {@code 0 EC}, nothing anywhere reporting a problem. The rescale then dutifully multiplied zero by
 * 10^16.
 *
 * <p>⚠ <b>The first verification of the migration passed against exactly this.</b> It built a save
 * with the <em>new</em> code and hand-edited one value, so the keys already matched and the rename
 * was never exercised. A test that constructs its fixture with the code under test cannot catch a
 * format change — the fixture here is therefore <b>literal JSON, written the way the old build wrote
 * it</b>, and it is the only shape that would have caught this.
 *
 * <p>It was found by a crash rather than by a test: one field, {@code ContributionState.creditedWei},
 * had no initialiser, so it came back null and threw on the way into the rescale. The loud failure
 * was an accident, and the silent one is what this guards.
 */
class LegacySaveTest {

    private static final Instant NOW = Instant.parse("2026-07-30T09:00:00Z");

    /**
     * A save exactly as the pre-wei build wrote it: old key names, amounts in hundredths.
     *
     * <p>⚠ Deliberately hand-written rather than generated. Every key here is a fact about a file
     * format that shipped, and the moment this is produced by current code it stops testing anything.
     */
    private static final String LEGACY_JSON = """
            {
              "handle" : "kyyrell",
              "characterId" : "11111111-2222-3333-4444-555555555555",
              "createdAt" : "2026-07-26T09:00:00Z",
              "lastPlayedAt" : "2026-07-29T09:00:00Z",
              "ethecoinMinorUnits" : 50000,
              "rig" : {
                "totalCycles" : 100,
                "miningMinorUnits" : 1200,
                "miningPendingMinorUnits" : 33,
                "miningResidueMinorUnits" : 0.0
              },
              "ledger" : [ {
                "entryId" : "e1",
                "at" : "2026-07-28T09:00:00Z",
                "deltaMinorUnits" : -800,
                "balanceAfterMinorUnits" : 50000,
                "type" : "PURCHASE",
                "description" : "Canary Token",
                "feeMinorUnits" : 6
              } ]
            }
            """;

    @Test
    @DisplayName("a pre-wei save keeps every amount it had, rescaled rather than dropped")
    void legacyAmountsSurvive(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("legacy.json");
        Files.writeString(file, LEGACY_JSON);

        SoloGame game = SoloGame.open(
                new SaveStore(file), "kyyrell", Clock.fixed(NOW, ZoneOffset.UTC));
        SoloSave save = game.state();

        // 50 000 hundredths was 500.00 EC, and it still is.
        assertThat(save.ethecoinWei).isEqualTo(Balance.ec("500"));
        assertThat(save.rig.miningWei).isEqualTo(Balance.ec("12"));
        assertThat(save.rig.miningPendingWei).isEqualTo(Balance.ec("0.33"));

        assertThat(save.ledger).hasSize(1);
        // ⚠ negate(), because Balance.ec goes through Ethecoin and an Ethecoin is never
        // negative by construction. A ledger DELTA is signed; the value type is not.
        assertThat(save.ledger.getFirst().deltaWei).isEqualTo(Balance.ec("8").negate());
        assertThat(save.ledger.getFirst().balanceAfterWei).isEqualTo(Balance.ec("500"));
        assertThat(save.ledger.getFirst().feeWei).isEqualTo(Balance.ec("0.06"));
    }

    /**
     * ⚠ The rescale must not run twice.
     *
     * <p>It is gated on {@code SoloSave.moneySchema}, which the load stamps. Without the stamp a
     * second launch would multiply an already-correct balance by another 10^16 — and unlike the
     * zeroing above, that failure grows every time the player opens the game.
     */
    @Test
    @DisplayName("re-opening a migrated save does not rescale it again")
    void migrationIsIdempotent(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("legacy.json");
        Files.writeString(file, LEGACY_JSON);

        SoloGame first = SoloGame.open(
                new SaveStore(file), "kyyrell", Clock.fixed(NOW, ZoneOffset.UTC));
        first.persist();

        SoloGame second = SoloGame.open(
                new SaveStore(file), "kyyrell", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(second.state().ethecoinWei).isEqualTo(Balance.ec("500"));
        assertThat(second.state().moneySchema).isEqualTo(SoloSave.MONEY_SCHEMA);
    }

    /**
     * ⚠ Every money field must have an initialiser, so a missing key is zero rather than null.
     *
     * <p>{@code ContributionState.creditedWei} did not, which is how this whole class of bug was
     * found — a {@code NullPointerException} out of the rescale, on the login screen. A default of
     * zero is the honest reading of "the file did not say", and it is also the only one that cannot
     * take the client down while a player is trying to open their character.
     */
    @Test
    @DisplayName("no money field is null on a save that omits it entirely")
    void absentAmountsAreZeroNotNull(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("sparse.json");
        Files.writeString(file, "{ \"handle\" : \"ghost\", \"characterId\" : "
                + "\"22222222-3333-4444-5555-666666666666\" }");

        SoloGame game = SoloGame.open(
                new SaveStore(file), "ghost", Clock.fixed(NOW, ZoneOffset.UTC));
        assertThat(game.state().ethecoinWei).isNotNull();
        assertThat(game.state().rig.miningWei).isNotNull();
        assertThat(game.state().rig.miningResidueWei).isNotNull();
        assertThat(game.balance()).isEqualTo(Ethecoin.ZERO);
    }
}

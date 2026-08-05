package io.github.stoicswe.eyeandsickle.client.session;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.Balance;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.rules.Repac;
import io.github.stoicswe.eyeandsickle.engine.state.StoredFileState;
import io.github.stoicswe.eyeandsickle.protocol.game.StorageTier;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Buying a tool: pay, download, wait for the block, install.
 *
 * <h2>What changed on 2026-07-29</h2>
 *
 * A purchase used to hand over the item in the same call that took the money — a decision
 * {@code GameEngine.debit} defended in as many words as "the one place this simulation declines to be
 * faithful". It now goes over the same pipeline a stolen upgrade does: a real transfer bounded by the
 * vendor's uplink, a package in {@code ~/Downloads}, and an {@code install} step.
 *
 * <h2>⚠ The `.pkg` → `.upg` rename IS the lock, and there is no second mechanism</h2>
 *
 * {@code Repac} already draws the line between "a vendor's package" and "one this rig can install",
 * and a bought package simply does not cross it until the payment is mined. That means the lock is
 * visible in {@code ls}, in the file manager and in the shell without any of them being told about
 * confirmation — and it means there is no flag anywhere that can disagree with the chain.
 */
class PurchaseFlowTest {

    private static final Instant T0 = Instant.parse("2026-07-29T09:00:00Z");
    private static final String OFFERING = "canary-token";

    private static StoredFileState onlyFile(GameEngine game) {
        assertThat(game.state().files).hasSize(1);
        return game.state().files.getFirst();
    }

    @Test
    @DisplayName("the whole journey: paid, downloaded, held, confirmed, installed")
    void buyDownloadConfirmInstall(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        game.credit(Balance.ec("500"), "TEST", "seed");
        java.math.BigInteger before = session.balance().wei();

        // ── paid, and nothing delivered ───────────────────────────────────────────────────────
        var bought = session.purchase(OFFERING);
        assertThat(bought.succeeded()).isTrue();
        assertThat(session.balance().wei())
                .as("the money goes now — a real wallet deducts a send immediately")
                .isLessThan(before);
        assertThat(session.items(StorageTier.VAULT)).isEmpty();
        assertThat(session.transfers())
                .as("and a download starts, with a progress bar the file manager already draws")
                .hasSize(1);

        // ── downloaded, and still not installable ─────────────────────────────────────────────
        // ⚠ The chain is held OFF for the download window, deliberately.
        //
        // A transfer takes 2–17 seconds and a block lands every ~14 minutes, so the obvious fixture —
        // "advance two minutes and tick" — leaves a ~13% chance that the payment confirms during the
        // download and the package is already unlocked when the assertions run. That is a test which
        // passes most of the time and fails for a reason that has nothing to do with what it is
        // testing, which is worse than no test: it trains its reader to re-run.
        //
        // networkWorkTarget is the outstanding Exp(1) draw, so a large value is simply a block that
        // takes a very long time. Nothing here is mocked — the chain runs, it just has not found one.
        io.github.stoicswe.eyeandsickle.client.support.Chains.holdOff(game);
        clock.advance(Duration.ofMinutes(2));
        game.tick();
        assertThat(session.transfers()).isEmpty();

        StoredFileState pkg = onlyFile(game);
        assertThat(pkg.name)
                .as("a vendor package, and it stays one until the payment is mined")
                .endsWith(Repac.PAYLOAD_SUFFIX);
        assertThat(pkg.directory).endsWith("/Downloads");
        assertThat(Repac.locked(game.state(), pkg)).isTrue();

        var early = Repac.install(game.state(), pkg.path(), game.now());
        assertThat(early.ok()).isFalse();
        assertThat(early.refusal()).isEqualTo(Repac.Refusal.UNCONFIRMED);
        // ⚠ The refusal must name the BLOCK, not the file type. Falling through to the kind check
        // would say "not an installable upgrade", which is true, useless, and indistinguishable
        // from a corrupt download.
        assertThat(early.message()).contains("confirmed");
        assertThat(session.items(StorageTier.VAULT)).isEmpty();

        // Nor can it be resold — that hole would be shaped exactly like the secondary market.
        assertThat(Repac.sell(game.state(), pkg.path()).refusal()).isEqualTo(Repac.Refusal.UNCONFIRMED);

        // ── confirmed ─────────────────────────────────────────────────────────────────────────
        // ⚠ WAIT FOR THE PAYMENT TO BE MINED, not for a wall-clock span that usually contains it.
        // This read "release the chain, advance three hours, tick", and three hours is ~13 blocks —
        // but a block landing is not a transaction confirming. A STANDARD fee wins its slot against
        // the derived backlog only about 38% of blocks (MempoolRules.clearingFeeAt), so ~13 of them
        // miss roughly one run in five hundred, and the seed differs every run because a @TempDir
        // path gives a fresh character id. See support/Chains for the arithmetic.
        io.github.stoicswe.eyeandsickle.client.support.Chains.settlePayment(
                game, () -> clock.advance(Duration.ofHours(1)));

        StoredFileState upg = onlyFile(game);
        assertThat(upg.name).as("confirmation is what runs Repac").endsWith(Repac.PACKAGE_SUFFIX);
        assertThat(Repac.locked(game.state(), upg)).isFalse();

        // ── installed ─────────────────────────────────────────────────────────────────────────
        var installed = Repac.install(game.state(), upg.path(), game.now());
        assertThat(installed.ok()).isTrue();
        // ⚠ THE HIGH-RISK ZONE, not the vault (changed 2026-08-04). Bought goods arrive exposed and
        // the player files them somewhere safer themselves — the vault is meant to be a decision,
        // and a purchase that filed itself safely would make design/01 §6's tiers a setting nobody
        // ever touches. A STOLEN package still lands in the vault: that risk was already carried.
        assertThat(session.items(StorageTier.HIGH_HACKABLE_ZONE))
                .anyMatch(i -> i.displayName().equals("Canary Token"));
        assertThat(session.items(StorageTier.VAULT)).isEmpty();
        assertThat(game.state().files).isEmpty();
    }

    @Test
    @DisplayName("the ledger row for the purchase is what releases it")
    void theLedgerRowIsTheKey(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        game.credit(Balance.ec("500"), "TEST", "seed");
        session.purchase(OFFERING);
        io.github.stoicswe.eyeandsickle.client.support.Chains.holdOff(game);
        clock.advance(Duration.ofMinutes(2));
        game.tick();

        StoredFileState pkg = onlyFile(game);
        var held = Repac.heldBy(game.state(), pkg);
        assertThat(held).isPresent();
        assertThat(held.get().type).isEqualTo("MARKET");
        assertThat(held.get().blockNumber)
                .as("unconfirmed, which is exactly what the hold reads")
                .isNegative();
    }

    /**
     * ⚠ A package naming a ledger row that is not there is RELEASED, not held forever.
     *
     * <p>Only a hand-edited save or a bug reaches that state, and of the two possible errors —
     * releasing a package whose payment cannot be found, and holding one forever with no way for the
     * player to discover why — the second is unrecoverable and the first costs one item in a
     * single-player game.
     */
    @Test
    @DisplayName("a package whose ledger row has vanished fails open")
    void anOrphanedHoldFailsOpen(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        game.credit(Balance.ec("500"), "TEST", "seed");
        session.purchase(OFFERING);
        io.github.stoicswe.eyeandsickle.client.support.Chains.holdOff(game);
        clock.advance(Duration.ofMinutes(2));
        game.tick();

        StoredFileState pkg = onlyFile(game);
        assertThat(Repac.locked(game.state(), pkg)).isTrue();
        pkg.lockedByEntryId = "an-entry-that-is-not-in-the-ledger";
        assertThat(Repac.locked(game.state(), pkg)).isFalse();
    }

    @Test
    @DisplayName("buying the same thing twice is refused rather than charged twice")
    void noDoubleBuy(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        game.credit(Balance.ec("500"), "TEST", "seed");

        assertThat(session.purchase(OFFERING).succeeded()).isTrue();
        java.math.BigInteger after = session.balance().wei();

        // ⚠ A SECOND COPY IS ALLOWED (changed 2026-08-04) and this test used to assert the opposite.
        // Items stopped stacking: each has its own id, tier and build, so a second Canary Token is a
        // second thing and a shop that refused to sell one was answering a question about inventory
        // rather than about money.
        assertThat(session.purchase(OFFERING).succeeded()).isTrue();
        assertThat(session.balance().wei())
                .as("and it is charged again — two things cost twice")
                .isLessThan(after);
        assertThat(session.downloads()).as("both are owed").hasSize(2);
        assertThat(session.transfers())
                .as("but only one moves at a time — the queue is a queue")
                .hasSize(1);
    }

    @Test
    @DisplayName("⚠ two copies are two FILES, or the path stops being an identifier")
    void twoCopiesAreTwoFiles(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        game.credit(Balance.ec("500"), "TEST", "seed");

        session.purchase(OFFERING);
        session.purchase(OFFERING);
        for (int i = 0; i < 40 && !session.downloads().isEmpty(); i++) {
            clock.advance(Duration.ofSeconds(5));
            game.tick();
        }

        // Both landed, and they are distinguishable. `Repac.find` resolves a path to the FIRST
        // match, so two files called `canary-token.pkg` would make Get Info describe one of them,
        // `install` consume one of them and `rm` delete one of them, with nothing on screen saying
        // which — a filesystem where the path is not an identifier.
        assertThat(game.state().files).hasSize(2);
        assertThat(game.state().files.stream().map(f -> f.name).distinct())
                .as("distinct names")
                .hasSize(2);
        assertThat(game.state().files.stream().map(f -> f.fileId).distinct()).hasSize(2);
    }

    // ────────────────────────────────────────────────────────────── the installer's manifest

    private static io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest bought(
            Path dir, Winding clock, GameEngine game, LocalGameSession session) {
        game.credit(Balance.ec("500"), "TEST", "seed");
        session.purchase(OFFERING);
        io.github.stoicswe.eyeandsickle.client.support.Chains.holdOff(game);
        clock.advance(Duration.ofMinutes(2));
        game.tick();
        return session.packageAt(onlyFile(game).path()).orElseThrow();
    }

    @Test
    @DisplayName("a bought package's manifest reports its publisher, contents and hold")
    void manifestDescribesThepackage(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        var pkg = bought(dir, clock, game, session);

        assertThat(pkg.fromMarket()).isTrue();
        assertThat(pkg.publisher()).isNotBlank();
        assertThat(pkg.displayName()).isEqualTo("Canary Token");
        assertThat(pkg.summary()).isNotBlank();
        assertThat(pkg.sizeBytes()).isPositive();
        assertThat(pkg.locked()).isTrue();
        assertThat(pkg.pendingNote()).isNotBlank();
        assertThat(pkg.owned()).isFalse();
        // ⚠ The panel's Install button is driven by this, and it must agree with the rule rather than
        // be a second opinion about it — a button enabled where install() would refuse is the client
        // claiming authority it does not have (C4).
        assertThat(pkg.installable()).isFalse();
        assertThat(Repac.install(game.state(), pkg.path(), game.now()).ok()).isFalse();
    }

    /**
     * ⚠ Market packages ALWAYS verify, and the mismatch path is built anyway.
     *
     * <p>In single player there is exactly one party and nothing can tamper. The mismatch state
     * exists, renders and is tested because the player-to-player market in online play is where a
     * payload can stop agreeing with its manifest — and a verification step introduced at the same
     * moment as the threat would be a new mechanic arriving with nobody in the habit of reading it.
     */
    @Test
    @DisplayName("digests match on a market package, and diverge when the payload is substituted")
    void integrityIsCheckable(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        var pkg = bought(dir, clock, game, session);

        assertThat(pkg.shaMatches()).isTrue();
        assertThat(pkg.expectedSha()).startsWith("sha256:").isEqualTo(pkg.actualSha());
        // Two different upgrades cannot share a digest, or the comparison proves nothing.
        assertThat(Repac.expectedSha("canary-token")).isNotEqualTo(Repac.expectedSha("noise-damper"));

        // The seam: a substituted payload. Nothing in single player sets this.
        onlyFile(game).payloadSalt = "malicious";
        var tampered = session.packageAt(onlyFile(game).path()).orElseThrow();
        assertThat(tampered.shaMatches()).isFalse();
        assertThat(tampered.actualSha()).isNotEqualTo(tampered.expectedSha());
        // ⚠ The DECLARED digest is unchanged — that is what makes the mismatch legible. A tamper that
        // rewrote both would verify, which is why a manifest is only worth anything when it is signed
        // by somebody other than whoever handed you the bytes.
        assertThat(tampered.expectedSha()).isEqualTo(pkg.expectedSha());
    }

    @Test
    @DisplayName("a path that is not a package this rig holds has no manifest")
    void noManifestForAnythingElse(@TempDir Path dir) {
        Winding clock = new Winding(T0);
        GameEngine game = GameEngine.open(
                io.github.stoicswe.eyeandsickle.engine.save.TestSaves.at(dir.resolve("save.json")), "operator", clock);
        LocalGameSession session = new LocalGameSession(game);
        // The panel falls back to the rules' own refusal for these rather than rendering blank.
        assertThat(session.packageAt("/Users/operator/Downloads/not-here.upg")).isEmpty();
        assertThat(session.packageAt("/System/bin/ls")).isEmpty();
    }

    /** A hand-wound clock. {@code solo.TestClock} is package-private and in another module. */
    private static final class Winding extends Clock {

        private Instant instant;

        Winding(Instant start) {
            this.instant = start;
        }

        void advance(Duration by) {
            instant = instant.plus(by);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

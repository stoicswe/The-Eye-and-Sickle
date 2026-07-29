package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.protocol.game.PackageManifest;
import java.util.Locale;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * The package installer — what is in this thing, who made it, and is it what it says it is.
 *
 * <h2>Why installing gets a panel instead of a menu item that just does it</h2>
 *
 * Installing consumes the package, and a {@code .upg} is an <b>asset</b>: it can be sold instead, and
 * for an upgrade already owned it is worth strictly more sold than installed. That is a decision, and
 * a decision made from a right-click menu is a decision made without the facts. The panel puts the
 * facts where the action is — what it installs, what it will reserve when equipped, what gate it sits
 * behind, and whether anything is currently holding it.
 *
 * <h2>⚠ The two digests are shown, not reduced to a tick</h2>
 *
 * {@link PackageManifest#expectedSha} is what the manifest declares and {@link
 * PackageManifest#actualSha} is what the payload hashes to. <b>Both</b> are printed, in full-width
 * monospace, above a verdict — because the point of a checksum is that a person compares two figures,
 * and a panel that only ever showed "verified ✓" would teach a player to trust a tick mark rather
 * than to read a digest. They always agree today; the mismatch state is built, styled and tested
 * because the player-to-player market in online play is where a payload can stop matching its
 * manifest, and a verification step that arrived at the same moment as the threat would be a new
 * mechanic nobody had a habit for.
 *
 * <h2>Two modes, one panel</h2>
 *
 * {@link Mode#INSTALL} carries the action. {@link Mode#INSPECT} is the same facts with no action at
 * all — the safe way to look at something you have not decided about. Sharing the panel is deliberate:
 * a separate read-only viewer would be a second place for the same six fields to be formatted, and
 * the day they disagreed the one showing the digest would be the one nobody trusted.
 */
public final class PackageView {

    private PackageView() {}

    /** Whether this panel can act, or only report. */
    public enum Mode {
        /** Carries Install and Sell. */
        INSTALL,

        /** Read-only. Nothing here changes anything. */
        INSPECT
    }

    /**
     * Builds the panel.
     *
     * @param onAction run after an install or a sale, so the caller can refresh and dismiss
     * @param report where a refusal is written — the rules' own words, never this panel's guess
     */
    public static Region create(
            GameSession session,
            PackageManifest pkg,
            Mode mode,
            Runnable onAction,
            java.util.function.Consumer<String> report) {
        VBox root = new VBox(UiTokens.SPACE_3);
        root.getStyleClass().addAll("es-package", "es-body-pad");
        root.setMinWidth(640);

        Label title = new Label(mode == Mode.INSPECT ? "INSPECT PACKAGE" : "INSTALL PACKAGE");
        title.getStyleClass().add("es-panel-title");

        Label file = new Label(pkg.name());
        file.getStyleClass().addAll("es-package-name", "es-mono");

        VBox facts = new VBox(UiTokens.SPACE_1);
        facts.getChildren().addAll(
                field("installs", pkg.displayName()),
                field("publisher", pkg.publisher()),
                field("origin", pkg.fromMarket() ? "vendor market" : pkg.origin()),
                field("gate", pkg.gate().name().toLowerCase(Locale.ROOT).replace('_', '-')),
                field("size", String.format(Locale.ROOT, "%.1f MB", pkg.sizeBytes() / 1_000_000.0d)),
                field("reserves", pkg.equippedCycles() == 0
                        ? "nothing while equipped"
                        : pkg.equippedCycles() + (pkg.equippedCycles() == 1 ? " cycle" : " cycles")
                                + " while equipped"));

        Label contents = new Label(pkg.summary());
        contents.setWrapText(true);
        contents.getStyleClass().add("es-package-summary");

        root.getChildren().addAll(title, file, facts, heading("CONTENTS"), contents,
                heading("INTEGRITY"), integrity(pkg), heading("STATUS"), status(pkg));

        if (mode == Mode.INSTALL) {
            root.getChildren().add(actions(session, pkg, onAction, report));
        } else {
            // Said out loud, because a panel with facts and no buttons otherwise reads as one whose
            // buttons failed to appear.
            Label note = new Label("Read-only. Nothing on this panel installs, sells or changes "
                    + "anything — close it and choose Install when you have decided.");
            note.setWrapText(true);
            note.getStyleClass().add("es-package-note");
            root.getChildren().add(note);
        }
        return root;
    }

    /**
     * The two digests, then the verdict.
     *
     * <p>⚠ Printed in full and on their own lines. Shortening them to eight characters would make the
     * comparison the panel is asking a player to make impossible to actually make, which is the exact
     * failure of every interface that renders a hash as "9f3c…4a2b ✓".
     */
    private static Region integrity(PackageManifest pkg) {
        VBox box = new VBox(UiTokens.SPACE_1);
        box.getChildren().addAll(
                digest("declared", pkg.expectedSha()),
                digest("payload", pkg.actualSha()));

        Label verdict = new Label(pkg.shaMatches()
                ? "MATCH — the payload is what the manifest says it is."
                : "MISMATCH — the payload is NOT what this manifest declares. Something replaced it "
                        + "after it was signed. Installing it would run whatever it actually is.");
        verdict.setWrapText(true);
        verdict.getStyleClass().addAll("es-mono",
                pkg.shaMatches() ? "es-package-match" : "es-package-mismatch");
        box.getChildren().add(verdict);

        if (pkg.fromMarket()) {
            Label note = new Label("Vendor packages are signed by the network and always match. A "
                    + "package from another player is not — check this panel before installing one.");
            note.setWrapText(true);
            note.getStyleClass().add("es-package-note");
            box.getChildren().add(note);
        }
        return box;
    }

    /** What is holding this package, if anything. */
    private static Region status(PackageManifest pkg) {
        VBox box = new VBox(UiTokens.SPACE_1);
        if (pkg.locked()) {
            Label held = new Label("LOCKED — " + pkg.pendingNote());
            held.setWrapText(true);
            held.getStyleClass().addAll("es-mono", "es-package-locked");
            box.getChildren().add(held);
            // ⚠ The rename is named. It is the lock, and a player who has not been told that will
            // read the `.pkg` as a failed download rather than as an unpaid invoice.
            Label why = new Label("It is still a vendor package — a `.pkg`. Confirmation is what "
                    + "turns it into a `.upg` this rig can install.");
            why.setWrapText(true);
            why.getStyleClass().add("es-package-note");
            box.getChildren().add(why);
        } else if (pkg.owned()) {
            Label owned = new Label("ALREADY INSTALLED — a second copy adds nothing. This one is "
                    + "worth more sold than installed.");
            owned.setWrapText(true);
            owned.getStyleClass().addAll("es-mono", "es-package-locked");
            box.getChildren().add(owned);
        } else {
            Label ready = new Label("READY — nothing is holding this package.");
            ready.getStyleClass().addAll("es-mono", "es-package-match");
            box.getChildren().add(ready);
        }
        return box;
    }

    /**
     * Install and Sell.
     *
     * <p>⚠ Install is disabled from {@link PackageManifest#installable}, which the rules computed —
     * this panel never decides for itself whether a rule would allow something (client pillar C4).
     * ⚠ And a disabled button still needs to say <em>why</em>: the STATUS block above it always does,
     * so the button never has to carry the explanation and never appears refused for no visible
     * reason.
     */
    private static Region actions(
            GameSession session,
            PackageManifest pkg,
            Runnable onAction,
            java.util.function.Consumer<String> report) {
        BreachView.Chip install = new BreachView.Chip("Install", "es-breach-chip-loud");
        install.setDisable(!pkg.installable());
        install.setAccessibleText(pkg.installable()
                ? "Install " + pkg.displayName() + ". The package is consumed."
                : "Install is unavailable: " + (pkg.locked() ? "the payment has not been mined yet."
                        : "this tool is already installed."));
        install.onInvoke(() -> {
            report.accept(session.install(pkg.path()).message());
            onAction.run();
        });

        BreachView.Chip sell = new BreachView.Chip("Sell", "es-breach-chip-quiet");
        // ⚠ Not gated on installable(): an ALREADY OWNED package cannot be installed and is precisely
        // the one most worth selling. Only the confirmation hold stops a sale, because selling a
        // package whose payment has not been mined turns goods that are not paid for into spendable
        // ethecoin.
        sell.setDisable(pkg.locked());
        sell.setAccessibleText("Sell " + pkg.displayName() + " on the secondary market. Only "
                + "ethecoin-gated tools can be sold.");
        sell.onInvoke(() -> {
            report.accept(session.sell(pkg.path()).message());
            onAction.run();
        });

        HBox row = Ui.row(UiTokens.SPACE_3, install, sell);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static Region digest(String name, String sha) {
        Label label = new Label(pad(name) + sha);
        label.getStyleClass().addAll("es-mono", "es-package-sha");
        return label;
    }

    private static Region field(String name, String value) {
        Label label = new Label(pad(name) + value);
        label.getStyleClass().addAll("es-mono", "es-package-field");
        label.setWrapText(true);
        HBox.setHgrow(label, Priority.ALWAYS);
        return label;
    }

    private static Label heading(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("es-package-heading");
        return label;
    }

    /** A fixed-width label column, so the values line up down the panel. */
    private static String pad(String name) {
        return (name + "            ").substring(0, 12);
    }
}

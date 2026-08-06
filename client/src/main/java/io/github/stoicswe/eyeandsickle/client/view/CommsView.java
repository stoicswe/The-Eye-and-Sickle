package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * COMS — the rig's inbox.
 *
 * <h2>⚠ TWO SOURCES, ONE WINDOW, AND THEY ARE NOT THE SAME LIST</h2>
 *
 * <b>INBOX</b> is the game talking to the player: a vendor making contact, an event worth a sentence.
 * Every message is authored by the rules, lives in the save, and is trusted — one of them carries an
 * entitlement to an item. <b>DIRECT</b> is player-to-player conversation, which is <em>Bluesky's</em>,
 * reached through the player's own account, and never touches a save.
 *
 * <p>They share a window because that is where a player looks for "who said something to me", and
 * they share nothing else. Merging the two types is how a message somebody else wrote ends up in a
 * list whose entries can grant items — <b>I14</b> at the smallest possible scale. The tab strip is
 * the seam, and it is deliberately visible.
 *
 * <h2>What this replaced</h2>
 *
 * A prose stub describing what the window would be, which had been correct for as long as there was
 * nothing to put in it. {@code docs/design/12} is still {@code [PROPOSAL]} and the informant and
 * evidence systems are still unbuilt — what exists now is the delivery surface those will use, plus
 * the one message the rules currently send.
 */
public final class CommsView {

    private CommsView() {}

    private static final DateTimeFormatter WHEN =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.systemDefault());

    /**
     * @param session the session
     * @param direct the Bluesky pane, or {@code null} when the client has no account configured
     */
    public static Region create(GameSession session, Region direct) {
        javafx.scene.control.TabPane tabs = new javafx.scene.control.TabPane();
        tabs.getStyleClass().add("es-tabs");
        tabs.setTabClosingPolicy(javafx.scene.control.TabPane.TabClosingPolicy.UNAVAILABLE);

        javafx.scene.control.Tab inbox = new javafx.scene.control.Tab(Views.t("ui.comms.inbox", "INBOX"), inbox(session));
        tabs.getTabs().add(inbox);
        if (direct != null) {
            // ⚠ THE LABEL CHANGED, THE KEY DID NOT. `ui.comms.direct` is what any translation is
            // filed under, and the class behind it is still `DirectView` — renaming either to follow
            // a display name moves three things to change what one strip says, which is the rule
            // COMMS' own id already records from being relabelled COMPort.
            tabs.getTabs().add(new javafx.scene.control.Tab(Views.t("ui.comms.direct", "ALO MESSENGER"), direct));
        }
        return tabs;
    }

    /**
     * The engine's inbox: a list on the left, the message on the right.
     *
     * <h2>⚠ Rebuilt on CHANGE, not on a Pulse</h2>
     *
     * A message arrives when the rules send one, which is a state change and not a function of wall
     * time — so a one-second repaint would be work with no subject, and it would tear the selection
     * out from under a player halfway through reading. The one thing here that IS wall-clock derived
     * is the relative age beside each subject, and that gets its own slow clock.
     */
    private static Region inbox(GameSession session) {
        VBox list = new VBox(1);

        VBox reader = new VBox(UiTokens.SPACE_3);
        reader.getStyleClass().add("es-body-pad");
        VBox.setVgrow(reader, Priority.ALWAYS);

        String[] selected = {""};
        Runnable[] repaint = new Runnable[1];

        repaint[0] = () -> {
            List<GameSession.InboxMessage> messages = session.messages();
            list.getChildren().clear();

            if (messages.isEmpty()) {
                Label empty = Views.secondary(Views.t(
                        "ui.comms.empty", "Nothing yet. This is where the game gets in touch with you."));
                empty.setWrapText(true);
                list.getChildren().add(empty);
                reader.getChildren().setAll(Views.secondary(Views.t("ui.comms.no-selection", "No message selected.")));
                return;
            }

            // ⚠ Keep the selection across a rebuild, and fall back to the newest when the selected
            // message is gone. Without the fallback, a trimmed message leaves the reader showing a
            // message that no longer exists, which reads as the panel having frozen.
            boolean stillThere = messages.stream().anyMatch(m -> m.messageId().equals(selected[0]));
            if (!stillThere) {
                selected[0] = messages.getFirst().messageId();
            }

            for (GameSession.InboxMessage message : messages) {
                list.getChildren().add(row(session, message, selected, repaint));
            }
            messages.stream()
                    .filter(m -> m.messageId().equals(selected[0]))
                    .findFirst()
                    .ifPresent(m -> reader.getChildren().setAll(read(session, m, repaint)));
        };
        repaint[0].run();

        // Opening the window is the moment a player has "seen" the newest message enough for the
        // badge to be honest about, but NOT enough to mark it read — read means read.
        AutoCloseable onSession = session.onChange(s -> repaint[0].run());

        // ⚠ THE WIDTH GOES ON THE SCROLLER, NOT ON THE CONTENT INSIDE IT. Setting min/pref/max on
        // the inner VBox looks right and does nothing: an HBox distributes width to its OWN
        // children, and a ScrollPane's minimum is small and unrelated to what it contains — so the
        // list column was squeezed to about 45px and the reading pane was laid out over the top of
        // it, leaving three truncated words down the left edge. Found by rendering.
        Region listPane = Views.scrollable(list);
        listPane.setMinWidth(190);
        listPane.setPrefWidth(210);
        listPane.setMaxWidth(230);

        Region readerPane = Views.scrollable(reader);
        // ⚠ And the reader needs a minimum of ZERO, or the row demands list + reader before it
        // distributes anything and the last child runs off the panel — the same failure
        // AnonShareView records for its Canvas column, from the other side.
        readerPane.setMinWidth(0);

        HBox split = new HBox(listPane, new Separator(javafx.geometry.Orientation.VERTICAL), readerPane);
        HBox.setHgrow(readerPane, Priority.ALWAYS);
        split.setFillHeight(true);
        VBox root = new VBox(split);
        VBox.setVgrow(split, Priority.ALWAYS);
        Views.releaseOnDetach(root, onSession);
        return root;
    }

    /** One row in the list: unread marker, subject, sender, age. */
    private static Region row(
            GameSession session, GameSession.InboxMessage message, String[] selected, Runnable[] repaint) {
        VBox box = new VBox(1);
        box.getStyleClass().add("es-comms-row");
        if (message.messageId().equals(selected[0])) {
            box.getStyleClass().add("es-comms-row-on");
        }

        Label subject = new Label(message.subject());
        subject.getStyleClass().add(message.read() ? "es-comms-subject" : "es-comms-subject-unread");
        subject.setWrapText(true);

        Label meta = new Label(message.from() + "  ·  " + age(message.receivedAt()));
        meta.getStyleClass().add("es-comms-meta");

        box.getChildren().addAll(subject, meta);
        // ⚠ An offer that is still there is the one thing worth marking in the LIST, because it is
        // the only message state with a consequence the player can miss by not scrolling.
        if (message.hasOffer()) {
            Label chip = new Label(Views.t("ui.comms.attachment", "ATTACHMENT"));
            chip.getStyleClass().add("es-comms-offer");
            box.getChildren().add(chip);
        }

        box.setOnMouseClicked(e -> {
            selected[0] = message.messageId();
            session.markMessageRead(message.messageId());
            repaint[0].run();
        });
        box.setAccessibleText((message.read() ? "" : "Unread. ") + message.subject() + ", from " + message.from());
        io.github.stoicswe.eyeandsickle.client.ui.cursors.Cursors.shared().clickable(box);
        return box;
    }

    /** The reading pane. */
    private static List<javafx.scene.Node> read(
            GameSession session, GameSession.InboxMessage message, Runnable[] repaint) {
        Label subject = new Label(message.subject());
        subject.getStyleClass().add("es-comms-read-subject");
        subject.setWrapText(true);

        Label from = Views.secondary(Views.t("ui.comms.from", "from") + " " + message.from() + "  ·  "
                + WHEN.format(message.receivedAt()));

        Label body = new Label(message.body());
        body.setWrapText(true);
        body.getStyleClass().add("es-comms-body");

        List<javafx.scene.Node> nodes = new java.util.ArrayList<>(List.of(subject, from, new Separator(), body));

        if (!message.offerItemType().isBlank()) {
            Label result = new Label();
            result.setWrapText(true);
            if (message.offerClaimed()) {
                nodes.add(new Separator());
                nodes.add(Views.secondary(
                        Views.t("ui.comms.claimed", "Attachment collected: ") + message.offerName()));
            } else {
                Button collect = new Button(Views.t("ui.comms.collect", "Collect") + "  " + message.offerName());
                collect.setOnAction(e -> {
                    GameSession.Outcome outcome = session.claimMessageOffer(message.messageId());
                    result.setText(outcome.message());
                    Views.styleByOutcome(result, outcome);
                    repaint[0].run();
                });
                HBox actions = new HBox(UiTokens.SPACE_3, collect);
                actions.setAlignment(Pos.CENTER_LEFT);
                nodes.add(new Separator());
                nodes.add(actions);
                nodes.add(Views.secondary(Views.t(
                        "ui.comms.collect-note",
                        "It downloads to ~/Downloads like anything else, and installs from there.")));
                nodes.add(result);
            }
        }
        return nodes;
    }

    /**
     * "4m", "3h", "2d" — how long ago, at the granularity a mail client shows.
     *
     * <p>⚠ Reads the wall clock, deliberately and narrowly. This is "how long ago did this land in
     * front of me", which is a question about the player's afternoon rather than a game deadline —
     * the same carve-out the event bus's {@code time} and the frost's pacing already take. Nothing
     * decides anything from it.
     */
    static String age(Instant when) {
        Duration since = Duration.between(when, Instant.now());
        if (since.isNegative()) {
            return "now";
        }
        long minutes = since.toMinutes();
        if (minutes < 1) {
            return "now";
        }
        if (minutes < 60) {
            return minutes + "m";
        }
        long hours = since.toHours();
        if (hours < 24) {
            return hours + "h";
        }
        return since.toDays() + "d";
    }
}

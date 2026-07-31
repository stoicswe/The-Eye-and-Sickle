package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.events.CloudEvent;
import io.github.stoicswe.eyeandsickle.client.events.EventRecorder;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * EVENTS — every {@link CloudEvent} the client's broker has carried this session.
 *
 * <h2>What this is for</h2>
 *
 * Debugging. When a panel does not update, or an action appears to do nothing, the question is
 * whether the event was published, whether it carried what was expected, and what order things
 * actually happened in. A stream that answers all three turns "it did not work" into a line number.
 *
 * <h2>⚠ Every attribute is shown, including the ones that look like noise</h2>
 *
 * The {@code id} and {@code source} in particular. They are what makes CloudEvents' uniqueness rule
 * checkable — {@code source + id} identifies an event, so a duplicate that shares both is a
 * redelivery and one that does not is a second thing happening. A log that dropped them for tidiness
 * would remove the only way to tell those apart, which is precisely the distinction somebody is here
 * to make.
 *
 * <h2>⚠ Reads the recorder, does not subscribe</h2>
 *
 * The bus records unconditionally in its own constructor, so everything published before this window
 * was ever opened is already there. A panel that subscribed instead would show only what happened
 * after a player thought to look — which is never when the interesting thing happened.
 */
public final class EventLogView {

    private EventLogView() {}

    private static final DateTimeFormatter STAMP =
            DateTimeFormatter.ofPattern("HH:mm:ss.SSS").withZone(ZoneId.systemDefault());

    public static Region create(GameSession session) {
        VBox root = new VBox(UiTokens.SPACE_3);
        EventRecorder recorder = session.events().recorder();

        Label explain = new Label(Views.t(
                "ui.event-log.every-event-the-broker",
                "Every event the broker carried, oldest first. CloudEvents v1.0.2 — "
                        + "an event is identified by its source and id together, so two rows sharing both are "
                        + "one event delivered twice."));
        explain.setWrapText(true);
        explain.getStyleClass().add("es-text-secondary");

        TextField search = new TextField();
        search.setPromptText("Filter by type, subject or payload");
        HBox.setHgrow(search, Priority.ALWAYS);

        io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch follow =
                new io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch(Views.t("ui.event-log.follow", "Follow"));
        follow.setSelected(true);
        follow.setAccessibleText("Scroll to the newest event as it arrives");

        Label count = Ui.micro("");
        BreachView.Chip clear = new BreachView.Chip("Clear", "es-breach-chip-quiet");
        clear.setAccessibleText("Forget every recorded event, so the next interaction stands alone.");

        HBox controls = Ui.row(UiTokens.SPACE_3, search, follow, clear, count);
        controls.setAlignment(Pos.CENTER_LEFT);

        ListView<CloudEvent> list = new ListView<>();
        list.getStyleClass().add("es-terminal");
        list.setPlaceholder(new Label(Views.t(
                "ui.event-log.no-events-yet-anything", "No events yet. Anything you do in the game appears here.")));
        list.setCellFactory(view -> new ListCell<>() {
            @Override
            protected void updateItem(CloudEvent event, boolean empty) {
                super.updateItem(event, empty);
                if (empty || event == null) {
                    setText(null);
                    setTooltip(null);
                    return;
                }
                String payload = event.payload();
                setText(String.format(
                        "%s  %-28s %-22s %s",
                        event.time() == null ? "--:--:--.---" : STAMP.format(event.time()),
                        event.shortType(),
                        event.subject() == null ? "-" : event.subject(),
                        payload));
                // ⚠ id and source live in the tooltip rather than the row. They are the two
                // attributes that identify an event and the two nobody reads while scanning — on the
                // row they would push the type and payload off the right edge, and the whole value of
                // a stream is being able to scan it.
                javafx.scene.control.Tooltip tip = new javafx.scene.control.Tooltip("type    " + event.type()
                        + "\nid      " + event.id()
                        + "\nsource  " + event.source()
                        + "\nspec    " + event.specversion()
                        + (event.subject() == null ? "" : "\nsubject " + event.subject())
                        + (payload.isBlank() ? "" : "\ndata    " + payload));
                tip.setWrapText(true);
                tip.setMaxWidth(460);
                setTooltip(tip);
                setAccessibleText(event.shortType() + " about "
                        + (event.subject() == null ? "nothing in particular" : event.subject())
                        + ". " + payload);
            }
        });
        VBox.setVgrow(list, Priority.ALWAYS);

        Runnable refresh = () -> {
            String query =
                    search.getText() == null ? "" : search.getText().trim().toLowerCase();
            List<CloudEvent> events = recorder.events().stream()
                    .filter(event -> query.isEmpty()
                            || event.type().toLowerCase().contains(query)
                            || String.valueOf(event.subject()).toLowerCase().contains(query)
                            || event.payload().toLowerCase().contains(query))
                    .toList();
            // Only touch the list when it actually changed: replacing the items every tick would
            // fight the reader's scroll position and selection — the same rule OVERVIEW follows.
            if (!events.equals(list.getItems())) {
                list.getItems().setAll(events);
                if (follow.isSelected() && !events.isEmpty()) {
                    list.scrollTo(events.size() - 1);
                }
            }
            count.setText(recorder.size() + " held"
                    + (recorder.dropped() > 0 ? "  ·  " + recorder.dropped() + " dropped" : "")
                    + (query.isEmpty() ? "" : "  ·  " + events.size() + " shown"));
        };
        refresh.run();
        search.textProperty().addListener((observable, was, now) -> refresh.run());
        clear.onInvoke(() -> {
            recorder.clear();
            refresh.run();
        });

        // ⚠ On the clock, not on session change. Most events are published by things that never touch
        // the save — a window opening, an intent refused — so onChange would miss exactly the ones a
        // developer opened this tab to see. Pulse.every, because this is data: suppressing it under
        // reduced motion would remove the readout rather than an animation.
        AutoCloseable clock = Pulse.shared().every(500, refresh);
        Views.releaseOnDetach(root, clock);

        root.getChildren().addAll(explain, controls, list);
        return root;
    }
}

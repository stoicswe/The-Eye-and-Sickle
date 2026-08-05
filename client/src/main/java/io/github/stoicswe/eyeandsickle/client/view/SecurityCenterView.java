package io.github.stoicswe.eyeandsickle.client.view;

import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import io.github.stoicswe.eyeandsickle.client.shell.Shell;
import io.github.stoicswe.eyeandsickle.client.ui.Pulse;
import io.github.stoicswe.eyeandsickle.client.ui.Ui;
import io.github.stoicswe.eyeandsickle.client.ui.UiTokens;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.SectionMark;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.SecurityMark;
import io.github.stoicswe.eyeandsickle.client.ui.widgets.Switch;
import io.github.stoicswe.eyeandsickle.protocol.game.ScanScheduleView;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

/**
 * SECURITY CENTER — what got in, what was supposed to stop it, and when to look again.
 *
 * <h2>The layout is a consumer security suite; the LOOK is not</h2>
 *
 * The structure is deliberately borrowed from the shape every real antivirus product has settled on,
 * because that shape is genuinely good and a player already knows how to read it: a section rail down
 * the left, one status card per subsystem, and a single loud verdict with one primary action beside
 * it. A player who has ever seen such a product knows what this window is for before reading a word.
 *
 * <h2>⚠ NONE of the reference's styling is reproduced, and that is not a shortfall</h2>
 *
 * {@code docs/design/ui-design-language.md} §9 makes drop shadows, blur and glassmorphism
 * <b>build-blocking</b> — {@code UiContractTest} fails the build on {@code dropshadow(} anywhere in a
 * stylesheet — and rounded corners are an opt-in gated on {@code .es-rounded}. §2.1 bans a semantic
 * colour system and reserves amber and alarm for named meanings. So the soft gradients, the glowing
 * circular button and the blurred colour field are not available, and imitating them would be the
 * "competent dark-mode developer tool" failure §1 names, arrived at from the opposite direction.
 *
 * <p>What replaces them is this client's own vocabulary for the same jobs: a discrete cell meter
 * where the reference has a gradient ring, a stepped sparkline where it has a smooth one, a hazard
 * band where it has a coloured glow, and the verdict carried by <b>type size and position</b>
 * rather than by a halo. §4.4's rule does the work — state survives greyscale and reaches a screen
 * reader, which a glow never does.
 *
 * <h2>⚠ The verdict is the only place alarm is spent</h2>
 *
 * §2.1 rations {@code -es-alarm} to loss and hostile state and asks for at most twice a screen. A
 * security panel is exactly where that budget belongs, and it is spent on one thing: the headline,
 * when there is something to find. Everything else stays on the neutral ramp so the headline means
 * something when it changes.
 */
public final class SecurityCenterView {

    private SecurityCenterView() {}

    /** The sections down the left, in the order a player works through them. */
    private enum Section {
        /** The verdict and the primary action. */
        HOME("HOME"),

        /** The audit: processes, connections, storage. */
        AUDIT("AUDIT"),

        /** Firewalls, canaries, tarpits. */
        DEFENSE("DEFENSE"),

        /** When to look again. */
        SCHEDULE("SCHEDULE");

        private final String label;

        Section(String label) {
            this.label = label;
        }

        String label() {
            return label;
        }
    }

    /**
     * @param session the session
     * @param shell the local shell, for the audit's listings
     * @return the tool
     */
    public static Region create(GameSession session, Shell shell) {
        Section[] section = {Section.HOME};

        Region audit = withMark(AuditView.create(session, shell), SectionMark.Kind.DETECTIVE);
        Region defense = withMark(Views.defense(session), SectionMark.Kind.CASTLE);
        VBox home = new VBox(UiTokens.SPACE_4);
        VBox schedule = new VBox(UiTokens.SPACE_3);
        Region scheduleSection = withMark(schedule, SectionMark.Kind.CLOCK);

        Label result = new Label();
        result.setWrapText(true);

        VBox rail = new VBox(UiTokens.SPACE_1);
        rail.getStyleClass().add("es-sec-rail");
        rail.setMinWidth(UiTokens.SECURITY_RAIL_WIDTH);
        rail.setPrefWidth(UiTokens.SECURITY_RAIL_WIDTH);
        rail.setMaxWidth(UiTokens.SECURITY_RAIL_WIDTH);

        VBox body = new VBox();
        VBox.setVgrow(body, Priority.ALWAYS);
        body.getChildren().addAll(home, audit, defense, scheduleSection);

        Runnable[] repaint = new Runnable[1];
        List<Label> chips = new java.util.ArrayList<>();
        for (Section value : Section.values()) {
            Label chip = Ui.label(value.label());
            chip.getStyleClass().add("es-sec-nav");
            chip.setMaxWidth(Double.MAX_VALUE);
            chip.setOnMouseClicked(event -> {
                section[0] = value;
                repaint[0].run();
            });
            chip.setAccessibleText("Show " + value.label().toLowerCase(Locale.ROOT) + ".");
            chips.add(chip);
            rail.getChildren().add(chip);
        }

        repaint[0] = () -> {
            for (int i = 0; i < chips.size(); i++) {
                Label chip = chips.get(i);
                chip.getStyleClass().remove("es-sec-nav-on");
                if (Section.values()[i] == section[0]) {
                    chip.getStyleClass().add("es-sec-nav-on");
                }
            }
            visible(home, section[0] == Section.HOME);
            visible(audit, section[0] == Section.AUDIT);
            visible(defense, section[0] == Section.DEFENSE);
            visible(scheduleSection, section[0] == Section.SCHEDULE);
        };

        // ⚠ Survives the repaint, so the mark's animation is not reset every second. See buildHome.
        SecurityMark[] mark = new SecurityMark[1];
        buildHome(home, session, section, repaint, result, mark);
        buildSchedule(schedule, session, result, repaint);
        repaint[0].run();

        // ⚠ Pulse.every — DATA. The verdict and the countdown are both derived from wall time and
        // from state a scan changes without the player touching anything, so an onChange listener
        // would leave a stale "no threats" on screen while a scan was finding one.
        AutoCloseable clock = Pulse.shared().every(1000, () -> {
            buildHome(home, session, section, repaint, result, mark);
            buildSchedule(schedule, session, result, repaint);
            repaint[0].run();
        });

        HBox split = new HBox(rail, body);
        HBox.setHgrow(body, Priority.ALWAYS);
        split.setFillHeight(true);

        VBox page = new VBox(UiTokens.SPACE_3, split, result);
        page.getStyleClass().add("es-sec");
        // ⚠ Both released together: the panel's own clock AND whatever mark is current. A Pulse
        // subscription outlives the node that made it — `CycleGrid.dispose` and `CoreCage.dispose`
        // were written, correct, and called by nobody, and every open of the rig monitor leaked one.
        Views.releaseOnDetach(page, clock);
        Views.releaseOnDetach(page, () -> {
            if (mark[0] != null) {
                mark[0].dispose();
            }
        });
        return page;
    }

    /**
     * The verdict, from what the last audit found and when.
     *
     * <h2>⚠ THE MARK IS ABOUT THE AUDIT, and folding anything else in broke the panel</h2>
     *
     * This briefly also went to CHECK when nothing was armed — reasonable-sounding, and wrong in a
     * way that made the whole tool look broken: on a rig with no defences, running a clean audit
     * left the mark on the same warning triangle it already had. <b>The panel's primary action
     * appeared to do nothing.</b> A player cannot tell "your audit changed nothing" from "the button
     * is broken", and they will conclude the second.
     *
     * <p>So the verdict answers exactly the question an audit answers — <em>is something on this rig
     * right now</em> — and being undefended is a statement about the <em>future</em>, which belongs
     * on the DEFENSE card and in the reason line beneath the verdict. Both are still said; only the
     * mark is narrowed.
     *
     * <p>⚠ Pure and package-private so it can be tested without a toolkit. The derivation is the part
     * that can be wrong; the rendering is not.
     */
    /**
     * The most recent audit, or null if the rig has never been scanned.
     *
     * <h2>⚠ {@code scanReports()} IS NEWEST FIRST, and this read it backwards</h2>
     *
     * {@code GameSession.scanReports} documents "newest first" and {@code GameEngine} reverses the
     * stored list to deliver it that way. This panel called {@code getLast()}, which is therefore the
     * <b>oldest</b> audit on file — so the verdict was pinned to the player's very first scan and
     * never moved again however many they ran. Reported from a rig with eleven audits on file still
     * reading "the last quick audit was clean, but that was a while ago".
     *
     * <p>⚠ <b>It is silent, and it gets MORE wrong with use.</b> On a fresh rig the first audit is
     * also the last one, so the panel is correct exactly until the second scan — which is the point
     * at which nobody is looking at it any more.
     *
     * <p>⚠ Pure and package-private <b>so it can be tested without a toolkit</b>, which is the same
     * reason {@link #markStateFor} is, and for the same reason: the last verdict bug shipped because
     * the rule lived inside a repaint that needed a live scene to reach. {@code AuditView} consumes
     * the same list correctly, so the contract was right and only this caller was wrong.
     *
     * @param reports the session's audits, newest first
     * @return the newest, or null if there are none
     */
    /**
     * Puts a section's illustration in its top-right corner, over the section's own content.
     *
     * <h2>⚠ An OVERLAY, not a row, and the difference is that the sections are not ours</h2>
     *
     * AUDIT is {@code AuditView} and DEFENSE is {@code Views.defense} — both are complete panels that
     * predate this window and are used elsewhere. Reaching inside them to add a header cell would
     * mean editing two views to decorate a third, and would put the mark in a different place in each
     * depending on what their first row happens to be. A {@code StackPane} keeps the mark's placement
     * identical across all three sections and leaves both views untouched.
     *
     * <p>⚠ The mark is <b>mouse-transparent</b> ({@code SectionMark}'s constructor) so it cannot
     * swallow a click meant for the panel beneath it — the corner of these sections holds real
     * controls, and a silent dead zone there is the sort of bug nobody manages to describe.
     *
     * @param content the section's own panel, untouched
     * @param kind which illustration
     * @return the section with its mark laid over the top-right corner
     */
    private static Region withMark(Region content, SectionMark.Kind kind) {
        SectionMark mark = new SectionMark(kind);
        javafx.scene.layout.StackPane stacked = new javafx.scene.layout.StackPane(content, mark);
        javafx.scene.layout.StackPane.setAlignment(mark, javafx.geometry.Pos.TOP_RIGHT);
        javafx.scene.layout.StackPane.setMargin(
                mark, new javafx.geometry.Insets(UiTokens.SPACE_5, UiTokens.SPACE_6, 0, 0));
        return stacked;
    }

    static io.github.stoicswe.eyeandsickle.protocol.game.ScanReport latestOf(
            java.util.List<io.github.stoicswe.eyeandsickle.protocol.game.ScanReport> reports) {
        return reports == null || reports.isEmpty() ? null : reports.getFirst();
    }

    static SecurityMark.State markStateFor(boolean everScanned, boolean clean, boolean stale) {
        if (everScanned && !clean) {
            return SecurityMark.State.QUARANTINE;
        }
        // Never looked, or looked so long ago the answer has expired. Both are "unknown", which is
        // not the same as "hostile" and must not borrow its colour.
        return (!everScanned || stale) ? SecurityMark.State.CHECK : SecurityMark.State.CLEAR;
    }

    private static void visible(Region node, boolean on) {
        node.setVisible(on);
        node.setManaged(on);
    }

    // ── the verdict ───────────────────────────────────────────────────────────────────────────

    private static void buildHome(
            VBox box, GameSession session, Section[] section, Runnable[] repaint, Label result, SecurityMark[] mark) {
        box.getChildren().clear();

        var reports = session.scanReports();
        var latest = latestOf(reports);
        // ⚠ The verdict is derived from the LAST SCAN, not from live state, and the distinction is
        // the whole honesty of this panel: a security product can only tell you what it found when
        // it last looked. "Nothing found" and "nobody has looked" are different sentences and a
        // player must be able to tell them apart.
        boolean everScanned = latest != null;
        boolean clean = everScanned && latest.clean();
        java.time.Instant asOf = session.scanSchedule().asOf();
        // ⚠ THREE states, not two, and the middle one is the point of the mark. A clean audit is a
        // statement about a MOMENT — nothing stops something landing the second after it finishes —
        // so a week-old "clear" is not clear, it is UNKNOWN. Likewise a rig with nothing armed has
        // not been compromised; it is simply undefended. Neither deserves the alarm a real finding
        // gets, and collapsing them into it would cry wolf until the player stopped reading.
        boolean stale = !everScanned
                || java.time.Duration.between(latest.finishedAt(), asOf)
                                .compareTo(io.github.stoicswe.eyeandsickle.engine.rules.ScanSchedule.STALE_AFTER)
                        > 0;
        boolean undefended = session.defenses().isEmpty();
        SecurityMark.State markState = markStateFor(everScanned, clean, stale);

        Label lead = new Label("Your rig is");
        lead.getStyleClass().add("es-sec-lead");
        Label verdict = new Label(
                switch (markState) {
                    case QUARANTINE -> "Compromised";
                    case CHECK -> everScanned ? "Unverified" : "Unaudited";
                    case CLEAR -> "Clear";
                });
        verdict.getStyleClass().add("es-sec-verdict");
        // ⚠ The ONE place -es-alarm is spent on this screen. §2.1 rations it to loss and hostile
        // state and asks for at most twice a screen; a finding is exactly that, and keeping
        // everything else neutral is what makes the change mean something.
        verdict.getStyleClass()
                .add(
                        switch (markState) {
                            case QUARANTINE -> "es-sec-hit";
                            case CHECK -> "es-sec-unknown";
                            case CLEAR -> "es-sec-clear";
                        });
        // ⚠ Never ellipsised. This one word is the whole panel; "Compromi..." is worse than no
        // verdict at all, because a player reads the shape rather than the letters and "Clear" and
        // "Compromised" have different shapes only if both are complete.
        verdict.setMinWidth(Region.USE_PREF_SIZE);
        lead.setMinWidth(Region.USE_PREF_SIZE);

        // ⚠ "Found nothing" is a RESULT, not an absence — and the tier is named with it, because a
        // clean Quick and a clean Thorough are different claims about the same rig.
        // ⚠ Says WHICH of the two middle conditions is true. "Needs attention" without naming the
        // reason is a mark with no action attached to it, and the two have completely different
        // fixes — one is a scan, the other is arming something.
        String why = !everScanned
                ? "No audit has ever run on this rig."
                : !latest.clean()
                        ? "Last " + latest.tier() + " audit named " + latest.found()
                                + (latest.found() == 1 ? " process." : " processes.")
                        : stale
                                ? "The last " + latest.tier() + " audit was clean, but that was a " + "while ago."
                                // ⚠ The defence gap is STILL SAID, it just no longer drives the
                                // mark. It is a statement about the future rather than about what is
                                // on the rig now, and the DEFENSE card carries it too.
                                : undefended
                                        ? "Last " + latest.tier() + " audit found nothing — but "
                                                + "nothing is standing guard."
                                        : "Last " + latest.tier() + " audit found nothing.";
        Label since = Ui.micro(why);
        since.setWrapText(true);

        VBox headline = new VBox(UiTokens.SPACE_1, lead, verdict, since);

        // The primary action. Square, per §9 — the reference's glowing disc is not available and a
        // rectangle that says what it does is not a worse control.
        Button runScan = new Button("RUN FULL AUDIT");
        runScan.getStyleClass().addAll("es-market-buy", "es-sec-action");
        runScan.setOnAction(event -> {
            GameSession.Outcome outcome = session.scan("full");
            result.setText(outcome.message());
            Views.styleByOutcome(result, outcome);
            repaint[0].run();
        });
        Label actionNote = Ui.micro("A full audit walks every path on the rig. It costs cycles and "
                + "takes real time; the rig keeps running while it does.");
        actionNote.setWrapText(true);
        actionNote.setMaxWidth(UiTokens.SECURITY_CARD_WIDTH);

        VBox action = new VBox(UiTokens.SPACE_2, runScan, actionNote);

        // ── the subsystem cards, one per thing that has a state ───────────────────────────────
        VBox cards = new VBox(UiTokens.SPACE_3);
        cards.getChildren()
                .addAll(
                        card(
                                "AUDIT",
                                everScanned ? "Last run recorded" : "Never run",
                                reports.size() + (reports.size() == 1 ? " audit on file" : " audits on file"),
                                () -> {
                                    section[0] = Section.AUDIT;
                                    repaint[0].run();
                                }),
                        card(
                                "DEFENSE",
                                session.defenses().isEmpty() ? "Nothing standing" : "Standing",
                                session.defenses().size()
                                        + (session.defenses().size() == 1 ? " measure armed" : " measures armed")
                                        + "  ·  costs cycles, never heat",
                                () -> {
                                    section[0] = Section.DEFENSE;
                                    repaint[0].run();
                                }),
                        scheduleCard(session, section, repaint));

        // ⚠ A FlowPane, not an HBox with a spacer. The verdict is 30px type and the action is a
        // 168px button, and in a tiled window there is not room for both on one line — an HBox
        // squeezes the headline and JavaFX ellipsises it, so "Your rig is Compromised" rendered as
        // "Your ...". Found by rendering the deck rather than the panel alone, which is the only
        // place the window is narrow.
        //
        // ⚠ TOP alignment, or the shorter child is centred against the taller one and the button
        // floats halfway down the headline.
        // ⚠ ONLY THE VERDICT PAIRS WITH THE MARK. The action and its note moved OUT of this row and
        // sit below it, which is what lets the mark come up and left instead of stranding itself
        // under the button with a column of empty space beside it.
        //
        // ⚠ AND `setMaxWidth` DOES NOT CONSTRAIN A WRAPPED LABEL'S PREFERRED WIDTH — this is why the
        // mark wrapped even in a wide window. A FlowPane places children at their PREFERRED size, and
        // a `wrapText` Label's preferred width is its whole string on one line however low its
        // maximum is set. So the column reported itself ~900px wide, the pair did not fit, and the
        // mark dropped to the next row while the panel plainly had room. `setPrefWidth` is the fix;
        // `setMaxWidth` alone looks like it should work and silently does not.
        javafx.scene.layout.FlowPane top = new javafx.scene.layout.FlowPane(UiTokens.SPACE_6, UiTokens.SPACE_3);
        top.setRowValignment(javafx.geometry.VPos.TOP);
        headline.setPrefWidth(UiTokens.SECURITY_HEADLINE_WIDTH);
        headline.setMaxWidth(UiTokens.SECURITY_HEADLINE_WIDTH);
        action.setMaxWidth(UiTokens.SECURITY_CARD_WIDTH);
        top.getChildren().add(headline);

        // ⚠ THE MARK IS BUILT ONCE AND KEPT, and rebuilding it every repaint was a real bug rather
        // than a waste. `buildHome` runs on the one-second Pulse, so a fresh SecurityMark each time
        // would reset its step counter every second — the shield's sweep travels a quarter of the
        // way down and jumps back to the top, forever, never completing a pass. It would also leak a
        // Pulse subscription per repaint.
        //
        // So it is replaced only when the STATE changes, which is the only time a different picture
        // is needed. Held in the caller's array because this whole view is static factories.
        if (mark[0] == null || mark[0].state() != markState) {
            if (mark[0] != null) {
                mark[0].dispose();
            }
            mark[0] = new SecurityMark(markState);
        }
        top.getChildren().add(mark[0]);

        // The action sits under the verdict-and-mark row rather than inside it.
        box.getChildren().addAll(top, action, cards);
    }

    /**
     * One subsystem card.
     *
     * <p>⚠ The deck's one card recipe — {@code -es-panel-hi} ground and a hairline, square. The
     * reference's soft-shadowed rounded tiles are not available (§9) and depth comes from brightness
     * instead (§2.1), which is the same job done with the one lever this design system has.
     */
    private static Region card(String title, String state, String detail, Runnable open) {
        Label heading = Ui.label(title);
        heading.getStyleClass().addAll("es-panel-title", "es-market-section");
        Label stateLabel = new Label(state);
        stateLabel.getStyleClass().add("es-sec-card-state");
        Label detailLabel = Ui.micro(detail);
        detailLabel.setWrapText(true);

        Button open_ = new Button("Open");
        open_.getStyleClass().add("es-shmark-cancel");
        open_.setOnAction(event -> open.run());

        VBox card = new VBox(
                UiTokens.SPACE_2, Ui.row(UiTokens.SPACE_3, heading, Ui.spacer(), open_), stateLabel, detailLabel);
        card.getStyleClass().add("es-market-card");
        card.setMaxWidth(UiTokens.SECURITY_CARD_WIDTH);
        return card;
    }

    private static Region scheduleCard(GameSession session, Section[] section, Runnable[] repaint) {
        ScanScheduleView view = session.scanSchedule();
        String state = view.enabled() ? "Every " + view.everyHours() + "h" : "Off";
        String detail = view.enabled()
                ? view.tier() + " audit  ·  next in " + human(view.untilNext())
                        + (view.affordable() ? "" : "  ·  not enough free cycles right now")
                : "Nothing is scheduled. Audits run only when you ask.";
        return card("SCHEDULE", state, detail, () -> {
            section[0] = Section.SCHEDULE;
            repaint[0].run();
        });
    }

    // ── the schedule ──────────────────────────────────────────────────────────────────────────

    private static void buildSchedule(VBox box, GameSession session, Label result, Runnable[] repaint) {
        box.getChildren().clear();
        ScanScheduleView view = session.scanSchedule();

        Label heading = Ui.label("SCHEDULED AUDITS");
        heading.getStyleClass().addAll("es-panel-title", "es-market-section");

        Switch on = new Switch("Run audits on a timer");
        on.setSelected(view.enabled());

        String[] tier = {view.tier()};
        HBox tiers = new HBox(UiTokens.SPACE_1);
        for (String option : new String[] {"quick", "full", "thorough"}) {
            Button button = new Button(option.toUpperCase(Locale.ROOT));
            button.getStyleClass().add("es-shmark-interval");
            if (option.equals(view.tier())) {
                button.getStyleClass().add("es-sec-nav-on");
            }
            button.setOnAction(event -> {
                tier[0] = option;
                apply(session, on.isSelected(), option, (int) Math.round(sliderValue(box)), result, repaint);
            });
            tiers.getChildren().add(button);
        }

        Slider every = new Slider(1, 48, view.everyHours());
        every.setShowTickMarks(true);
        every.setMajorTickUnit(12);
        every.setBlockIncrement(1);
        every.setId("es-sec-every");
        Label everyValue = Ui.micro(view.everyHours() + "h");
        every.valueProperty().addListener((o, was, now) -> everyValue.setText(Math.round(now.doubleValue()) + "h"));
        // ⚠ Applied on RELEASE, not on every value change. The slider fires continuously while it is
        // dragged, and writing the schedule on each frame would persist the save dozens of times per
        // drag and light the disk lamp like a fault.
        every.setOnMouseReleased(
                event -> apply(session, on.isSelected(), tier[0], (int) Math.round(every.getValue()), result, repaint));

        on.selectedProperty()
                .addListener((o, was, now) ->
                        apply(session, now, tier[0], (int) Math.round(every.getValue()), result, repaint));

        // ⚠ No warning glyph in the string — U+26A0 is in neither bundled face and
        // GlyphCoverageTest scans SOURCE, so a placeholder literal that gets overwritten at runtime
        // still fails the build. The emphasis is the sentence.
        Label note = Views.wrapped("A scheduled audit costs the same cycles as one you run yourself. If the rig cannot "
                + "pay when the timer comes round, that audit is skipped rather than queued — a "
                + "scan landing at an unpredictable moment could take cycles you were counting "
                + "on. However long you are away, at most one scheduled audit runs when you "
                + "come back.");
        note.getStyleClass().add("es-text-secondary");
        note.setMaxWidth(UiTokens.SECURITY_CARD_WIDTH);

        Label next = Ui.micro(
                view.enabled() ? "Next audit in " + human(view.untilNext()) + "  ·  " + view.cycles() + " cycles" : "");

        box.getChildren()
                .addAll(heading, on, Ui.micro("Depth"), tiers, Ui.micro("How often"), every, everyValue, next, note);
    }

    private static double sliderValue(VBox box) {
        return box.lookup("#es-sec-every") instanceof Slider slider ? slider.getValue() : 6;
    }

    private static void apply(
            GameSession session, boolean enabled, String tier, int hours, Label result, Runnable[] repaint) {
        GameSession.Outcome outcome = session.setScanSchedule(enabled, tier, hours);
        result.setText(outcome.message());
        Views.styleByOutcome(result, outcome);
        repaint[0].run();
    }

    private static String human(Duration left) {
        if (left.isZero()) {
            return "moments";
        }
        long hours = left.toHours();
        return hours > 0 ? hours + "h " + left.toMinutesPart() + "m" : left.toMinutesPart() + "m";
    }
}

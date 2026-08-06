package io.github.stoicswe.eyeandsickle.client.presence;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.client.session.LocalGameSession;
import io.github.stoicswe.eyeandsickle.client.window.WindowSpec;
import io.github.stoicswe.eyeandsickle.engine.GameEngine;
import io.github.stoicswe.eyeandsickle.engine.save.TestSaves;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * That rich presence cannot say anything about the player.
 *
 * <h2>Why this test is the feature</h2>
 *
 * Everything else about Discord presence is a convenience. This is the part that would do harm if it
 * were wrong, and it would do it silently: a leaked handle or machine address goes to the player's
 * friends list and to a third party's servers, and nothing on the player's own screen would say so.
 * The client's own {@code docs/client/00} §7 non-goal was amended to admit this feature at all, and
 * the amendment's conditions are only true while this passes.
 *
 * <h2>⚠ The guarantee is STRUCTURAL and this test checks the structure held</h2>
 *
 * {@link RichPresence#activity} takes a {@link PresenceState} and an {@link Instant}. There is no
 * session in scope for it to read, so a leak needs somebody to change that signature — which is a
 * visible act. What this test adds is the case nobody would notice: the window id the desk publishes
 * <em>does</em> carry a machine address for a shell window, so a plausible "just put the subject in
 * the details line" edit would ship an address to everyone the player knows.
 */
@DisplayName("discord presence never transmits anything about the player")
class PresenceLeakTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-05T12:00:00Z"), ZoneOffset.UTC);

    private static final Instant SINCE = Instant.parse("2026-08-05T11:00:00Z");

    /**
     * ⚠ Distinctive enough that a substring match cannot be a coincidence. A probe of "operator"
     * would collide with ordinary prose and a passing assertion would mean nothing.
     */
    private static final String HANDLE = "zzprobehandlezz";

    private static final String ADDRESS = "203.0.113.77";

    /** Any non-blank id will do: nothing here reaches a real Discord. */
    private static final String FAKE_APP_ID = "000000000000000000";

    /**
     * ⚠ Every frame this class produces, whatever the test.
     *
     * <p>Installed in {@link #isolateTheSingleton} rather than per test, so no test in this class can
     * reach a real Discord even by accident. That is not belt and braces — it is the bug this file
     * already had: {@code noApplicationIdMeansOff} enabled presence without a transport, which was
     * harmless only while no application id was configured and became a live connection to the
     * developer's own Discord the moment one was.
     */
    private final List<String> frames = new CopyOnWriteArrayList<>();

    @BeforeEach
    void isolateTheSingleton() {
        RichPresence.shared().reset();
        RichPresence.shared().useTransport(() -> recorder(frames));
        // ⚠ FORCED, not set through the system property. The property is an override that a blank
        // value falls through — so clearing it means "resolve normally", which finds the id in
        // build.properties on any build that has one. The seam is the only way to say "no id".
        RichPresence.shared().useApplicationId(FAKE_APP_ID);
    }

    @AfterEach
    void restoreTheSingleton() {
        RichPresence.shared().reset();
        System.clearProperty(RichPresence.APPLICATION_ID_PROPERTY);
    }

    @Nested
    @DisplayName("the payload")
    class Payload {

        @Test
        @DisplayName("says its state's own constant and nothing else about the game")
        void saysOnlyItsConstant() {
            for (PresenceState state : PresenceState.values()) {
                assertThat(RichPresence.activity(state, SINCE))
                        .as("payload for %s", state)
                        .contains(state.details());
            }
        }

        @Test
        @DisplayName("⚠ a shell window never carries the machine it is open on")
        void aShellWindowNeverCarriesItsAddress() {
            // The desk publishes `shell:<address>` as the window id, so this is the one subject on
            // the bus that contains a machine's name. Resolving it must keep the state and drop the
            // address — the failure being guarded is an edit that puts the subject in the details.
            PresenceState state = PresenceState.forWindowId("shell:" + ADDRESS);

            assertThat(state).isEqualTo(PresenceState.TERMINAL);
            assertThat(RichPresence.activity(state, SINCE)).doesNotContain(ADDRESS);
        }

        @Test
        @DisplayName("clearing sends a null activity, not an empty one")
        void clearingIsNull() {
            // An empty object leaves a blank presence card standing on the friends list, which is
            // the feature still saying something after the player switched it off.
            assertThat(RichPresence.clearActivity()).contains("\"activity\":null");
        }

        @Test
        @DisplayName("the elapsed timestamp is the instant it was given, in epoch seconds")
        void timestampIsWhatItWasGiven() {
            assertThat(RichPresence.activity(PresenceState.DECK, SINCE))
                    .contains("\"start\":" + SINCE.getEpochSecond());
        }
    }

    @Nested
    @DisplayName("the whole path, with a session open")
    class WholePath {

        @Test
        @DisplayName("⚠ transmits no handle and no character id, through every state")
        void transmitsNothingAboutTheCharacter(@TempDir Path dir) throws Exception {
            GameEngine engine = GameEngine.open(TestSaves.at(dir.resolve("s.json")), HANDLE, CLOCK);
            String characterId = engine.state().characterId;
            LocalGameSession session = new LocalGameSession(engine);

            RichPresence presence = RichPresence.shared();
            presence.setEnabled(true);
            presence.attach(session);

            // ⚠ Only the FIRST update lands promptly — MIN_INTERVAL coalesces the rest, which is
            // Discord's limit and not something to defeat here. One real frame off the real worker
            // is what proves the wiring carries what `activity` builds.
            String sent = awaitFrame(frames);

            assertThat(sent).doesNotContain(HANDLE);
            assertThat(sent).doesNotContain(characterId);
            assertThat(frames).allSatisfy(frame -> {
                assertThat(frame).doesNotContain(HANDLE);
                assertThat(frame).doesNotContain(characterId);
            });
        }

        @Test
        @DisplayName("sends nothing at all while the toggle is off")
        void silentWhenOff(@TempDir Path dir) throws Exception {
            LocalGameSession session =
                    new LocalGameSession(GameEngine.open(TestSaves.at(dir.resolve("s.json")), HANDLE, CLOCK));

            RichPresence presence = RichPresence.shared();
            // Deliberately NOT enabled. Attaching and driving states must stay dark: this is the
            // default every player who never opens the setting is running.
            presence.attach(session);
            presence.show(PresenceState.TERMINAL);
            presence.show(PresenceState.NETWORK);

            Thread.sleep(200);
            assertThat(frames).isEmpty();
            assertThat(presence.isEnabled()).isFalse();
        }

        @Test
        @DisplayName("⚠ refuses to start with no application id, rather than throwing")
        void noApplicationIdMeansOff() {
            RichPresence presence = RichPresence.shared();
            // ⚠ THROUGH THE SEAM, NOT BY CLEARING THE PROPERTY, and the difference is the whole
            // reason the seam exists. Clearing the property means "resolve normally", which finds
            // whatever `<discord.app.id>` puts in build.properties — so the first version of this
            // test passed only while the pom was empty and failed the moment a real id was set.
            // A test that is green because a build setting happens to be unconfigured is testing
            // the build, not the code.
            presence.useApplicationId("");

            presence.setEnabled(true);

            // A fork, or any local build, has no id. That has to be a quiet no-op: a build where
            // enabling a setting threw would fail for everyone who never configured a feature they
            // did not ask for.
            assertThat(presence.isEnabled()).isFalse();
            assertThat(presence.describe()).contains("no Discord application id");
            assertThat(frames).isEmpty();
        }

        @Test
        @DisplayName("a configured id lets it start, so the check above is about the id")
        void anApplicationIdLetsItStart() {
            // The other half of the pair. Without it, `noApplicationIdMeansOff` would still pass if
            // presence could never start at all for some unrelated reason — a test asserting a
            // refusal needs a partner asserting the thing is otherwise reachable.
            RichPresence presence = RichPresence.shared();

            presence.setEnabled(true);

            assertThat(presence.isEnabled()).isTrue();
            assertThat(presence.describe()).doesNotContain("no Discord application id");
        }
    }

    @Nested
    @DisplayName("the vocabulary")
    class Vocabulary {

        @Test
        @DisplayName("⚠ every tool window resolves to a state, by id and by spec")
        void everyWindowIsFiled() {
            // forWindow is exhaustive at compile time; this covers the OTHER route, which goes
            // through a string and could silently fall through to DECK for a window whose id
            // changed. A window quietly reporting as "On the deck" looks exactly like the feature
            // working.
            for (WindowSpec spec : WindowSpec.values()) {
                assertThat(PresenceState.forWindowId(spec.id()))
                        .as("window %s", spec.id())
                        .isEqualTo(PresenceState.forWindow(spec));
            }
        }

        @Test
        @DisplayName("⚠ every tool but Settings reports something of its own")
        void everyToolHasItsOwnLine() {
            // The point of the feature: a friends list should learn what somebody is doing, and two
            // tools sharing a line means one of them is invisible. Walks the catalogue rather than a
            // list, so a fifteenth window that forgot its state fails here.
            for (WindowSpec spec : WindowSpec.values()) {
                PresenceState state = PresenceState.forWindow(spec);
                if (spec == WindowSpec.SETTINGS) {
                    continue;
                }
                assertThat(state)
                        .as("%s should report something of its own, not the generic deck line", spec.id())
                        .isNotEqualTo(PresenceState.DECK);
            }

            // Distinct STATES is not enough — two states could carry the same sentence, and then the
            // enum looks right while the friends list cannot tell the two tools apart.
            List<String> toolLines = java.util.Arrays.stream(WindowSpec.values())
                    .filter(spec -> spec != WindowSpec.SETTINGS)
                    .map(PresenceState::forWindow)
                    .map(PresenceState::details)
                    .toList();
            assertThat(toolLines).doesNotHaveDuplicates();
        }

        @Test
        @DisplayName("⚠ Settings deliberately reports the generic deck line, not one of its own")
        void settingsHasNoLineOfItsOwn() {
            // A decision, not an omission — see PresenceState.forWindow. Pinned because the obvious
            // "tidy-up" is to give it one for symmetry, and that would silently undo the reasoning:
            // Settings is the panel a player opens to turn this feature OFF, and a line of its own
            // makes "Changing the settings" the last thing their friends are told.
            assertThat(PresenceState.forWindow(WindowSpec.SETTINGS)).isEqualTo(PresenceState.DECK);
            assertThat(java.util.Arrays.stream(PresenceState.values()).map(PresenceState::details))
                    .noneMatch(line -> line.toLowerCase(java.util.Locale.ROOT).contains("setting"));
        }

        @Test
        @DisplayName("no state is blank, and no two share a line")
        void statesAreDistinct() {
            List<String> lines =
                    java.util.Arrays.stream(PresenceState.values()).map(PresenceState::details).toList();

            assertThat(lines).doesNotContain("", (String) null);
            // Two states rendering the same sentence is not a bug in what is transmitted, but it is
            // a bug in the Settings page's claim to list everything it can say: the list would name
            // one line twice and the player could not tell which tools it covered.
            assertThat(lines).doesNotHaveDuplicates();
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────────────────────────

    private static Transport recorder(List<String> frames) {
        return new Transport() {
            @Override
            public void send(String json) {
                frames.add(json);
            }

            @Override
            public void close() throws IOException {
                // Nothing to release.
            }
        };
    }

    /** Waits briefly for the worker's first update. ⚠ Polls rather than sleeping a fixed time. */
    private static String awaitFrame(List<String> frames) throws InterruptedException {
        for (int attempt = 0; attempt < 100 && frames.isEmpty(); attempt++) {
            Thread.sleep(20);
        }
        assertThat(frames).as("the worker sent nothing within two seconds").isNotEmpty();
        return frames.get(0);
    }
}

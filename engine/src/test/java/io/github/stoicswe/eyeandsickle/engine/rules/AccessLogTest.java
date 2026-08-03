package io.github.stoicswe.eyeandsickle.engine.rules;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.engine.state.ItemState;
import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The intrusion log, and the counter-forensics loop built on it.
 *
 * <h2>Why this is tested now, when nothing can write to it yet</h2>
 *
 * Every writer is a remote actor and single player has none, so a solo player's log is empty for the
 * life of the character. The <em>rules</em> are still real code with real edge cases — a redaction
 * that deleted rows instead of blanking a field would quietly destroy the mechanic, and there would
 * be no way to notice until multiplayer shipped. Testing it now is what makes <b>CL-8</b> landing a
 * transport change rather than an engine change.
 */
class AccessLogTest {

    private static final Instant NOW = Instant.parse("2026-07-28T12:00:00Z");

    private static GameSave withEntries() {
        GameSave save = new GameSave();
        AccessLog.record(
                save,
                "10.4.0.7",
                "copy",
                "/home/op/Applications/Network.app/Contents/Upgrades/net-sweep-wide.upg",
                NOW);
        AccessLog.record(save, "10.9.1.2", "read", "/home/op/Documents", NOW.plusSeconds(30));
        AccessLog.record(save, "10.4.0.7", "copy", "/home/op/.VaultStore/hot/relay-hop", NOW.plusSeconds(60));
        return save;
    }

    private static ItemState item(String tier) {
        ItemState item = new ItemState();
        item.tier = tier;
        item.displayName = "Net Sweep (Wide)";
        return item;
    }

    @Nested
    @DisplayName("⚠ the tier still decides what can be taken — §6 is not bypassed")
    class Exposure {

        @Test
        @DisplayName("a vault item is never takeable, however deep the intruder navigated")
        void vaultIsSafe() {
            // The whole reason the Applications folder is a VIEW onto items rather than a second
            // place they live. If this ever returns true, ~/Applications has become a fourth
            // exposure surface that routes around the vault — and the vault being genuinely safe is
            // what the entire risk economy is priced against (I12).
            assertThat(AccessLog.canTake(item("VAULT"), true)).isFalse();
            assertThat(AccessLog.canTake(item("VAULT"), false)).isFalse();
        }

        @Test
        @DisplayName("standard storage is exposed only while the owner is online")
        void standardFollowsPresence() {
            assertThat(AccessLog.canTake(item("STANDARD_STORAGE"), true)).isTrue();
            assertThat(AccessLog.canTake(item("STANDARD_STORAGE"), false)).isFalse();
        }

        @Test
        @DisplayName("the hot zone is always exposed, online or not")
        void hotZoneIsAlwaysOpen() {
            assertThat(AccessLog.canTake(item("HIGH_HACKABLE_ZONE"), true)).isTrue();
            assertThat(AccessLog.canTake(item("HIGH_HACKABLE_ZONE"), false)).isTrue();
        }

        @Test
        @DisplayName("an unknown tier fails closed rather than guessing")
        void unknownTierIsSafe() {
            // A profile is a plain JSON file a player can edit. Guessing "exposed" would turn a
            // corrupted field into a theft.
            assertThat(AccessLog.canTake(item("NONSENSE"), true)).isFalse();
            assertThat(AccessLog.canTake(null, true)).isFalse();
        }
    }

    @Nested
    @DisplayName("the log records")
    class Recording {

        @Test
        @DisplayName("entries are appended with a monotonic sequence")
        void appends() {
            GameSave save = withEntries();
            assertThat(save.remoteAccessLog).hasSize(3);
            assertThat(save.remoteAccessLog).extracting(e -> e.sequence).containsExactly(1L, 2L, 3L);
        }

        @Test
        @DisplayName("an untouched rig says so, rather than showing an empty table")
        void emptyReadsAsEmpty() {
            // docs/design/ui-design-language.md §6: an empty state is an instruction, not a blank.
            // This is the state every solo player is permanently in.
            assertThat(String.join("\n", AccessLog.render(new GameSave())))
                    .contains("No one has been on this machine but you");
        }

        @Test
        @DisplayName("attackers lists everyone who did not clean up after themselves")
        void attackers() {
            assertThat(AccessLog.attackers(withEntries())).containsExactly("10.4.0.7", "10.9.1.2");
        }
    }

    @Nested
    @DisplayName("⚠ redaction blanks the address; it never deletes the line")
    class Redaction {

        @Test
        @DisplayName("wiping an address leaves the row, the time, the action and the path")
        void wipeKeepsTheEvidence() {
            // The mechanic in one assertion. What the intruder takes away is the ability to hit
            // back; what they cannot take away is the victim knowing they were robbed, when, and of
            // what. Deleting rows outright is the obvious "improvement" and it is the wrong one —
            // it turns a legible crime into a missing file.
            GameSave save = withEntries();

            assertThat(AccessLog.redact(save, "10.4.0.7")).isEqualTo(2);

            assertThat(save.remoteAccessLog).hasSize(3);
            assertThat(save.remoteAccessLog.getFirst().path).isNotBlank();
            assertThat(save.remoteAccessLog.getFirst().action).isEqualTo("copy");
            assertThat(save.remoteAccessLog.getFirst().fromAddress).isEmpty();
        }

        @Test
        @DisplayName("the sequence numbers survive, so a partial wipe reads as a wipe")
        void sequenceSurvives() {
            GameSave save = withEntries();
            AccessLog.redact(save, "10.4.0.7");

            assertThat(save.remoteAccessLog).extracting(e -> e.sequence).containsExactly(1L, 2L, 3L);
            String rendered = String.join("\n", AccessLog.render(save));
            assertThat(rendered).contains(AccessLog.REDACTED);
            // And the log says, in words, that it was edited — because somebody who wipes an
            // address knew there was one to wipe, and that is itself evidence.
            assertThat(rendered).contains("cleaned up after itself");
        }

        @Test
        @DisplayName("one intruder's wipe does not touch another's line")
        void wipeIsTargeted() {
            GameSave save = withEntries();
            AccessLog.redact(save, "10.4.0.7");

            assertThat(AccessLog.attackers(save)).containsExactly("10.9.1.2");
            assertThat(AccessLog.gaps(save)).isEqualTo(2);
        }

        @Test
        @DisplayName("redacting nothing is a no-op, not an exception")
        void redactNothing() {
            GameSave save = withEntries();
            assertThat(AccessLog.redact(save, "10.0.0.0")).isZero();
            assertThat(AccessLog.redact(save, "")).isZero();
            assertThat(AccessLog.redact(save, null)).isZero();
            assertThat(AccessLog.gaps(save)).isZero();
        }
    }
}

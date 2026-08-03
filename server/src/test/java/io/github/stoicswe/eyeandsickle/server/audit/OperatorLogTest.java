package io.github.stoicswe.eyeandsickle.server.audit;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.lan.LanIdentity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The operator log's one security property: it must not become a list of credentials.
 *
 * <p>The log lines themselves are not asserted — that would pin prose. What is pinned is the rule
 * that decides how an actor is named, because getting it wrong writes bearer tokens into a file
 * operators tail, ship to aggregators, and paste into bug reports.
 */
class OperatorLogTest {

    private final OperatorLog operatorLog = new OperatorLog();

    @Nested
    @DisplayName("naming an actor")
    class Naming {

        @Test
        @DisplayName("⚠ a named LAN player logs as username@rig, never as their identity")
        void usernameAtRig() {
            // The username is a display name the player chose and gave us — the same kind of thing as
            // a federated handle. The UUID is the credential. So this is both safe and what the
            // operator actually wants: "who is this", not "is this the same one as last line".
            Did lan = LanIdentity.mint();
            operatorLog.name(lan, "ghost", "nightjar");

            assertThat(operatorLog.actor(lan)).isEqualTo("ghost@nightjar");
            assertThat(operatorLog.actor(lan)).doesNotContain(lan.value().substring("did:easlan:".length()));
        }

        @Test
        @DisplayName("before a rig is chosen it is just the username")
        void usernameAlone() {
            Did lan = LanIdentity.mint();
            operatorLog.name(lan, "ghost", null);

            assertThat(operatorLog.actor(lan)).isEqualTo("ghost");
        }

        @Test
        @DisplayName("⚠ a username cannot smuggle a space or an = into the key=value shape")
        void usernameCannotBreakTheFormat() {
            Did lan = LanIdentity.mint();
            operatorLog.name(lan, "gh ost=x", "rig");

            assertThat(operatorLog.actor(lan)).doesNotContain(" ").doesNotContain("=x");
        }

        @Test
        @DisplayName("⚠ an UNNAMED LAN identity is NEVER logged in full — it is a bearer token")
        void lanIdentityIsFingerprinted() {
            // did:easlan:<uuid> IS the credential (architecture/12 §2). Whoever holds it is that
            // player, so a log carrying it is a log carrying a password.
            Did lan = LanIdentity.mint();

            String label = operatorLog.actor(lan);

            assertThat(label).doesNotContain(lan.value());
            assertThat(label).startsWith("lan:");
            // The UUID's own characters must not survive either — a "redaction" that leaves most of
            // the secret is not one.
            String uuid = lan.value().substring("did:easlan:".length());
            assertThat(label).doesNotContain(uuid);
        }

        @Test
        @DisplayName("the fingerprint is STABLE, so one player's actions can be correlated")
        void fingerprintIsStable() {
            // Without this the log is unusable for the operator's actual question — "what did this
            // player do" — and redaction would have cost more than it bought.
            Did lan = LanIdentity.mint();

            assertThat(operatorLog.actor(lan)).isEqualTo(operatorLog.actor(lan));
        }

        @Test
        @DisplayName("different LAN players get different fingerprints")
        void fingerprintsDiffer() {
            assertThat(operatorLog.actor(LanIdentity.mint())).isNotEqualTo(operatorLog.actor(LanIdentity.mint()));
        }

        @Test
        @DisplayName("⚠ a federated DID IS logged in full — it is an identifier, not a secret")
        void federatedDidIsPlain() {
            // The opposite decision, for the opposite reason: possession of a DID is not possession of
            // the account, because the account is proven by a signature. Fingerprinting it would
            // destroy correlation with the allowlist, the ledger and the directory for no gain.
            Did did = Did.of("did:plc:abcdefghijklmnopqrstuvwx");

            assertThat(operatorLog.actor(did)).isEqualTo("did:plc:abcdefghijklmnopqrstuvwx");
        }

        @Test
        @DisplayName("a null actor does not produce 'null'")
        void nullActor() {
            assertThat(operatorLog.actor(null)).isEqualTo("anonymous");
        }
    }

    @Nested
    @DisplayName("player-supplied text")
    class Injection {

        @Test
        @DisplayName("⚠ a newline in a username cannot forge a log line")
        void logInjection() {
            // A username is player-supplied. Without this, a player names themselves with a newline
            // and writes their own entries — including a fabricated denial for somebody else.
            String hostile = "ghost\nevent=player.signin.denied actor=did:plc:victim";

            String safe = OperatorLog.safe(hostile);

            assertThat(safe).doesNotContain("\n").doesNotContain("\r");
        }

        @Test
        @DisplayName("a long username cannot push the rest of the line off the terminal")
        void truncated() {
            assertThat(OperatorLog.safe("x".repeat(500))).hasSizeLessThan(80);
        }

        @Test
        @DisplayName("a username with spaces is quoted so key=value still parses")
        void quoted() {
            assertThat(OperatorLog.safe("two words")).startsWith("\"").endsWith("\"");
        }

        @Test
        @DisplayName("null and blank render as a placeholder, not as 'null'")
        void nothing() {
            assertThat(OperatorLog.safe(null)).isEqualTo("-");
            assertThat(OperatorLog.safe("   ")).isEqualTo("-");
        }
    }
}

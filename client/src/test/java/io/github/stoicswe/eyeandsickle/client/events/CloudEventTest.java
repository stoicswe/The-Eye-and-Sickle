package io.github.stoicswe.eyeandsickle.client.events;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * Conformance to CloudEvents v1.0.2.
 *
 * <p>The value of adopting a specification is entirely in actually following it. An envelope that
 * conforms only when every publisher remembers the rules is not a conformant envelope — so the rules
 * are enforced in the constructor and asserted here, each against the section it comes from.
 */
class CloudEventTest {

    private static CloudEvent valid() {
        return CloudEvent.of(EventTypes.of("intent"), "/client/session", "purchase",
                Map.of("outcome", "ok"));
    }

    @Nested
    @DisplayName("§3.1.1 — the four required attributes")
    class Required {

        @Test
        @DisplayName("all four are present and non-empty on a built event")
        void allFourPresent() {
            CloudEvent event = valid();
            assertThat(event.id()).isNotBlank();
            assertThat(event.source()).isNotNull();
            assertThat(event.source().toString()).isNotBlank();
            assertThat(event.specversion()).isEqualTo("1.0");
            assertThat(event.type()).isNotBlank();
        }

        @Test
        @DisplayName("each one is refused when absent or blank")
        void eachIsRequired() {
            assertThatThrownBy(() -> new CloudEvent(" ", URI.create("/x"), "1.0", "t",
                    null, null, null, Instant.now(), Map.of(), Map.of()))
                    .hasMessageContaining("id");
            assertThatThrownBy(() -> new CloudEvent("1", null, "1.0", "t",
                    null, null, null, Instant.now(), Map.of(), Map.of()))
                    .hasMessageContaining("source");
            assertThatThrownBy(() -> new CloudEvent("1", URI.create("/x"), "1.0", "",
                    null, null, null, Instant.now(), Map.of(), Map.of()))
                    .hasMessageContaining("type");
        }

        @Test
        @DisplayName("specversion is pinned to 1.0")
        void specVersionIsPinned() {
            assertThatThrownBy(() -> new CloudEvent("1", URI.create("/x"), "0.3", "t",
                    null, null, null, Instant.now(), Map.of(), Map.of()))
                    .hasMessageContaining("specversion");
        }

        /**
         * ⚠ The spec's own uniqueness rule: producers MUST ensure {@code source + id} is unique for
         * each distinct event. It is what makes a duplicate recognisable as a redelivery rather than
         * as a second thing happening — and a hand-written id is the first thing to break it, which
         * is why {@link CloudEvent#of} generates one.
         */
        @Test
        @DisplayName("the id is a UUID, and source+id is unique across many events")
        void idsAreUniqueUuids() {
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < 5_000; i++) {
                CloudEvent event = valid();
                // Parses as a UUID or throws — the assertion is the round trip.
                assertThat(UUID.fromString(event.id()).toString()).isEqualTo(event.id());
                assertThat(seen.add(event.source() + "#" + event.id()))
                        .as("source+id must be unique per distinct event")
                        .isTrue();
            }
        }
    }

    @Nested
    @DisplayName("§3.1.2 — optional attributes are absent or non-empty, never blank")
    class Optional {

        @Test
        @DisplayName("a blank subject is refused, an absent one is fine")
        void subjectIsAbsentOrReal() {
            // Absent and empty are different statements. A blank subject claims there is one and
            // names nothing, which is worse than saying nothing.
            assertThatThrownBy(() -> new CloudEvent("1", URI.create("/x"), "1.0", "t",
                    null, null, "  ", Instant.now(), Map.of(), Map.of()))
                    .hasMessageContaining("subject");
            assertThat(CloudEvent.of("t", "/x", null).subject()).isNull();
        }

        @Test
        @DisplayName("datacontenttype is set only when there is data to describe")
        void contentTypeFollowsData() {
            assertThat(CloudEvent.of("t", "/x", "s").datacontenttype()).isNull();
            assertThat(CloudEvent.of("t", "/x", "s", Map.of("a", "b")).datacontenttype())
                    .isEqualTo("application/json");
        }

        @Test
        @DisplayName("time is present and recent")
        void timeIsPresent() {
            assertThat(valid().time()).isBetween(Instant.now().minusSeconds(5), Instant.now().plusSeconds(5));
        }
    }

    @Nested
    @DisplayName("§4.1 — extension attribute naming")
    class Extensions {

        @Test
        @DisplayName("lowercase alphanumeric only")
        void namesAreLowercaseAlphanumeric() {
            assertThat(valid().with("retry", "1").extensions()).containsEntry("retry", "1");
            for (String bad : new String[] {"Retry", "re-try", "re_try", "re.try", "ретри", ""}) {
                assertThatThrownBy(() -> valid().with(bad, "1"))
                        .as("%s", bad)
                        .hasMessageContaining("§4.1");
            }
        }

        @Test
        @DisplayName("no longer than twenty characters")
        void namesAreTerse() {
            assertThat(valid().with("a".repeat(20), "1").extensions()).hasSize(1);
            assertThatThrownBy(() -> valid().with("a".repeat(21), "1"))
                    .hasMessageContaining("20");
        }

        /**
         * ⚠ An extension named {@code type} would be silently unreachable on any transport that maps
         * attributes into one flat namespace, which is most of them — the reader would see the core
         * attribute while the producer believed they had sent theirs.
         */
        @Test
        @DisplayName("an extension may not shadow a core attribute")
        void noShadowing() {
            for (String core : new String[] {"id", "source", "type", "specversion", "subject", "time", "data"}) {
                assertThatThrownBy(() -> valid().with(core, "x"))
                        .as("%s", core)
                        .hasMessageContaining("shadow");
            }
        }
    }

    @Nested
    @DisplayName("§3.1.1 — type naming")
    class Types {

        @Test
        @DisplayName("every type this client publishes is reverse-DNS prefixed")
        void typesAreNamespaced() {
            // The spec SHOULDs this; a client that owns a domain has no excuse not to. It is also
            // what makes prefix subscription work at all.
            assertThat(EventTypes.of(EventTypes.INTENT)).startsWith(CloudEvent.NAMESPACE + ".");
            assertThat(valid().shortType()).isEqualTo("intent");
        }

        @Test
        @DisplayName("prefix matching catches a whole branch, and does not catch a sibling")
        void prefixMatching() {
            CloudEvent scan = CloudEvent.of(EventTypes.of("portscan.started"), "/x", "10.0.0.2");
            assertThat(scan.isA(EventTypes.of("portscan"))).isTrue();
            assertThat(scan.isA(EventTypes.of("portscan.started"))).isTrue();
            // ⚠ Not a prefix match on the raw string: "portscan" must not catch "portscanner".
            assertThat(CloudEvent.of(EventTypes.of("portscanner"), "/x", "s")
                    .isA(EventTypes.of("portscan"))).isFalse();
        }
    }
}

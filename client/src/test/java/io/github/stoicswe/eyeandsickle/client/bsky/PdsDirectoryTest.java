package io.github.stoicswe.eyeandsickle.client.bsky;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * ⚠ <b>THE REGRESSION: every direct-message call came back 501 MethodNotImplemented.</b>
 *
 * <h2>What happened</h2>
 *
 * The client hard-coded {@code https://bsky.social} and sent both the sign-in and every later chat
 * call there. Sign-in <b>succeeded</b>, which is what made this so hard to read — the tab said it was
 * connected and then refused every conversation.
 *
 * <p>{@code bsky.social} is the <b>entryway</b>, not a PDS. It fronts session methods for all
 * Bluesky-hosted accounts and does not forward {@code chat.bsky.*}, so it answered the only thing it
 * can for a method it has never heard of. The account's real host was in its DID document all along:
 *
 * <pre>{@code
 * stoicswe.com → did:plc:zczf6tbnu4prqmdtj2hemgqu → https://leccinum.us-west.host.bsky.network
 * }</pre>
 *
 * <h2>⚠ Why these tests are pure, and why the fixtures are real</h2>
 *
 * Nothing here touches a network — a test that signed in to a real account would need somebody's
 * credential and would fail on an aeroplane. The documents below are the <b>actual</b> responses,
 * captured from {@code plc.directory} while diagnosing this, so the parsing is checked against what
 * the service really returns rather than against what this file imagines it returns.
 */
class PdsDirectoryTest {

    private static final ObjectMapper JSON = new ObjectMapper();

    private static JsonNode json(String text) {
        return JSON.readTree(text);
    }

    /** The real document for the account the bug was found on, trimmed to the parts that matter. */
    private static final String REAL_DOCUMENT = """
            {
              "id": "did:plc:zczf6tbnu4prqmdtj2hemgqu",
              "alsoKnownAs": ["at://stoicswe.com"],
              "service": [
                {
                  "id": "#atproto_pds",
                  "type": "AtprotoPersonalDataServer",
                  "serviceEndpoint": "https://leccinum.us-west.host.bsky.network"
                }
              ]
            }
            """;

    @Nested
    @DisplayName("reading the host out of a DID document")
    class Documents {

        /** ⚠ The one that matters: the answer must be the host, never the entryway. */
        @Test
        @DisplayName("it finds the account's real PDS")
        void itFindsThePds() {
            assertThat(PdsDirectory.pdsFromDidDocument(json(REAL_DOCUMENT)))
                    .contains("https://leccinum.us-west.host.bsky.network");
        }

        /**
         * ⚠ <b>The trap that would have shipped a second, quieter version of the same bug.</b>
         *
         * <p>{@code service} is a list of <em>different</em> services. Taking {@code service[0]} works
         * for a plain account and points the client at a labeler for anybody running one — and it
         * would fail exactly the same way, with the same unreadable 501.
         */
        @Test
        @DisplayName("it picks the PDS entry, not the first service in the list")
        void itIsNotTheFirstEntry() {
            JsonNode document = json("""
                    {
                      "service": [
                        {
                          "id": "#atproto_labeler",
                          "type": "AtprotoLabeler",
                          "serviceEndpoint": "https://labeler.example"
                        },
                        {
                          "id": "#atproto_pds",
                          "type": "AtprotoPersonalDataServer",
                          "serviceEndpoint": "https://pds.example"
                        }
                      ]
                    }
                    """);
            assertThat(PdsDirectory.pdsFromDidDocument(document)).contains("https://pds.example");
        }

        /**
         * ⚠ This endpoint is where an app password is POSTed, so plain HTTP is refused outright.
         *
         * <p>Falling back to the entryway is the safe failure; sending the credential in clear
         * because a document asked nicely is not.
         */
        @Test
        @DisplayName("it refuses a non-HTTPS endpoint")
        void httpsOnly() {
            JsonNode document = json("""
                    {"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer",
                     "serviceEndpoint":"http://pds.example"}]}
                    """);
            assertThat(PdsDirectory.pdsFromDidDocument(document)).isEmpty();
        }

        /** ⚠ {@code https://real@evil/} reads as one host to a person and resolves to another. */
        @Test
        @DisplayName("it refuses an endpoint carrying userinfo")
        void noUserInfo() {
            assertThat(PdsDirectory.usableEndpoint("https://pds.example@evil.example"))
                    .isFalse();
            assertThat(PdsDirectory.usableEndpoint("https://pds.example")).isTrue();
        }

        /** ⚠ Every caller appends {@code /xrpc/…}, so a documented trailing slash must not survive. */
        @Test
        @DisplayName("it drops a trailing slash")
        void noTrailingSlash() {
            JsonNode document = json("""
                    {"service":[{"id":"#atproto_pds","type":"AtprotoPersonalDataServer",
                     "serviceEndpoint":"https://pds.example/"}]}
                    """);
            assertThat(PdsDirectory.pdsFromDidDocument(document)).contains("https://pds.example");
        }

        /** ⚠ Nothing usable is empty, not a blank host — the caller falls back to a working default. */
        @Test
        @DisplayName("a document with no PDS yields nothing rather than a blank host")
        void emptyRatherThanBlank() {
            assertThat(PdsDirectory.pdsFromDidDocument(json("{\"service\":[]}")))
                    .isEmpty();
            assertThat(PdsDirectory.pdsFromDidDocument(json("{}"))).isEmpty();
            assertThat(PdsDirectory.pdsFromDidDocument(null)).isEmpty();
        }
    }

    @Nested
    @DisplayName("where a DID document is read from")
    class DocumentUrls {

        @Test
        @DisplayName("did:plc goes to the PLC directory")
        void plc() {
            assertThat(PdsDirectory.didDocumentUrl("did:plc:zczf6tbnu4prqmdtj2hemgqu"))
                    .contains("https://plc.directory/did:plc:zczf6tbnu4prqmdtj2hemgqu");
        }

        /** ⚠ A bare did:web is a well-known lookup; further segments become path elements. */
        @Test
        @DisplayName("did:web resolves per its own method")
        void web() {
            assertThat(PdsDirectory.didDocumentUrl("did:web:example.com"))
                    .contains("https://example.com/.well-known/did.json");
            assertThat(PdsDirectory.didDocumentUrl("did:web:example.com:user:alice"))
                    .contains("https://example.com/user/alice/did.json");
        }

        /** ⚠ A port arrives percent-encoded, and leaving it encoded yields an unreachable host. */
        @Test
        @DisplayName("did:web decodes a percent-encoded port")
        void webPort() {
            assertThat(PdsDirectory.didDocumentUrl("did:web:localhost%3A3000"))
                    .contains("https://localhost:3000/.well-known/did.json");
        }

        /** ⚠ Refused, never guessed at — a guess here decides where a password is sent. */
        @Test
        @DisplayName("an unknown DID method is refused")
        void unknown() {
            assertThat(PdsDirectory.didDocumentUrl("did:example:123")).isEmpty();
            assertThat(PdsDirectory.didDocumentUrl("not-a-did")).isEmpty();
            assertThat(PdsDirectory.didDocumentUrl("did:plc:")).isEmpty();
            assertThat(PdsDirectory.didDocumentUrl(null)).isEmpty();
        }
    }
}

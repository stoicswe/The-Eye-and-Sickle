package io.github.stoicswe.eyeandsickle.server.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * Unit tests for {@link RestClientPeerTransport}, driven through {@link MockRestServiceServer} so they
 * run in the Docker-free {@code mvn verify} with no real network.
 *
 * <p>The far end is an untrusted server, so the behaviour worth pinning is the bounds and the swallowing
 * of failure. A peer that returns more descriptors than the per-exchange cap, a body larger than the
 * budget, or a malformed payload must not overflow this server's work or propagate an exception into a
 * gossip round — an unreachable or abusive peer is a normal, expected condition handled by returning
 * "nothing", not by throwing.
 */
class RestClientPeerTransportTest {

    private static final String ENDPOINT = "https://peer.example.test";
    private static final String DESCRIPTOR_URL = ENDPOINT + "/federation/descriptor";

    private RestClient client;
    private MockRestServiceServer server;

    private RestClientPeerTransport transport(int maxDescriptorBytes, int maxPeersPerExchange) {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = builder.build();
        DiscoveryProperties properties = new DiscoveryProperties(
                null, null, maxPeersPerExchange, null, maxDescriptorBytes, null, null, null, null, null);
        return new RestClientPeerTransport(client, properties);
    }

    @Nested
    @DisplayName("fetchSelfDescriptor")
    class FetchSelfDescriptor {

        @Test
        @DisplayName("returns the peer's descriptor body on success")
        void returnsBody() {
            RestClientPeerTransport transport = transport(1 << 20, 64);
            String body = "{\"descriptor\":{\"peerDid\":\"did:plc:x\"}}";
            server.expect(requestTo(DESCRIPTOR_URL)).andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            assertThat(transport.fetchSelfDescriptor(ENDPOINT)).contains(body);
        }

        @Test
        @DisplayName("an over-budget body is discarded — a descriptor has an imposed length or none")
        void overBudgetBodyDiscarded() {
            RestClientPeerTransport transport = transport(10, 64); // 10-byte descriptor cap
            String tooBig = "{\"pad\":\"" + "a".repeat(100) + "\"}";
            server.expect(requestTo(DESCRIPTOR_URL)).andRespond(withSuccess(tooBig, MediaType.APPLICATION_JSON));

            assertThat(transport.fetchSelfDescriptor(ENDPOINT)).isEmpty();
        }

        @Test
        @DisplayName("a non-2xx response becomes empty, never an exception")
        void errorBecomesEmpty() {
            RestClientPeerTransport transport = transport(1 << 20, 64);
            server.expect(requestTo(DESCRIPTOR_URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

            assertThat(transport.fetchSelfDescriptor(ENDPOINT)).isEmpty();
        }
    }

    @Nested
    @DisplayName("fetchDirectory")
    class FetchDirectory {

        @Test
        @DisplayName("returns at most maxPeersPerExchange descriptors, whatever the peer offers or the caller asks")
        void boundsTheDescriptorCount() {
            RestClientPeerTransport transport = transport(1 << 20, 2); // per-exchange cap of 2
            List<Map<String, Object>> descriptors = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                descriptors.add(Map.of("descriptor", Map.of("peerDid", "did:plc:peer" + i)));
            }
            String body = Jsonb.writeObject(Map.of(RestClientPeerTransport.DESCRIPTORS_FIELD, descriptors));
            // The caller asks for 100; the outgoing request must already be clamped to the cap of 2.
            server.expect(requestTo(ENDPOINT + "/federation/peers?limit=2"))
                    .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));

            List<String> result = transport.fetchDirectory(ENDPOINT, 100);

            assertThat(result).hasSize(2);
            assertThat(result).allSatisfy(descriptor -> assertThat(descriptor).startsWith("{"));
        }

        @Test
        @DisplayName("an abusive over-budget directory body is refused whole")
        void overBudgetBodyRefused() {
            RestClientPeerTransport transport = transport(20, 2); // budget = 20 * (2+1) = 60 bytes
            Map<String, Object> body = new LinkedHashMap<>();
            body.put(RestClientPeerTransport.DESCRIPTORS_FIELD, List.of(Map.of("pad", "a".repeat(200))));
            server.expect(requestTo(ENDPOINT + "/federation/peers?limit=2"))
                    .andRespond(withSuccess(Jsonb.writeObject(body), MediaType.APPLICATION_JSON));

            assertThat(transport.fetchDirectory(ENDPOINT, 2)).isEmpty();
        }

        @Test
        @DisplayName("a malformed body is empty, not thrown")
        void malformedBodyIsEmpty() {
            RestClientPeerTransport transport = transport(1 << 20, 2);
            server.expect(requestTo(ENDPOINT + "/federation/peers?limit=2"))
                    .andRespond(withSuccess("this is not json", MediaType.APPLICATION_JSON));

            assertThat(transport.fetchDirectory(ENDPOINT, 2)).isEmpty();
        }

        @Test
        @DisplayName("a body with no descriptors array is empty")
        void missingArrayIsEmpty() {
            RestClientPeerTransport transport = transport(1 << 20, 2);
            server.expect(requestTo(ENDPOINT + "/federation/peers?limit=2"))
                    .andRespond(withSuccess("{\"other\":123}", MediaType.APPLICATION_JSON));

            assertThat(transport.fetchDirectory(ENDPOINT, 2)).isEmpty();
        }

        @Test
        @DisplayName("a failed request becomes an empty list, never an exception into the round")
        void failureIsEmpty() {
            RestClientPeerTransport transport = transport(1 << 20, 2);
            server.expect(requestTo(ENDPOINT + "/federation/peers?limit=2"))
                    .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

            assertThat(transport.fetchDirectory(ENDPOINT, 2)).isEmpty();
        }
    }

    @Nested
    @DisplayName("probe")
    class Probe {

        @Test
        @DisplayName("a reachable peer answers true")
        void reachableIsTrue() {
            RestClientPeerTransport transport = transport(1 << 20, 64);
            server.expect(requestTo(DESCRIPTOR_URL)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

            assertThat(transport.probe(ENDPOINT)).isTrue();
        }

        @Test
        @DisplayName("an unreachable peer answers false, not an exception")
        void unreachableIsFalse() {
            RestClientPeerTransport transport = transport(1 << 20, 64);
            server.expect(requestTo(DESCRIPTOR_URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

            assertThat(transport.probe(ENDPOINT)).isFalse();
        }
    }
}

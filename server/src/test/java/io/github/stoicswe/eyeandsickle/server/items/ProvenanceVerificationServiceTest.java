package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.provenance.ChainFault.Reason;
import io.github.stoicswe.eyeandsickle.protocol.provenance.DuelCommitteeLookup;
import io.github.stoicswe.eyeandsickle.protocol.provenance.ProvenanceEnvelope;
import io.github.stoicswe.eyeandsickle.server.items.ProvenanceVerificationService.Result;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * {@link ProvenanceVerificationService} — the thin edge that assembles a {@link
 * io.github.stoicswe.eyeandsickle.protocol.provenance.ChainVerificationContext} from this server's key
 * resolution, issuer authority and clock, and runs the pure protocol verifier.
 *
 * <p>The verifier's own algorithm is exhaustively tested in the protocol module; these tests pin the
 * <em>wiring</em>: that the service judges timestamps against its injected clock and the properties'
 * skew, and that the {@link Result} it returns records the basis it was reached on rather than a bare
 * boolean that has quietly expired.
 */
class ProvenanceVerificationServiceTest {

    private final TestChains chains = new TestChains();

    private ProvenanceVerificationService verificationAt(Instant now, Duration skew) {
        return new ProvenanceVerificationService(
                Clock.fixed(now, ZoneOffset.UTC),
                new ItemsProperties(skew, null, null, null),
                chains.directory(),
                new ServerIssuerAuthority(ServerRecognition.of(Set.of(TestChains.HOME_DID))),
                DuelCommitteeLookup.none());
    }

    // ------------------------------------------------------------------ verdicts

    @Nested
    @DisplayName("the verdict")
    class Verdicts {

        @Test
        @DisplayName("a sound chain is recognized with no faults")
        void soundChainRecognized() {
            Result result =
                    verificationAt(TestChains.NOW, TestChains.MAX_FUTURE_SKEW).verify(chains.validChain(3));

            assertThat(result.recognized()).isTrue();
            assertThat(result.verdict().faults()).isEmpty();
        }

        @Test
        @DisplayName("an empty chain is not recognized")
        void emptyChainNotRecognized() {
            Result result =
                    verificationAt(TestChains.NOW, TestChains.MAX_FUTURE_SKEW).verify(List.of());

            assertThat(result.recognized()).isFalse();
            assertThat(result.verdict().hasFault(Reason.EMPTY_CHAIN)).isTrue();
        }
    }

    // ------------------------------------------------------------------ the clock and skew are the service's

    @Nested
    @DisplayName("timestamps are judged against the service's clock and skew")
    class ClockAndSkew {

        @Test
        @DisplayName("a record dated past the clock plus skew is future-dated and refused")
        void futureDatedBeyondSkew() {
            // The fixture's genesis is dated 2026-07-01T01:00:00Z; a clock a fortnight earlier with zero
            // skew makes it implausibly future-dated.
            Result result = verificationAt(Instant.parse("2026-06-15T00:00:00Z"), Duration.ZERO)
                    .verify(chains.validChain(1));

            assertThat(result.verdict().hasFault(Reason.TIMESTAMP_IN_FUTURE)).isTrue();
            assertThat(result.recognized()).isFalse();
        }

        @Test
        @DisplayName("a generous skew tolerates the same record")
        void generousSkewTolerates() {
            Result result = verificationAt(Instant.parse("2026-06-15T00:00:00Z"), Duration.ofDays(365))
                    .verify(chains.validChain(1));

            // Same record, same clock — only the tolerated horizon changed, which is exactly the knob
            // ItemsProperties owns.
            assertThat(result.recognized()).isTrue();
        }
    }

    // ------------------------------------------------------------------ the result carries its basis

    @Test
    @DisplayName("the result records the instant and skew it was reached against")
    void resultCarriesItsBasis() {
        Instant now = Instant.parse("2026-08-01T09:30:00Z");
        Duration skew = Duration.ofSeconds(90);

        Result result = verificationAt(now, skew).verify(chains.validChain(1));

        assertThat(result.verifiedAt()).isEqualTo(now);
        assertThat(result.maxFutureSkew()).isEqualTo(skew);
    }

    @Test
    @DisplayName("a tampered chain is not recognized")
    void tamperedChainNotRecognized() {
        List<ProvenanceEnvelope> chain = chains.validChain(2);
        // Point the second record at the wrong predecessor, re-signed so only the link is wrong.
        ProvenanceEnvelope broken =
                chains.singleIssuer(TestChains.withPrevRecordHash(chain.get(1).payload(), "sha256-" + "f".repeat(64)));

        Result result =
                verificationAt(TestChains.NOW, TestChains.MAX_FUTURE_SKEW).verify(List.of(chain.get(0), broken));

        assertThat(result.recognized()).isFalse();
        assertThat(result.verdict().hasFault(Reason.BROKEN_HASH_LINK)).isTrue();
    }
}

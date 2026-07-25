package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Bean-validation tests for {@link AllocateComputeRequest} — the input filter at the REST edge. The
 * annotations reject the two malformed shapes (absent consumer, non-positive amount) with a 400 before
 * any rig is touched; they are a filter, not an authority (whether the rig can spare the cycles is the
 * server's call). Driven through a real {@link Validator} so the annotations are actually exercised,
 * and container-free.
 */
class AllocateComputeRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    @Test
    @DisplayName("a well-formed request passes validation")
    void wellFormedRequestPasses() {
        AllocateComputeRequest request = new AllocateComputeRequest(ComputeConsumer.SELF_MINING, null, 40L);
        assertThat(validator.validate(request)).isEmpty();
    }

    @Test
    @DisplayName("a null consumer is rejected")
    void nullConsumerRejected() {
        AllocateComputeRequest request = new AllocateComputeRequest(null, null, 40L);
        assertThat(validator.validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("consumer"));
    }

    @Test
    @DisplayName("a zero-cycle request is rejected — a reservation is for a positive amount")
    void zeroCyclesRejected() {
        AllocateComputeRequest request = new AllocateComputeRequest(ComputeConsumer.SELF_MINING, null, 0L);
        assertThat(validator.validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("cycles"));
    }

    @Test
    @DisplayName("a negative-cycle request is rejected")
    void negativeCyclesRejected() {
        AllocateComputeRequest request = new AllocateComputeRequest(ComputeConsumer.SELF_MINING, null, -5L);
        assertThat(validator.validate(request))
                .anySatisfy(v -> assertThat(v.getPropertyPath().toString()).isEqualTo("cycles"));
    }

    @Test
    @DisplayName("a null consumerRef is allowed — self-mining is the rig itself, not a distinct entity")
    void nullConsumerRefIsAllowed() {
        AllocateComputeRequest selfMining = new AllocateComputeRequest(ComputeConsumer.SELF_MINING, null, 10L);
        AllocateComputeRequest withRef = new AllocateComputeRequest(ComputeConsumer.BOT_FRAME, UUID.randomUUID(), 10L);

        assertThat(validator.validate(selfMining)).isEmpty();
        assertThat(validator.validate(withRef)).isEmpty();
    }
}

package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pure-logic tests for {@link ConsumptionModel} — the reservation/per-use split from {@code
 * docs/design/01-core-resources.md} §1.1 that decides which lifecycle operation is legal for an
 * allocation.
 *
 * <p>Getting this wrong is not cosmetic: a bot frame that could "recover" would hand its reserved
 * cycles back while the bot still runs, and a scan that could be "released" would skip the Thermal
 * Budget penalty. So the mapping is pinned exactly, and every consumer is asserted to have one.
 */
class ConsumptionModelTest {

    @Test
    @DisplayName("the permanent reservations are the five while-running consumers")
    void reservations() {
        assertThat(ConsumptionModel.of(ComputeConsumer.SELF_MINING)).isEqualTo(ConsumptionModel.RESERVATION);
        assertThat(ConsumptionModel.of(ComputeConsumer.BOT_FRAME)).isEqualTo(ConsumptionModel.RESERVATION);
        assertThat(ConsumptionModel.of(ComputeConsumer.CONTROL_CHANNEL)).isEqualTo(ConsumptionModel.RESERVATION);
        assertThat(ConsumptionModel.of(ComputeConsumer.DEFENSIVE_ARRAY)).isEqualTo(ConsumptionModel.RESERVATION);
        // The host-side draw of a foreign miner is a reservation while the miner runs, charged to the host.
        assertThat(ConsumptionModel.of(ComputeConsumer.DEPLOYED_MINER)).isEqualTo(ConsumptionModel.RESERVATION);
    }

    @Test
    @DisplayName("the per-use charges are the discrete-action consumers that recover")
    void perUse() {
        assertThat(ConsumptionModel.of(ComputeConsumer.ACTIVE_TOOL)).isEqualTo(ConsumptionModel.PER_USE);
        assertThat(ConsumptionModel.of(ComputeConsumer.RELAY_HOP)).isEqualTo(ConsumptionModel.PER_USE);
    }

    @ParameterizedTest
    @EnumSource(ComputeConsumer.class)
    @DisplayName("every consumer maps to a model — the switch is exhaustive by construction")
    void everyConsumerHasAModel(ComputeConsumer consumer) {
        // A new consumer added to the protocol enum must force a decision here (compile error), never a
        // silent default. This asserts the runtime half: no consumer returns null.
        assertThat(ConsumptionModel.of(consumer)).isNotNull();
    }
}

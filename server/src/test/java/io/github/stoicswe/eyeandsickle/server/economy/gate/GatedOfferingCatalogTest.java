package io.github.stoicswe.eyeandsickle.server.economy.gate;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.EthecoinCost;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The catalogue port, and its safe empty default.
 *
 * <p>Empty-by-default is the honest state until the item slices land: the unlock endpoint returns
 * "nothing to offer yet" rather than inventing offerings with invented prices. The {@link
 * GatedOfferingCatalog#find(String)} default is a small convenience over {@link
 * GatedOfferingCatalog#all()} and is tested against a hand-built catalogue.
 */
class GatedOfferingCatalogTest {

    @Test
    @DisplayName("the empty catalogue offers nothing and finds nothing")
    void emptyCatalogueIsEmpty() {
        GatedOfferingCatalog catalogue = GatedOfferingCatalog.empty();

        assertThat(catalogue.all()).isEmpty();
        assertThat(catalogue.find("anything")).isEmpty();
    }

    @Test
    @DisplayName("find resolves a present id and misses an absent one")
    void findByIdentifier() {
        GatedOffering fuzzer = GatedOffering.single("fuzzer", new EthecoinCost(Ethecoin.ofWholeEthecoin(25)));
        GatedOffering mapper = GatedOffering.single("mapper", new EthecoinCost(Ethecoin.ofWholeEthecoin(40)));
        GatedOfferingCatalog catalogue = () -> List.of(fuzzer, mapper);

        assertThat(catalogue.find("fuzzer")).contains(fuzzer);
        assertThat(catalogue.find("mapper")).contains(mapper);
        assertThat(catalogue.find("missing")).isEmpty();
    }
}

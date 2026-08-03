package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.server.persistence.DatabaseIntegrationTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link ServerStateRepository} against a real PostgreSQL. The row is a seeded singleton, so a read
 * always succeeds and never has to cope with an absent row; a fresh server has zero server-heat and no
 * DID yet (it is provisioned before the first mint, not at install).
 */
class ServerStateRepositoryIT extends DatabaseIntegrationTestBase {

    @Test
    @DisplayName("the seeded singleton reads back with zero heat and no server DID")
    void readsTheSeededSingleton() {
        ServerState state = new ServerStateRepository(jdbcClient()).read();

        assertThat(state.serverDid())
                .as("unknown until the server is provisioned")
                .isNull();
        assertThat(state.serverHeat().value()).isEqualByComparingTo("0");
        assertThat(state.heatUpdatedAt()).isNotNull();
        assertThat(state.rowVersion()).isZero();
    }
}

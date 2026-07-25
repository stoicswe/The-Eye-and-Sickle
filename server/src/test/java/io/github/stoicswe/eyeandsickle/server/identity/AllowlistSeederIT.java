package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link AllowlistSeeder} against a real PostgreSQL. The seeder is a one-way copy of the configured seed
 * DIDs into the durable table: idempotent on re-run, closed when there is nothing to seed, and — the
 * point of having a runtime table — it never un-revokes a DID an operator has since revoked.
 */
class AllowlistSeederIT extends PostgresIntegrationTestBase {

    private static final String DID_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String DID_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-24T10:00:00Z"), ZoneOffset.UTC);

    private AllowlistRepository repository;

    @BeforeEach
    void setUp() {
        repository = new AllowlistRepository(jdbcClient());
    }

    private AllowlistSeeder seeder(List<String> dids) {
        return new AllowlistSeeder(repository, new AllowlistProperties(true, dids), CLOCK);
    }

    @Test
    @DisplayName("seeding copies configured DIDs into the table and is idempotent on re-run")
    void seedsThenNoOps() {
        AllowlistSeeder seeder = seeder(List.of(DID_A, DID_B));

        assertThat(seeder.seed()).as("two newly inserted").isEqualTo(2);
        assertThat(repository.isAllowed(Did.of(DID_A))).isTrue();
        assertThat(repository.isAllowed(Did.of(DID_B))).isTrue();

        // A restart re-runs the seeder; already-present DIDs are left as-is.
        assertThat(seeder.seed()).as("nothing new on the second run").isZero();
    }

    @Test
    @DisplayName("a comma-joined env value seeds one entry per identity")
    void seedsCommaJoinedValue() {
        assertThat(seeder(List.of(DID_A + "," + DID_B)).seed()).isEqualTo(2);
        assertThat(repository.findAll()).hasSize(2);
    }

    @Test
    @DisplayName("re-seeding does NOT re-admit a DID the operator has revoked in the table")
    void reSeedDoesNotUnRevoke() {
        // The runtime table always wins over the config seed — the whole reason it exists. A DID left in
        // the config after being revoked must stay revoked.
        AllowlistSeeder seeder = seeder(List.of(DID_A));
        seeder.seed();
        repository.revoke(Did.of(DID_A), Did.of(DID_B), CLOCK.instant());

        assertThat(seeder.seed()).isZero();
        assertThat(repository.isAllowed(Did.of(DID_A))).isFalse();
    }

    @Test
    @DisplayName("no configured DIDs means nobody is seeded — the server stays closed")
    void emptyConfigSeedsNothing() {
        assertThat(seeder(List.of()).seed()).isZero();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("a malformed seed stops startup and inserts nothing")
    void malformedSeedFailsLoud() {
        // Fail loud on a typo in the one list that decides who may play; the failure propagates out of
        // seed() and no partial state is written.
        AllowlistSeeder seeder = seeder(List.of(DID_A, "not-a-did"));

        assertThatThrownBy(seeder::seed).isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.findAll())
                .as("parsedDids validates the whole list before any insert runs")
                .isEmpty();
    }
}

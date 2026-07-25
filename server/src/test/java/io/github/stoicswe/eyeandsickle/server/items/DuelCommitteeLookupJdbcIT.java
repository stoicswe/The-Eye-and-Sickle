package io.github.stoicswe.eyeandsickle.server.items;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import io.github.stoicswe.eyeandsickle.protocol.provenance.QuorumCommittee;
import io.github.stoicswe.eyeandsickle.server.persistence.Jsonb;
import io.github.stoicswe.eyeandsickle.server.persistence.PostgresIntegrationTestBase;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * {@link DuelCommitteeLookupJdbc} against a real PostgreSQL — supplying a duel's frozen sampling record
 * from the {@code duels} table so a {@code duel_grant} can be checked ({@code
 * docs/architecture/04-item-provenance.md} §7 step 1). Without the persisted committee, a real quorum
 * and a handful of freshly generated keys are indistinguishable, so an unknown duel is a rejection —
 * exactly as unrecognizable as a forged one.
 */
class DuelCommitteeLookupJdbcIT extends PostgresIntegrationTestBase {

    private static final String V1 = "did:plc:validator1";
    private static final String V2 = "did:plc:validator2";
    private static final String PARTICIPANT_A = "did:plc:aaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String PARTICIPANT_B = "did:plc:bbbbbbbbbbbbbbbbbbbbbbbb";

    private DuelCommitteeLookupJdbc lookup;

    @BeforeEach
    void setUp() {
        lookup = new DuelCommitteeLookupJdbc(jdbcClient());
    }

    private static Map<String, Object> weighted(String did, double weight) {
        return Map.of("did", did, "weight", weight);
    }

    private static Map<String, Object> repAndUptime(String did, double reputation, double uptime) {
        return Map.of("did", did, "reputation", reputation, "uptime", uptime);
    }

    private UUID insertDuel(List<Map<String, Object>> sampledValidators) {
        UUID id = UUID.randomUUID();
        jdbcClient()
                .sql("""
                        INSERT INTO duels (duel_id, participants, sampled_validators, committee_size)
                        VALUES (:id, :participants::jsonb, :sample::jsonb, :size)
                        """)
                .param("id", id)
                .param("participants", Jsonb.writeArray(List.of(PARTICIPANT_A, PARTICIPANT_B)))
                .param("sample", Jsonb.writeArray(sampledValidators))
                .param("size", sampledValidators.size())
                .update();
        return id;
    }

    // ------------------------------------------------------------------ resolving a committee

    @Test
    @DisplayName("resolves a stored committee with its frozen weights")
    void resolvesStoredCommittee() {
        UUID id = insertDuel(List.of(weighted(V1, 1.0), weighted(V2, 2.0)));

        QuorumCommittee committee = lookup.committeeFor(id.toString());

        assertThat(committee).isNotNull();
        assertThat(committee.duelId()).isEqualTo(id.toString());
        assertThat(committee.size()).isEqualTo(2);
        assertThat(committee.wasSampled(V1)).isTrue();
        assertThat(committee.weightOf(V1)).isEqualTo(1.0);
        assertThat(committee.weightOf(V2)).isEqualTo(2.0);
        assertThat(committee.totalWeight()).isEqualTo(3.0);
    }

    @Test
    @DisplayName("reconstructs weight as reputation x uptime for an older snapshot without a weight")
    void reconstructsWeightFromReputationAndUptime() {
        UUID id = insertDuel(List.of(repAndUptime(V1, 0.5, 0.8)));

        QuorumCommittee committee = lookup.committeeFor(id.toString());

        // 05 §2.2: the sampling weight is reputation × uptime, captured at sampling time.
        assertThat(committee.weightOf(V1)).isCloseTo(0.4, within(1e-9));
    }

    // ------------------------------------------------------------------ unknown / malformed ids are 'unknown', not
    // errors

    @Test
    @DisplayName("an unknown duel id resolves to null — a rejection, not a pass")
    void unknownDuelIsNull() {
        insertDuel(List.of(weighted(V1, 1.0)));

        assertThat(lookup.committeeFor(UUID.randomUUID().toString())).isNull();
    }

    @Test
    @DisplayName("a duel id that is not a UUID names no row here and resolves to null")
    void nonUuidDuelIsNull() {
        // A peer may reference a duel adjudicated on a server we hold no records for; that is 'unknown',
        // not a malformed-input error to throw over.
        assertThat(lookup.committeeFor("0a9f-4c2e")).isNull();
    }

    @Test
    @DisplayName("a blank or null duel id resolves to null")
    void blankAndNullAreNull() {
        assertThat(lookup.committeeFor("")).isNull();
        assertThat(lookup.committeeFor("   ")).isNull();
        assertThat(lookup.committeeFor(null)).isNull();
    }
}

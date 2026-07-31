package io.github.stoicswe.eyeandsickle.server.economy.gate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.protocol.game.PuzzleClass;
import io.github.stoicswe.eyeandsickle.protocol.game.UnlockGate;
import io.github.stoicswe.eyeandsickle.server.economy.account.Account;
import io.github.stoicswe.eyeandsickle.server.economy.account.FakeAccountRepository;
import io.github.stoicswe.eyeandsickle.server.economy.account.UnknownPlayerException;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.EthecoinCost;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.HeatStateRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.ProofOfSkillRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.ReputationRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.SchematicRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateEvaluation.ConditionOutcome;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * The authoritative gate decision ({@code docs/design/02-unlock-gates.md}). This is the server half of
 * Invariant I14: only this evaluator decides what is actually unlocked, reading real state through
 * fakes here.
 *
 * <p>Every gate is tested at its boundary and in its failing direction, because a green happy-path
 * unlock proves almost nothing — the interesting question for a gate is whether it withholds. Special
 * weight goes to: proof-of-skill being tier-gated and never count-gated (Invariant I7); the heat gate
 * running both directions (§2.5); a split being a conjunction; and the two ways the evaluator fails
 * safe — an unknown player is refused, and the schematic port denies by default.
 */
class GateEvaluatorTest {

    private static final String ACCOUNT = "did:plc:player0000000000000000";
    // What the evaluator now keys on: the acting character's DID (09 §9), not the raw account DID. Two
    // characters of one account are gated independently because each has its own character DID.
    private static final String DID = CharacterDid.of(ACCOUNT, 1);
    // A well-formed character DID that names no local character — the "not a local player" case.
    private static final String GHOST = CharacterDid.of("did:plc:ghost00000000000000000", 1);

    private final FakeAccountRepository accounts = new FakeAccountRepository();
    private final FakeGateState gateState = new FakeGateState();
    private final FakeSchematics schematics = new FakeSchematics();

    private final GateEvaluator evaluator = new GateEvaluator(accounts, gateState, schematics);

    private static Account account(String characterDid, long balanceMinor, String heat) {
        CharacterDid character = CharacterDid.from(characterDid);
        return new Account(
                UUID.randomUUID(),
                character.accountDid(),
                character.slot(),
                Ethecoin.ofWei(java.math.BigInteger.valueOf(balanceMinor)
                        .multiply(Ethecoin.WEI_PER_ETHECOIN)
                        .divide(java.math.BigInteger.valueOf(100))),
                new BigDecimal(heat),
                0L);
    }

    private ConditionOutcome only(GateEvaluation evaluation) {
        assertThat(evaluation.conditions()).hasSize(1);
        return evaluation.conditions().get(0);
    }

    // ------------------------------------------------------------------ failing safe

    @Nested
    @DisplayName("the evaluator never grants on missing data")
    class FailsSafe {

        @Test
        @DisplayName("a DID that is not a local player is refused, not treated as zeroed-out state")
        void unknownPlayerIsRejected() {
            // Being lenient here would let a non-account pass a cold gate on an assumed heat of zero.
            GatedOffering offering =
                    GatedOffering.single("x", new HeatStateRequirement(HeatDirection.COLD_GATED, BigDecimal.TEN));

            assertThatThrownBy(() -> evaluator.evaluate(GHOST, offering)).isInstanceOf(UnknownPlayerException.class);
        }

        @Test
        @DisplayName("evaluateAll also refuses an unknown player")
        void unknownPlayerIsRejectedInBatch() {
            assertThatThrownBy(() -> evaluator.evaluateAll(GHOST, List.of()))
                    .isInstanceOf(UnknownPlayerException.class);
        }

        @Test
        @DisplayName("the schematic port denies by default, so a schematic gate fails closed")
        void schematicPortDeniesByDefault() {
            // With SchematicHoldings.Denying, a schematic-gated offering must evaluate as not satisfied —
            // a missing progression slice withholds an unlock, never hands one out.
            GateEvaluator denying = new GateEvaluator(accounts, gateState, new SchematicHoldings.Denying());
            accounts.with(account(DID, 0, "0"));
            GatedOffering offering = GatedOffering.single("infra", new SchematicRequirement("firmware-implant"));

            assertThat(denying.evaluate(DID, offering).satisfied()).isFalse();
        }
    }

    // ------------------------------------------------------------------ ethecoin (I2)

    @Nested
    @DisplayName("ethecoin gate — affordability, and reading moves nothing")
    class EthecoinGate {

        private final GatedOffering offering =
                GatedOffering.single("fuzzer", new EthecoinCost(Ethecoin.ofDecimal("25")));

        @Test
        @DisplayName("a balance above the price satisfies the gate")
        void aboveThePrice() {
            accounts.with(account(DID, 2_501, "0"));
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName("a balance exactly at the price satisfies the gate (boundary)")
        void exactlyThePrice() {
            accounts.with(account(DID, 2_500, "0"));
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName("a balance one minor unit short does not satisfy the gate")
        void oneShort() {
            accounts.with(account(DID, 2_499, "0"));
            GateEvaluation evaluation = evaluator.evaluate(DID, offering);
            assertThat(evaluation.satisfied()).isFalse();
            assertThat(only(evaluation).gate()).isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("evaluating an ethecoin gate never writes a balance — it is a read, not a spend")
        void evaluationMovesNoMoney() {
            accounts.with(account(DID, 10_000, "0"));
            evaluator.evaluate(DID, offering);
            // The spend is a separate transactional act on the ledger; a gate check must not debit.
            assertThat(accounts.writes).isEmpty();
        }
    }

    // ------------------------------------------------------------------ schematic

    @Nested
    @DisplayName("schematic gate")
    class Schematic {

        private final GatedOffering offering =
                GatedOffering.single("mapper", new SchematicRequirement("topology-mapper"));

        @Test
        @DisplayName("holding the schematic satisfies the gate")
        void held() {
            accounts.with(account(DID, 0, "0"));
            schematics.grant(DID, "topology-mapper");
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName("not holding the schematic does not satisfy the gate")
        void missing() {
            accounts.with(account(DID, 0, "0"));
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }

        @Test
        @DisplayName("holding a different schematic does not satisfy the gate")
        void wrongSchematic() {
            accounts.with(account(DID, 0, "0"));
            schematics.grant(DID, "some-other-schematic");
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }
    }

    // ------------------------------------------------------------------ reputation

    @Nested
    @DisplayName("reputation gate")
    class Reputation {

        private final GatedOffering offering =
                GatedOffering.single("dead-drop", new ReputationRequirement(Faction.SICKLE, 120));

        @Test
        @DisplayName("standing above the minimum satisfies the gate")
        void aboveMinimum() {
            accounts.with(account(DID, 0, "0"));
            gateState.setStanding(DID, Faction.SICKLE, 200);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName("standing exactly at the minimum satisfies the gate (boundary)")
        void atMinimum() {
            accounts.with(account(DID, 0, "0"));
            gateState.setStanding(DID, Faction.SICKLE, 120);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName("standing below the minimum does not satisfy the gate")
        void belowMinimum() {
            accounts.with(account(DID, 0, "0"));
            gateState.setStanding(DID, Faction.SICKLE, 119);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }

        @Test
        @DisplayName("no recorded standing counts as zero — enough for a non-positive threshold, not a positive one")
        void absentStandingIsZero() {
            accounts.with(account(DID, 0, "0"));
            // No standing set at all. Against the 120 threshold that is a miss...
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();

            // ...but a contact who deals with anyone not openly hostile (threshold -10) is reachable.
            GatedOffering openContact = GatedOffering.single("fence", new ReputationRequirement(Faction.EYE, -10));
            assertThat(evaluator.evaluate(DID, openContact).satisfied()).isTrue();
        }

        @Test
        @DisplayName("standing is read per named faction — Eye standing does not satisfy a Sickle gate")
        void standingIsPerFaction() {
            accounts.with(account(DID, 0, "0"));
            gateState.setStanding(DID, Faction.EYE, 500);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }
    }

    // ------------------------------------------------------------------ proof-of-skill (I7)

    @Nested
    @DisplayName("proof-of-skill gate — tier-gated, never count-gated (Invariant I7)")
    class ProofOfSkill {

        private final GatedOffering offering = GatedOffering.single(
                "overflow-kit", new ProofOfSkillRequirement(PuzzleClass.OFFSET_CIPHER, DifficultyTier.of(3)));

        @Test
        @DisplayName("a highest live breach at the threshold tier satisfies the gate (boundary)")
        void atThreshold() {
            accounts.with(account(DID, 0, "0"));
            gateState.setHighestLiveBreach(DID, PuzzleClass.OFFSET_CIPHER, 3);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName("a highest live breach above the threshold satisfies the gate")
        void aboveThreshold() {
            accounts.with(account(DID, 0, "0"));
            gateState.setHighestLiveBreach(DID, PuzzleClass.OFFSET_CIPHER, 5);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isTrue();
        }

        @Test
        @DisplayName(
                "a highest live breach BELOW the threshold does not satisfy — no amount of low-tier play unlocks it")
        void belowThreshold() {
            accounts.with(account(DID, 0, "0"));
            // The port already collapses "solved tier 1 a hundred times" to a single highest tier of 1.
            // Against a tier-3 gate that is a miss: count is structurally irrelevant, only the tier counts.
            gateState.setHighestLiveBreach(DID, PuzzleClass.OFFSET_CIPHER, 1);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }

        @Test
        @DisplayName("never having breached a live target of the class does not satisfy the gate")
        void neverBreached() {
            accounts.with(account(DID, 0, "0"));
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }

        @Test
        @DisplayName("competence in another puzzle class does not satisfy this class's gate")
        void wrongClass() {
            accounts.with(account(DID, 0, "0"));
            gateState.setHighestLiveBreach(DID, PuzzleClass.BREACH_PROTOCOL, 5);
            assertThat(evaluator.evaluate(DID, offering).satisfied()).isFalse();
        }
    }

    // ------------------------------------------------------------------ heat state (§2.5, both directions)

    @Nested
    @DisplayName("heat-state gate — runs both directions (§2.5)")
    class Heat {

        @Test
        @DisplayName("cold-gated: reachable at or below the threshold, closed above it")
        void coldGated() {
            GatedOffering fixer = GatedOffering.single(
                    "fixer", new HeatStateRequirement(HeatDirection.COLD_GATED, new BigDecimal("40")));

            accounts.with(account(DID, 0, "39.9999"));
            assertThat(evaluator.evaluate(DID, fixer).satisfied())
                    .as("below threshold: cold, reachable")
                    .isTrue();

            accounts.with(account(DID, 0, "40.0000"));
            assertThat(evaluator.evaluate(DID, fixer).satisfied())
                    .as("exactly at threshold: still reachable")
                    .isTrue();

            accounts.with(account(DID, 0, "40.0001"));
            assertThat(evaluator.evaluate(DID, fixer).satisfied())
                    .as("above threshold: too hot, door closes")
                    .isFalse();
        }

        @Test
        @DisplayName("hot-gated: reachable at or above the threshold, closed below it")
        void hotGated() {
            // The black market opens to the hunted — the only sanctioned route to zero-days.
            GatedOffering broker = GatedOffering.single(
                    "broker", new HeatStateRequirement(HeatDirection.HOT_GATED, new BigDecimal("60")));

            accounts.with(account(DID, 0, "60.0001"));
            assertThat(evaluator.evaluate(DID, broker).satisfied())
                    .as("above threshold: hunted, reachable")
                    .isTrue();

            accounts.with(account(DID, 0, "60.0000"));
            assertThat(evaluator.evaluate(DID, broker).satisfied())
                    .as("exactly at threshold: reachable")
                    .isTrue();

            accounts.with(account(DID, 0, "59.9999"));
            assertThat(evaluator.evaluate(DID, broker).satisfied())
                    .as("below threshold: too clean, no access")
                    .isFalse();
        }

        @Test
        @DisplayName("the same heat opens one direction's door and closes the other's")
        void directionsAreOpposite() {
            accounts.with(account(DID, 0, "50"));
            GatedOffering cold = GatedOffering.single(
                    "cold", new HeatStateRequirement(HeatDirection.COLD_GATED, new BigDecimal("40")));
            GatedOffering hot = GatedOffering.single(
                    "hot", new HeatStateRequirement(HeatDirection.HOT_GATED, new BigDecimal("40")));

            assertThat(evaluator.evaluate(DID, cold).satisfied()).isFalse();
            assertThat(evaluator.evaluate(DID, hot).satisfied()).isTrue();
        }
    }

    // ------------------------------------------------------------------ splits are conjunctions

    @Nested
    @DisplayName("a split gate is a conjunction — every condition must be met")
    class Splits {

        // Relay Chain: schematic framework AND per-session ethecoin.
        private final GatedOffering relayChain = GatedOffering.split(
                "relay-chain",
                new SchematicRequirement("relay-chain-framework"),
                new EthecoinCost(Ethecoin.ofDecimal("5")));

        @Test
        @DisplayName("both halves met -> satisfied, and both outcomes are reported, primary first")
        void bothMet() {
            accounts.with(account(DID, 500, "0"));
            schematics.grant(DID, "relay-chain-framework");

            GateEvaluation evaluation = evaluator.evaluate(DID, relayChain);
            assertThat(evaluation.satisfied()).isTrue();
            assertThat(evaluation.conditions()).hasSize(2);
            assertThat(evaluation.conditions().get(0).gate()).isEqualTo(UnlockGate.SCHEMATIC);
            assertThat(evaluation.conditions().get(1).gate()).isEqualTo(UnlockGate.ETHECOIN);
        }

        @Test
        @DisplayName("primary met but secondary not -> not satisfied")
        void primaryOnly() {
            accounts.with(account(DID, 499, "0"));
            schematics.grant(DID, "relay-chain-framework");

            GateEvaluation evaluation = evaluator.evaluate(DID, relayChain);
            assertThat(evaluation.satisfied()).isFalse();
            assertThat(evaluation.conditions().get(0).met()).isTrue();
            assertThat(evaluation.conditions().get(1).met()).isFalse();
        }

        @Test
        @DisplayName("secondary met but primary not -> not satisfied (never one OR the other)")
        void secondaryOnly() {
            accounts.with(account(DID, 500, "0"));
            // Has the ethecoin, lacks the schematic framework.

            GateEvaluation evaluation = evaluator.evaluate(DID, relayChain);
            assertThat(evaluation.satisfied()).isFalse();
            assertThat(evaluation.conditions().get(0).met()).isFalse();
            assertThat(evaluation.conditions().get(1).met()).isTrue();
        }

        @Test
        @DisplayName("neither half met -> not satisfied")
        void neitherMet() {
            accounts.with(account(DID, 0, "0"));
            assertThat(evaluator.evaluate(DID, relayChain).satisfied()).isFalse();
        }
    }

    // ------------------------------------------------------------------ batch

    @Nested
    @DisplayName("evaluateAll")
    class Batch {

        @Test
        @DisplayName("reads the account exactly once, whatever the number of offerings")
        void accountReadOnce() {
            accounts.with(account(DID, 1_000, "10"));
            List<GatedOffering> offerings = List.of(
                    GatedOffering.single("a", new EthecoinCost(Ethecoin.ofDecimal("5"))),
                    GatedOffering.single("b", new EthecoinCost(Ethecoin.ofDecimal("20"))),
                    GatedOffering.single(
                            "c", new HeatStateRequirement(HeatDirection.COLD_GATED, new BigDecimal("20"))));

            List<GateEvaluation> results = evaluator.evaluateAll(DID, offerings);

            // The balance/heat snapshot is shared across every offering — one read, not three.
            assertThat(accounts.findByCharacterCalls).isEqualTo(1);
            assertThat(results).extracting(GateEvaluation::offeringId).containsExactly("a", "b", "c");
            assertThat(results).extracting(GateEvaluation::satisfied).containsExactly(true, false, true);
        }

        @Test
        @DisplayName("an empty offering set yields an empty result, having still validated the player")
        void emptyOfferings() {
            accounts.with(account(DID, 0, "0"));
            assertThat(evaluator.evaluateAll(DID, List.of())).isEmpty();
            assertThat(accounts.findByCharacterCalls).isEqualTo(1);
        }
    }

    // ------------------------------------------------------------------ fakes

    /** In-memory {@link GateStateRepository}: the two authoritative reads the gates need, without SQL. */
    private static final class FakeGateState extends GateStateRepository {

        private final Map<String, Long> standings = new HashMap<>();
        private final Map<String, DifficultyTier> highestLiveBreach = new HashMap<>();

        FakeGateState() {
            super(mock(JdbcClient.class));
        }

        void setStanding(String characterDid, Faction faction, long standing) {
            standings.put(characterDid + "|" + faction, standing);
        }

        void setHighestLiveBreach(String characterDid, PuzzleClass puzzleClass, int tier) {
            highestLiveBreach.put(characterDid + "|" + puzzleClass, DifficultyTier.of(tier));
        }

        @Override
        public Optional<Long> factionStanding(CharacterDid character, Faction faction) {
            return Optional.ofNullable(standings.get(character.value() + "|" + faction));
        }

        @Override
        public Optional<DifficultyTier> highestLiveBreachTier(CharacterDid character, PuzzleClass puzzleClass) {
            return Optional.ofNullable(highestLiveBreach.get(character.value() + "|" + puzzleClass));
        }
    }

    /** In-memory {@link SchematicHoldings}. */
    private static final class FakeSchematics implements SchematicHoldings {

        private final Set<String> held = new HashSet<>();
        private final Map<String, Integer> expansion = new HashMap<>();

        void grant(String did, String schematicId) {
            held.add(did + "|" + schematicId);
        }

        @Override
        public boolean holdsSchematic(String holderDid, String schematicId) {
            return held.contains(holderDid + "|" + schematicId);
        }

        @Override
        public int vaultExpansionLevel(String holderDid) {
            return expansion.getOrDefault(holderDid, 0);
        }
    }
}

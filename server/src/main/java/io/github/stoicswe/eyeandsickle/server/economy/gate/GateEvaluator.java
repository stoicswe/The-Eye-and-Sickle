package io.github.stoicswe.eyeandsickle.server.economy.gate;

import io.github.stoicswe.eyeandsickle.protocol.game.DifficultyTier;
import io.github.stoicswe.eyeandsickle.server.economy.account.Account;
import io.github.stoicswe.eyeandsickle.server.economy.account.AccountRepository;
import io.github.stoicswe.eyeandsickle.server.economy.account.UnknownPlayerException;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.EthecoinCost;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.HeatStateRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.ProofOfSkillRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.ReputationRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateCondition.SchematicRequirement;
import io.github.stoicswe.eyeandsickle.server.economy.gate.GateEvaluation.ConditionOutcome;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Service;

/**
 * Decides, authoritatively, whether a player satisfies an offering's gate ({@code
 * docs/design/02-unlock-gates.md}).
 *
 * <h2>This is the server half of the gate system (Invariant I14)</h2>
 *
 * The client may grey out what it believes is locked; only this evaluator decides what actually is. It
 * reads real state — the character's materialised balance and personal heat, their faction standing,
 * their breach history, their schematics — and compares each against the offering's server-defined
 * requirement. Nothing here trusts a value that came from a request: the character DID identifies the
 * acting character, and every threshold comes from the {@link GatedOffering}, which was defined on the
 * server.
 *
 * <h2>Gates are per-character (09 §9)</h2>
 *
 * The acting party is a <em>character</em>, not an account: balance, heat, faction standing and
 * proof-of-skill are all per-character state ({@code docs/architecture/09-player-state-portability.md}
 * §3, §9). So the DID this evaluator is given is a character DID, and it is resolved to the single
 * character it names before any state is read — two characters of one account are gated independently.
 *
 * <h2>A split gate is a conjunction</h2>
 *
 * An offering is satisfied only when its primary and (if present) its secondary condition both are.
 * That is the correct reading of a split ({@code docs/design/02-unlock-gates.md} §1.1): the Relay Chain
 * needs the framework schematic <em>and</em> the per-session hop cost; the Zero-Day needs the hot heat
 * state <em>and</em> the ethecoin. Never one or the other.
 *
 * <h2>The evaluator never grants on missing data</h2>
 *
 * A character DID that names no local character cannot be evaluated — {@link UnknownPlayerException} —
 * rather than being treated as having zero heat and thereby passing a cold gate. And the schematic gate
 * reads a port that <em>denies</em> by default ({@link SchematicHoldings}). Both choices fail in the safe
 * direction: a gap withholds an unlock, it never hands one out.
 */
@Service
public class GateEvaluator {

    private final AccountRepository accounts;
    private final GateStateRepository gateState;
    private final SchematicHoldings schematics;

    GateEvaluator(AccountRepository accounts, GateStateRepository gateState, SchematicHoldings schematics) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.gateState = Objects.requireNonNull(gateState, "gateState");
        this.schematics = Objects.requireNonNull(schematics, "schematics");
    }

    /**
     * Evaluates one offering for one character.
     *
     * @param characterDid the acting character's DID ({@code did:eyeandsickle:<slot>:<accountDid>})
     * @param offering the server-defined offering
     * @return the verdict, with per-condition detail
     * @throws UnknownPlayerException if the string names no active local character
     */
    public GateEvaluation evaluate(String characterDid, GatedOffering offering) {
        Objects.requireNonNull(characterDid, "characterDid");
        Objects.requireNonNull(offering, "offering");
        Account account =
                accounts.findByCharacterDid(characterDid).orElseThrow(() -> new UnknownPlayerException(characterDid));
        return evaluate(account, offering);
    }

    /**
     * Evaluates many offerings for one character, reading the character's account once.
     *
     * <p>This is the "what can I unlock" query. The account snapshot (balance, heat) is shared across
     * every offering; only the per-condition earned-state reads (standing, breach tier, schematic) are
     * repeated, and only for the conditions that need them.
     *
     * @param characterDid the acting character's DID
     * @param offerings the offerings to evaluate; the catalogue, or a subset
     * @return one verdict per offering, in iteration order
     * @throws UnknownPlayerException if the string names no active local character
     */
    public List<GateEvaluation> evaluateAll(String characterDid, Collection<GatedOffering> offerings) {
        Objects.requireNonNull(characterDid, "characterDid");
        Objects.requireNonNull(offerings, "offerings");
        Account account =
                accounts.findByCharacterDid(characterDid).orElseThrow(() -> new UnknownPlayerException(characterDid));
        List<GateEvaluation> results = new ArrayList<>(offerings.size());
        for (GatedOffering offering : offerings) {
            results.add(evaluate(account, offering));
        }
        return results;
    }

    private GateEvaluation evaluate(Account account, GatedOffering offering) {
        List<ConditionOutcome> outcomes = new ArrayList<>(2);
        outcomes.add(evaluateCondition(account, offering.primary()));
        if (offering.isSplit()) {
            outcomes.add(evaluateCondition(account, offering.secondary()));
        }
        boolean satisfied = outcomes.stream().allMatch(ConditionOutcome::met);
        return new GateEvaluation(offering.offeringId(), satisfied, outcomes);
    }

    private ConditionOutcome evaluateCondition(Account account, GateCondition condition) {
        // The earned-state reads key on the resolved character (account.characterDid()), which is non-null
        // here: findByCharacterDid only ever resolves a DID-bound row.
        return switch (condition) {
            case EthecoinCost cost -> ethecoin(account, cost);
            case SchematicRequirement schematic -> schematic(account, schematic);
            case ReputationRequirement reputation -> reputation(account, reputation);
            case ProofOfSkillRequirement proof -> proofOfSkill(account, proof);
            case HeatStateRequirement heat -> heat(account, heat);
        };
    }

    private ConditionOutcome ethecoin(Account account, EthecoinCost cost) {
        // Affordability, not payment: can they buy it, read from the materialised balance. The spend is
        // a separate transactional act (LedgerService); this read moves nothing.
        boolean met = account.balance().compareTo(cost.price()) >= 0;
        return new ConditionOutcome(
                cost.gate(),
                met,
                // ⚠ Ethecoin.toString, not the raw count. This printed hundredths and called them
                // "EC(minor)"; the same line in wei would be an eighteen-digit integer in a
                // message a human reads to find out why a gate refused them.
                "balance " + account.balance() + " vs price " + cost.price());
    }

    private ConditionOutcome schematic(Account account, SchematicRequirement requirement) {
        // Schematics are held per-character: the holder is the character DID, not the shared account DID.
        boolean met = schematics.holdsSchematic(account.characterDid().value(), requirement.schematicId());
        return new ConditionOutcome(
                requirement.gate(),
                met,
                (met ? "holds" : "missing") + " schematic '" + requirement.schematicId() + "'");
    }

    private ConditionOutcome reputation(Account account, ReputationRequirement requirement) {
        // No standing recorded is a standing of zero (uncommitted), not an error.
        long standing = gateState
                .factionStanding(account.characterDid(), requirement.faction())
                .orElse(0L);
        boolean met = standing >= requirement.minimumStanding();
        return new ConditionOutcome(
                requirement.gate(),
                met,
                requirement.faction() + " standing " + standing + " vs required " + requirement.minimumStanding());
    }

    private ConditionOutcome proofOfSkill(Account account, ProofOfSkillRequirement requirement) {
        // Tier-gated, never count-gated (Invariant I7): the highest live breach, compared to a tier.
        Optional<DifficultyTier> best =
                gateState.highestLiveBreachTier(account.characterDid(), requirement.puzzleClass());
        boolean met = best.map(tier -> tier.tier() >= requirement.minimumTier().tier())
                .orElse(false);
        String have = best.map(tier -> Integer.toString(tier.tier())).orElse("none");
        return new ConditionOutcome(
                requirement.gate(),
                met,
                "highest live " + requirement.puzzleClass() + " breach " + have + " vs required tier "
                        + requirement.minimumTier().tier());
    }

    private ConditionOutcome heat(Account account, HeatStateRequirement requirement) {
        int comparison = account.personalHeat().compareTo(requirement.threshold());
        boolean met =
                switch (requirement.direction()) {
                    // Cold-gated: reachable at or below the threshold (respectable fixers avoid the hunted).
                    case COLD_GATED -> comparison <= 0;
                    // Hot-gated: reachable at or above it (the black market opens to the hunted).
                    case HOT_GATED -> comparison >= 0;
                };
        return new ConditionOutcome(
                requirement.gate(),
                met,
                requirement.direction() + " heat threshold "
                        + requirement.threshold().toPlainString() + ", personal heat "
                        + account.personalHeat().toPlainString());
    }
}

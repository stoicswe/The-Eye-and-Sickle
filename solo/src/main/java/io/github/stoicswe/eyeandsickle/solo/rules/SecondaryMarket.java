package io.github.stoicswe.eyeandsickle.solo.rules;

import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.util.Random;

/**
 * ⚠ <b>[PROPOSAL]</b> — the player-to-player upgrade market, and the fact that it is not safe.
 *
 * <h2>The mechanic in one line</h2>
 *
 * A sale is <b>not escrowed</b>. The seller takes payment and then chooses whether to send the
 * package. Not sending is a real, available move — which is what makes buying from a stranger a
 * decision rather than a transaction, and what gives {@link #reputation} anything to measure.
 *
 * <h2>⚠ THIS IS A THIRD REPUTATION AND IT MUST NEVER SHARE A FIELD WITH THE OTHER TWO</h2>
 *
 * {@code CLAUDE.md} and {@code docs/design/glossary.md} already warn that <b>factionReputation</b>
 * (standing with the Eye or the Sickle) and <b>validatorReputation</b> (federation trust weighting)
 * are unrelated quantities that must never share a field, a column, a label or a colour. This adds a
 * <b>third</b>: <b>traderReputation</b> — whether you deliver what you were paid for.
 *
 * <p>All three are independent on purpose. A Sickle hero can be a thief; a scrupulous trader can be
 * a validator nobody trusts. Collapsing any two would make one of them a proxy for the other and
 * quietly delete a whole axis of characterisation. The field is {@code SoloSave.traderReputation}
 * and nothing else reads or writes it.
 *
 * <h2>⚠ Why a decrease is a CHANCE and not a certainty</h2>
 *
 * A guaranteed penalty is just a price, and a price is something a player budgets for. A
 * <em>chance</em> that rises with each undelivered sale cannot be budgeted — the first defection is
 * usually free, the fifth usually is not, and the seller never knows which one will be the one that
 * costs them. That is what makes a reputation worth protecting rather than worth spending.
 *
 * <p>It also matches the fiction: reputation damage comes from a buyer complaining loudly enough to
 * be believed, and whether they are believed is not up to the seller.
 *
 * <h2>Nothing here can fire in single player</h2>
 *
 * Both parties are players. Solo has no counterparty, so a solo character's trader reputation is a
 * permanent zero and no sale is ever pending. The rules exist and are tested now so that the day
 * multiplayer lands (<b>CL-8</b>) this is a transport change and not an engine change — the same
 * argument {@code AccessLog} is written under.
 */
public final class SecondaryMarket {

    private SecondaryMarket() {}

    /** A clean trader. Everyone starts here; it is not an achievement. */
    public static final int STARTING_REPUTATION = 0;

    /** The best a trader can be. Small range on purpose — see {@link #deliver}. */
    public static final int MAX_REPUTATION = 100;

    public static final int MIN_REPUTATION = -100;

    /**
     * What one honest delivery is worth.
     *
     * <p>⚠ Deliberately much smaller than {@link #DEFECTION_PENALTY}. A reputation that is slow to
     * build and quick to lose is the only shape in which it is worth having: if honesty paid back as
     * fast as defection cost, the optimal play would be to alternate, and the score would measure
     * nothing but volume.
     */
    public static final int DELIVERY_REWARD = 2;

    /** What one <em>caught</em> defection costs. */
    public static final int DEFECTION_PENALTY = 15;

    /**
     * The chance a defection is noticed, in percent, for the first one.
     *
     * <p>Low, because the first time is the one a seller can most plausibly claim was an accident —
     * and because a mechanic whose first use always punishes you is a mechanic nobody uses twice.
     */
    public static final int BASE_DETECTION_PERCENT = 20;

    /** How much each previous defection adds to that chance. */
    public static final int DETECTION_PER_DEFECTION = 18;

    /**
     * The chance this defection is noticed, given how many came before it.
     *
     * <p>Rises steeply and then saturates: by the fifth undelivered sale it is effectively certain.
     * A seller who has done this once is probably fine; a seller who has made a habit of it is not,
     * which is exactly the signal a reputation is supposed to carry.
     */
    public static int detectionChance(int previousDefections) {
        int chance = BASE_DETECTION_PERCENT + Math.max(0, previousDefections) * DETECTION_PER_DEFECTION;
        return Math.min(95, chance);
    }

    /** The seller delivered. Reputation up, slowly. */
    public static int deliver(SoloSave save) {
        save.traderDeliveries++;
        save.traderReputation = clamp(save.traderReputation + DELIVERY_REWARD);
        return save.traderReputation;
    }

    /**
     * The seller took the money and did not send the package.
     *
     * <p>⚠ The draw happens <b>here and once</b>, and its result is recorded — never re-rolled on
     * read. The same rule every other roll in this engine follows ({@code NetRules.beginSweep}'s
     * frozen result, {@code HostState.detectRoll}): a chance a player can re-roll by reloading is
     * not a chance, it is a delay.
     *
     * @param random the caller's source, so a test can make the coin land
     * @return whether it was noticed
     */
    public static boolean defect(SoloSave save, Random random) {
        boolean caught = random.nextInt(100) < detectionChance(save.traderDefections);
        save.traderDefections++;
        if (caught) {
            save.traderReputation = clamp(save.traderReputation - DEFECTION_PENALTY);
        }
        return caught;
    }

    public static int reputation(SoloSave save) {
        return save == null ? STARTING_REPUTATION : save.traderReputation;
    }

    /**
     * How a trader reads to somebody deciding whether to buy from them.
     *
     * <p>Bands rather than the raw number, and the wording is deliberately about <em>risk</em>
     * rather than about morality — the market does not care whether you are a good person, only
     * whether the thing arrives.
     */
    public static String standing(SoloSave save) {
        int score = reputation(save);
        if (score >= 60) {
            return "trusted — deliveries have always arrived";
        }
        if (score >= 20) {
            return "known good";
        }
        if (score > -20) {
            return "unproven";
        }
        if (score > -60) {
            return "has taken money and not delivered";
        }
        return "do not pay this trader first";
    }

    private static int clamp(int value) {
        return Math.max(MIN_REPUTATION, Math.min(MAX_REPUTATION, value));
    }
}

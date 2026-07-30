package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Something a breach could be opened against, described well enough to decide whether to.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §2 instantiates an attempt with a target "with a
 * defense profile (firewall tier, tarpit, honeypot flag, canary tokens, ...) drawn from {@code
 * 09-defense-and-hardening.md}". This record is that profile as the <em>player</em> has it, which is
 * not the same document.
 *
 * <h2>Every defence field is recon output, not ground truth</h2>
 *
 * {@code docs/design/07-recon-tools.md} §2 sells knowing-before-you-leap as a ladder of purchases: the
 * Passive Sniffer sees one hop, the Topology Mapper two, the Traffic Analyzer "separates live/defended
 * targets from dormant ones" and is reputation-gated precisely because "knowing which nodes are
 * <em>worth</em> hitting (and which will fight back) is economy-distorting if free". If this record
 * carried the server's truth, every one of those tools would be redundant on arrival.
 *
 * <p>So {@code firewallTier}, {@code tarpit} and {@code canaries} mean "as far as recon has
 * established", and {@code liveOrDormant} reads {@link TargetState#DORMANT} both for a target that is
 * dormant and for one nobody has analysed yet. Which is also the trap worth naming: dormant is the
 * default reading, and {@code docs/design/02-unlock-gates.md} §2.4 gives proof-of-skill credit only
 * against a live target, so an unanalysed target is one the player may work perfectly and earn no
 * unlock from.
 *
 * <h2>{@code honeypotSuspected}, never {@code isHoneypot}</h2>
 *
 * {@code docs/design/09-defense-and-hardening.md} §1 defines the Honeypot Stash as "a decoy
 * high-hackable zone containing junk; raiders can't tell until extraction", and §2 says its function is
 * to make "casing a target unreliable, so raiding stays risky". A boolean stating the truth would
 * delete an entire reputation-gated defensive item at the point of rendering. The name carries the
 * uncertainty so nobody later "cleans it up".
 *
 * <h2>{@code minerCrack} is why this can be the safest thing in the game</h2>
 *
 * A crack is the core minigame run against a foreign miner on the player's <em>own</em> rig
 * ({@code docs/design/04-mining.md} §5.1). Invariant I9 means it generates no heat on any outcome, and
 * §5.1 makes it the game's teaching vector for exactly that reason. The estimate beside it is the
 * timing bet §5.1 describes: a buffer found at minute five holds almost nothing, one found at hour four
 * holds the cap. It is an estimate because making it exact would turn the decision into arithmetic.
 *
 * @param targetId what {@code beginBreach} takes
 * @param address where it is, as the player writes it
 * @param label what it is called
 * @param role the Enumeration banner when recon has established it; {@code ""} otherwise
 * @param difficultyTier how hard it is expected to be, on the one shared scale
 * @param liveOrDormant whether recon has established it is defended; dormant is also the not-yet-known
 *     reading
 * @param minerCrack whether this is a foreign miner on the player's own rig — no heat, whatever happens
 * @param firewallTier 0 for none, 1–3 for the tiers {@code 09} §1 publishes
 * @param tarpit whether a Tarpit is believed armed; it surcharges every action rather than the budget
 * @param canaries whether canary tokens are believed present; touching one tags the player's handle
 *     ({@code docs/design/09-defense-and-hardening.md} §2)
 * @param honeypotSuspected whether this looks like a decoy — a suspicion, never a finding
 * @param estimatedBufferWei the crack prize, estimated, in minor units; 0 for anything that is
 *     not a crack
 * @param computeCost cycles the attempt will reserve for its whole duration
 * @param available whether a breach can be opened against it right now
 * @param refusal {@code ""} when available; otherwise why not, in words
 */
public record BreachTarget(
        String targetId,
        String address,
        String label,
        String role,
        DifficultyTier difficultyTier,
        TargetState liveOrDormant,
        boolean minerCrack,
        int firewallTier,
        boolean tarpit,
        boolean canaries,
        boolean honeypotSuspected,
        BigInteger estimatedBufferWei,
        long computeCost,
        boolean available,
        String refusal) {

    public BreachTarget {
        Objects.requireNonNull(targetId, "targetId");
        Objects.requireNonNull(address, "address");
        Objects.requireNonNull(label, "label");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(difficultyTier, "difficultyTier");
        Objects.requireNonNull(liveOrDormant, "liveOrDormant");
        Objects.requireNonNull(refusal, "refusal");

        // 09 §1 publishes exactly three firewall tiers; 0 is "none observed". A fourth would be a
        // balance decision with a price and a compute cost attached, not a value that arrives here.
        if (firewallTier < 0 || firewallTier > 3) {
            throw new IllegalArgumentException(
                    "firewallTier is 0 (none) or 1..3 (docs/design/09-defense-and-hardening.md §1), was "
                            + firewallTier);
        }
        if (estimatedBufferWei.signum() < 0) {
            throw new IllegalArgumentException(
                    "estimatedBufferWei must not be negative, was " + estimatedBufferWei);
        }
        // A buffer estimate on a non-crack is not harmless flavour: a buffer is the accumulated yield of
        // a miner sitting on the player's own rig, so quoting one against an offensive target promises
        // an ethecoin payout that 03 §5 rule 3 forbids an offensive breach from ever making.
        if (!minerCrack && estimatedBufferWei.signum() != 0) {
            throw new IllegalArgumentException("Only a miner crack has a yield buffer to seize "
                    + "(docs/design/04-mining.md §5.1); a non-crack target quoted "
                    + estimatedBufferWei + " wei");
        }
        if (computeCost < 0) {
            throw new IllegalArgumentException("computeCost must not be negative, was " + computeCost);
        }
        if (available && !refusal.isEmpty()) {
            throw new IllegalArgumentException(
                    "An available target carries no refusal; " + targetId + " gave \"" + refusal + "\"");
        }
        if (!available && refusal.isEmpty()) {
            throw new IllegalArgumentException("An unavailable target must say why "
                    + "(docs/design/05-hacking-minigame.md §1 constraint 4); " + targetId + " gave no refusal");
        }
    }
}

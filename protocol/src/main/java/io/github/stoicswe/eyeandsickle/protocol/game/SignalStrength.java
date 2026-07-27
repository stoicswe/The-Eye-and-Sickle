package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * How loud a machine is on the wire — the property that decides how easily a sweep finds it.
 *
 * <p>{@code docs/design/04-mining.md} §2.1 already publishes this exact three-value vocabulary
 * (Low / Moderate / High) as a deployed miner's <em>signal strength</em>, and §3.3 states the rule it
 * serves: "Nothing announces itself. <strong>Signal strength is what the player pays for.</strong>" This
 * enum is that established vocabulary generalised from miners to whole machines. Reusing the words is
 * the point — a player who has learned that a bigger miner is easier to find already knows what a HIGH
 * host means, and a second scale would have taught them the same lesson twice in two dialects.
 *
 * <h2>Why this is not called {@code noise}</h2>
 *
 * {@code noise} is taken, and it means something incompatible. The 2026-07-26 decision recorded in
 * {@code docs/design/15-open-questions.md} §3 fixed it as the sum of a rig's {@code CONTROL_CHANNEL},
 * {@code RELAY_HOP} and {@code BOT_FRAME} cycles — "work that reaches other machines" — i.e. a scalar
 * attributed to <em>a player's own actions</em>, which is why a rig at full load on self-mining and
 * local defence correctly reads zero. A machine carrying a {@code noise} field would make the word mean
 * two things at once, and the second reading it would invite ("this node is making noise at me") is
 * precisely backwards: the host is not acting, the sweeping player is.
 *
 * <h2>What this enum decides: nothing</h2>
 *
 * Loud is easier to find than quiet, and a better instrument finds more than a worse one. Both of those
 * are design ({@code docs/design/07-recon-tools.md} §2 — the recon ladder is a sensitivity ladder). The
 * <em>numbers</em> — what probability a given sweep tier has against a given signal — are balance values
 * and live with the authoritative rules, never here. The practical test from {@code package-info}: if a
 * constant here changed, would a player gain something? A detection probability plainly would, so this
 * type carries the three names and nothing else.
 *
 * <p>Nor does a machine's signal say anything about whether the player will ever see it. Reach is a hard
 * ceiling raised only by a schematic-gated tool ({@code 07} §2, Invariant I2); signal only matters inside
 * the reach the player already has. Schematics buy reach, ethecoin buys sensitivity.
 */
public enum SignalStrength {

    /** Quiet. A desktop that talks to almost nothing, and the hardest class of machine to notice. */
    LOW,

    /** Ordinary chatter. */
    MODERATE,

    /** Chatty by function — infrastructure, or a machine with something running on it that should not be. */
    HIGH
}

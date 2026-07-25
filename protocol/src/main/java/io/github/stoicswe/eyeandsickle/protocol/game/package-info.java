/**
 * Game-domain vocabulary that has to mean the same thing on both sides of the wire.
 *
 * <h2>What belongs here</h2>
 *
 * Closed sets the client must be able to <em>name</em> in order to render state and build requests:
 * unlock gates, storage tiers, puzzle classes, outcomes. Names follow {@code
 * docs/design/glossary.md} so the docs and the code stay searchable against each other.
 *
 * <h2>What does not</h2>
 *
 * Any rule, threshold, price, yield, curve or evaluation. The client may know that {@code
 * PROOF_OF_SKILL} exists; it must not know — or believe it can decide — whether a given player has
 * satisfied one. That check reads authoritative state and is precisely what a cheating client would
 * forge (Invariant I14).
 *
 * <p>The practical test: if a constant here changed, would a player gain something? If yes, it is a
 * balance value and it belongs to the server.
 *
 * <h2>Two quantities that must never convert</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin} and {@link
 * io.github.stoicswe.eyeandsickle.protocol.game.Cycles} are separate types with no conversion, no
 * common supertype and no cross-type arithmetic. Invariant I1 — compute is never purchasable with
 * ethecoin — is the rule that stops the economy becoming a compounding flywheel, and as two bare
 * {@code long}s the forbidden conversion would be a one-character mistake that compiles. The type
 * system cannot forbid it outright, but it can make it a deliberate, greppable act.
 *
 * <p>The same instinct runs through the compute ledger: {@link
 * io.github.stoicswe.eyeandsickle.protocol.game.ComputeAllocation} can name two rigs because a
 * deployed miner spends the <em>host's</em> cycles while the deployer pays a control channel
 * (Invariant I6), and {@link io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget} reports
 * {@code available} rather than deriving it, so that a hidden parasite still shows up as cycles that
 * do not add up.
 *
 * <h2>The reputation trap</h2>
 *
 * {@code factionReputation} (a player's Eye/Sickle standing, {@code docs/design/01-core-resources.md}
 * §5) and {@code validatorReputation} (a federated server's trust score, {@code
 * docs/architecture/05-validator-quorum.md}) are <strong>unrelated</strong> and must never share a
 * field, a column, or a type. Neither is a plain {@code Reputation}. If you find yourself writing a
 * generic reputation type, you are about to merge two things the design keeps apart on purpose.
 */
package io.github.stoicswe.eyeandsickle.protocol.game;

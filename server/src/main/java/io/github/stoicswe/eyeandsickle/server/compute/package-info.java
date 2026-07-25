/**
 * The compute ledger — the master scarcity ({@code docs/design/01-core-resources.md} §1) and the
 * game's most important HUD element (§1.4).
 *
 * <p>This slice owns two schema tables and one view: {@code rigs} (the compute ceiling), {@code
 * compute_allocations} (the ledger), and {@code rig_compute_reconciliation} (the authoritative
 * arithmetic over <em>all</em> rows). It is authoritative (Invariant I14): a client is told outcomes,
 * never the arithmetic that produced them, and a client asking for more cycles than exist is refused,
 * not clamped.
 *
 * <h2>The three invariants this package is built around</h2>
 *
 * <ul>
 *   <li><b>I1 — compute is never purchasable with ethecoin.</b> Made structural here: no type, method
 *       or endpoint in this package accepts, returns or imports {@link
 *       io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin}. Rig capacity ({@code
 *       rigs.total_cycles}) is written only by {@link
 *       io.github.stoicswe.eyeandsickle.server.compute.ComputeLedgerService#createRig} at provisioning
 *       time — never by an allocation, a purchase, or anything touching a balance. There is
 *       deliberately no "buy cycles" code path to review, because there is no method that could host
 *       one. Capacity expansion (schematics, story milestones — {@code
 *       docs/design/11-rig-infrastructure.md}) is a different system and is out of this slice.
 *   <li><b>I6 — a deployed miner consumes the host's compute, not the deployer's.</b> One deployment
 *       produces <em>two</em> allocation rows on two rigs: a {@code control_channel} on the deployer
 *       ({@link ComputeLedgerService#openControlChannel}, capacity-checked — this is the
 *       self-correcting network cap of {@code docs/design/04-mining.md} §2.2) and a {@code
 *       deployed_miner} on the host ({@link ComputeLedgerService#chargeHostForParasite},
 *       deliberately <em>not</em> capacity-checked, because a parasite does not respect its host's
 *       budget and the resulting over-subscription is the audit signal).
 *   <li><b>I5 — deployed miners are the only offline income; self-mining and bots are online-only.</b>
 *       Not enforced here directly, but the control channel this slice charges is what makes offline
 *       payout require an online deployer.
 * </ul>
 *
 * <h2>The audit surface is exposed, never "fixed"</h2>
 *
 * A discrepancy between a rig's ceiling and what is reserved against it is precisely what a hidden
 * hostile miner creates ({@code docs/design/04-mining.md} §3.1, {@code
 * docs/architecture/06-data-model.md} §1 constraint 4). This package surfaces it twice, at two layers:
 * {@link io.github.stoicswe.eyeandsickle.server.compute.RigComputeReconciliation} carries the raw,
 * <em>signed</em> available figure (negative on an over-subscribed rig) over all rows, and {@link
 * io.github.stoicswe.eyeandsickle.protocol.game.ComputeBudget#unaccountedFor()} surfaces the gap
 * between what the server discloses and what it actually reserves. Neither is silently reconciled.
 *
 * <h2>Seams onto slices this package does not own</h2>
 *
 * <ul>
 *   <li>{@link io.github.stoicswe.eyeandsickle.server.compute.AllocationDisclosurePolicy} — deciding
 *       which allocations a rig's owner is shown. The default discloses everything; the defensive
 *       /deployed-mining slice supplies the real policy that hides rootkit-wrapped parasites ({@code
 *       docs/design/09-defense-and-hardening.md}). Hiding a row is what turns {@code unaccountedFor()}
 *       from zero into the manual-audit signal.
 *   <li>Principal-based authorization (does the caller own this rig?) belongs to the identity/security
 *       slice ({@code docs/architecture/02-identity-and-auth.md}). This package enforces the narrower,
 *       structural rule that an allocation may only be released through the rig it is charged to.
 *   <li>Returning recovered cycles to the pool on a timer: {@link ComputeLedgerService#sweepRecovered}
 *       is the operation; wiring a scheduler to call it is left to the application.
 * </ul>
 */
package io.github.stoicswe.eyeandsickle.server.compute;

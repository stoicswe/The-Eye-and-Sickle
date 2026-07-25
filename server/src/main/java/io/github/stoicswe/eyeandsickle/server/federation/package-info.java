/**
 * The trustless half of the architecture: the validator quorum, reputation, and non-recognition.
 *
 * <p>This package implements {@code docs/architecture/05-validator-quorum.md} in full — how the
 * federation adjudicates a cross-server duel outcome with <strong>no single arbiter</strong>
 * (Invariant I15). The shape of the loop (§5) is:
 *
 * <ol>
 *   <li>a cross-server duel needs adjudication;
 *   <li>{@link io.github.stoicswe.eyeandsickle.server.federation.sampling.AResSampler sample} a
 *       committee of N opted-in validators, weighted-random by {@code reputation × uptime} and
 *       floor-protected for newcomers (§2);
 *   <li>validators evaluate and sign the outcome; consensus needs {@code 2f+1}-of-{@code 3f+1}
 *       weighted power (§1), the arithmetic already owned by protocol {@code QuorumCommittee};
 *   <li>the signed outcome becomes a {@code duel_grant} provenance event ({@code
 *       docs/architecture/04} §3.1);
 *   <li>{@link io.github.stoicswe.eyeandsickle.server.federation.reputation.ReputationRules update}
 *       every sampled validator's reputation (§3, AIMD) and uptime (§4, no-show decay);
 *   <li>verifiers later confirm the outcome by re-checking sampled membership and the weighted
 *       threshold (§7), which the protocol verifier does.
 * </ol>
 *
 * <h2>The one reputation this package is about</h2>
 *
 * {@code validatorReputation} — a federated <em>server's</em> trust score — and nothing to do with
 * {@code factionReputation}, a <em>player's</em> Eye/Sickle standing ({@code
 * docs/design/glossary.md}). They share no table, no column and no key here, exactly as {@code
 * docs/architecture/06} §1 constraint 5 requires. If a query in this package ever joined the two,
 * it would be merging two notions the design keeps apart on purpose.
 *
 * <h2>Authoritative, not predictive</h2>
 *
 * Every decision here is made and validated on the server (Invariant I14). Sampling weights are
 * frozen at sampling time so a duel is never silently re-adjudicated against reputations that moved
 * afterward; equivocation is judged from cryptographic proof, never from a report; and a flagged
 * server's items are refused across the federation ({@code docs/architecture/03} §4) rather than
 * banned by an authority that does not exist.
 *
 * <h2>Seams to slices this package does not own</h2>
 *
 * The play-level definition of a duel and <em>which</em> engagements need quorum are {@code
 * [PROPOSAL]} ({@code docs/design/13}); vote transport (asking a remote validator to sign and
 * collecting its answer) is the federation-transport concern. This package takes the collected
 * signatures as input and adjudicates them, and exposes the collection point as a REST endpoint —
 * it does not implement the network gossip that fills it.
 */
package io.github.stoicswe.eyeandsickle.server.federation;

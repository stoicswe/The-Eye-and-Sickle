package io.github.stoicswe.eyeandsickle.protocol.game;

import java.time.Instant;
import java.util.UUID;

/**
 * Everything the client may READ about one character, in one document.
 *
 * <h2>⚠ Why a snapshot and not 104 endpoints</h2>
 *
 * {@code GameSession} exposes about a hundred reads. One REST call per read looks obvious and is
 * wrong, because the client is already built around a local copy of authoritative state:
 * {@code RemoteGameSession}'s <strong>last-known-good rule</strong> requires every read to return the
 * last value the server sent <em>even while disconnected</em>. A read that made a network call could
 * not do that, and the accessibility argument behind the rule — a HUD that empties on a network
 * hiccup removes information from a player mid-decision — would be lost.
 *
 * <p>So the server sends this, the client answers every read from it, and adding a system adds a
 * <em>field</em> rather than a controller. ⚠ It is also the only shape that keeps <b>I14</b>
 * checkable: with one snapshot and one intent endpoint there is exactly one place a client-supplied
 * value crosses into the rules, and it is auditable at a glance.
 *
 * <h2>⚠ RESULTS ONLY — never the rules that produced them</h2>
 *
 * A snapshot carries what is true, never why. No yield curves, no thresholds, no gate tables, no
 * difficulty formula. A client holding the rules can <em>predict</em>, and predicting is one step
 * from asserting — which is the whole attack I14 exists to refuse. {@code ArchitectureRulesTest}
 * already refuses balance values in this module; this is the same rule stated where it will be
 * tempting to break.
 *
 * <h2>⚠ Deliberately incomplete, and honest about it</h2>
 *
 * This covers what the server can authoritatively answer <em>today</em>: compute, balance, heat,
 * uptime. Mining, the chain, breach, the filesystem and the network map exist only in {@code solo}
 * and are not here because <strong>the server cannot yet answer them</strong> — a field carrying a
 * plausible zero would be worse than an absent one, because the client would render it as fact. They
 * arrive as the engine does ({@code docs/architecture/13-the-game-transport.md} §4).
 *
 * @param characterId which character this describes
 * @param revision increments on every change; ⚠ lets the client tell a genuinely unchanged world
 *     from a stalled connection, which look identical otherwise
 * @param serverTime the server's clock — ⚠ authoritative, because every deadline in the game is
 *     measured against it and the client's clock is a value a cheater controls
 * @param computeBudget the rig's compute, allocated and free
 * @param balance the character's ethecoin
 * @param personalHeat current heat. ⚠ {@link java.math.BigDecimal}, not an int — heat is fractional
 *     server-side, and rounding it here would silently lose precision on the value that gates
 *     detection. A player sitting just under a threshold must not be shown as being at it.
 * @param uptimeSeconds how long this character has been running
 */
public record GameSnapshot(
        UUID characterId,
        long revision,
        Instant serverTime,
        ComputeBudget computeBudget,
        Ethecoin balance,
        java.math.BigDecimal personalHeat,
        long uptimeSeconds) {}

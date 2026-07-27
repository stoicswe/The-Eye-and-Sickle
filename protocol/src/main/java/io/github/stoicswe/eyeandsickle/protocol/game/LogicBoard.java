package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.List;

/**
 * A Logic layer: the shape of the lock, everything the player has learned about the code, and the guess
 * they are composing.
 *
 * <p>{@code docs/design/05-hacking-minigame.md} §3.1 — "reconstruct a lock's rule from probe
 * responses", Mastermind-family deduction. This is the class Credential folded into on 2026-07-26,
 * because "pattern deduction" and "reconstruct a rule from probe responses" were the same verb and
 * keeping both would have shipped the reskin §3.1's merge rule exists to forbid. The Rainbow Table and
 * the Credential Harvester were repointed here with it (§6, P-4).
 *
 * <h2>Nothing here is the code</h2>
 *
 * There is no field on this record that could hold the secret, and that is a design decision rather
 * than an omission. What travels is {@code history} (responses the player has already been given),
 * {@code facts} (reads they have already paid for), and {@code known} (positions a tool has already
 * revealed). Every one of those is information the player possesses; a renderer holding this board
 * cannot draw more than the player has earned, because more never arrived.
 *
 * <h2>{@code candidatesRemaining} is the class's diegetic centrepiece</h2>
 *
 * <em>KEYSPACE 4096 → 37.</em> It is derived by filtering the candidate set against every response in
 * {@code history}, which is arithmetic the player could in principle do by hand from what they already
 * hold — so publishing it hands over no new information, it hands over <em>legibility</em>. That is the
 * difference between the class feeling like deduction and feeling like guessing, and it is the same
 * argument that lets {@link LogicProbe#inconsistent()} be published.
 *
 * <p>It is also what makes §3.2's human-read step visible: a player watching the number collapse is
 * interpreting the responses holistically, which is precisely the step a fixed-heuristic bot does not
 * take.
 *
 * <h2>{@code salted} is a counter the player must be able to see before they spend</h2>
 *
 * {@code docs/design/06-intrusion-tools.md} §1 makes the Rainbow Table "useless against salted
 * targets", and §2 calls that a deliberate conditional power spike: "devastating against lazy targets,
 * useless against prepared ones, so it rewards recon (know before you buy the attempt)." A hard counter
 * the player cannot see coming is not a counter, it is a tax — so the flag is on the board, and the
 * engine refuses the tool for zero attention rather than charging for a null result.
 *
 * <h2>What the constructor deliberately does not check</h2>
 *
 * {@code known} and {@code draft} are both expected to hold exactly {@code length} entries. That is not
 * enforced, on {@link ResolutionRecord}'s precedent: these records describe what a producer has, and a
 * constructor that rejects a half-built board converts a cosmetic gap in one panel into an exception
 * thrown out of a repaint. The engine keeps the sizes honest; the renderer clamps.
 *
 * @param length how many positions the code has
 * @param alphabet the symbols in play, in a stable order so a tumbler cycles the same way every time
 * @param salted whether the Rainbow Table's hard counter applies here
 * @param history every response so far, oldest first
 * @param facts free reads the player has bought with quiet listens, oldest first; each is true
 * @param known one entry per position, {@code ""} where the position is not yet revealed
 * @param draft one entry per position, the guess being composed; bookkeeping until it is submitted
 * @param keyspace how many codes were possible before any probe — the left-hand number
 * @param candidatesRemaining how many are still consistent with every response so far; never more than
 *     {@code keyspace}
 */
public record LogicBoard(
        int length,
        List<String> alphabet,
        boolean salted,
        List<LogicProbe> history,
        List<String> facts,
        List<String> known,
        List<String> draft,
        int keyspace,
        int candidatesRemaining)
        implements BreachBoard {

    public LogicBoard {
        // Every list is frozen and null-rejecting. `known` in particular relies on it: an unrevealed
        // position is the empty string, and a null there would mean the difference between "not yet
        // known" and "known to be nothing" stopped being expressible.
        alphabet = List.copyOf(alphabet);
        history = List.copyOf(history);
        facts = List.copyOf(facts);
        known = List.copyOf(known);
        draft = List.copyOf(draft);

        if (length < 1) {
            throw new IllegalArgumentException("A Logic code has at least one position, was " + length);
        }
        if (keyspace < 0) {
            throw new IllegalArgumentException("keyspace must not be negative, was " + keyspace);
        }
        if (candidatesRemaining < 0) {
            throw new IllegalArgumentException("candidatesRemaining must not be negative, was " + candidatesRemaining);
        }
        // A filter cannot yield more candidates than it started with. If this ever fires, the filter and
        // the keyspace were computed against different alphabets or different lengths — which on screen
        // reads as a puzzle getting easier the more you learn about it.
        if (candidatesRemaining > keyspace) {
            throw new IllegalArgumentException(
                    "candidatesRemaining " + candidatesRemaining + " exceeds the starting keyspace " + keyspace);
        }
    }

    @Override
    public PuzzleClass puzzleClass() {
        return PuzzleClass.LOGIC;
    }
}

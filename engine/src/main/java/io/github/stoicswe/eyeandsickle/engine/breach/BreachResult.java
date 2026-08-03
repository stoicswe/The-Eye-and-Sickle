package io.github.stoicswe.eyeandsickle.engine.breach;

/**
 * What the engine says back when the player tries something.
 *
 * <h2>Why this is not the client's {@code Outcome}</h2>
 *
 * The client's port speaks in exit statuses — {@code ok}, {@code refused}, {@code gated}, {@code
 * usage} — because {@code docs/client/04} §3.5 maps them onto real {@code sysexits.h} values that a
 * shell pipeline reads out of {@code $?}. That vocabulary belongs to the client, and {@code solo}
 * cannot import it: this module is consumed by the view layer and never the other way round.
 *
 * <p>So the engine returns this instead, and {@code LocalGameSession} translates. Three states are
 * enough because the engine only ever has three things to say: it happened, it did not happen, or a
 * gate is in the way — and the third is separate from the second because {@code docs/client/04} §3.5
 * requires a gate to be reported with its requirement in words rather than as a bare refusal. A gate
 * you can read is legible; a gate you cannot is just an obstruction.
 *
 * @param applied whether the save changed
 * @param gated whether a gate blocked this, in which case {@code message} states the requirement
 * @param message what to tell the player, always in words and never a code
 */
public record BreachResult(boolean applied, boolean gated, String message) {

    public static BreachResult applied(String message) {
        return new BreachResult(true, false, message);
    }

    public static BreachResult refused(String message) {
        return new BreachResult(false, false, message);
    }

    /** Blocked by a gate. The requirement goes in the message, because an unstated gate teaches nothing. */
    public static BreachResult gated(String requirement) {
        return new BreachResult(false, true, requirement);
    }
}

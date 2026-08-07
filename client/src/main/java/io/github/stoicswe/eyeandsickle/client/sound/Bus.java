package io.github.stoicswe.eyeandsickle.client.sound;

/**
 * Where a voice is routed, and therefore which slider governs it.
 *
 * <h2>⚠ TWO BUSES, NOT ONE VOLUME, AND THE REASON IS ACCESSIBILITY RATHER THAN TASTE</h2>
 *
 * A single volume control forces one decision on two unrelated questions. Music is continuous,
 * optional, and the thing a player turns off to concentrate or to listen to their own; effects are
 * momentary and carry <i>information</i> — a message arrived, a scan finished, something was refused.
 * Collapsing them means a player who wants the game quiet in the background loses the notifications
 * too, and a player who wants notifications has to accept the soundtrack.
 *
 * <p>WCAG 1.4.2 (Audio Control) is the formal version of the same point: audio that plays
 * automatically for more than three seconds must be independently stoppable. The music bus <b>is</b>
 * that control, which is why it exists as a separate slider rather than as a checkbox somewhere.
 *
 * <h2>⚠ MASTER IS DELIBERATELY NOT A MEMBER OF THIS ENUM</h2>
 *
 * Master is not a destination — nothing routes <i>to</i> it — it is the final multiply applied after
 * the buses have been summed. Listing it here would invite {@code bus == Bus.MASTER} branches in
 * voice code, where the answer is always "no": a voice never routes to master, it routes to a bus
 * that master then scales. It lives on {@link SoftMixer} as a scalar for that reason.
 *
 * <h2>⚠ THE SET IS CLOSED ON PURPOSE</h2>
 *
 * Every new bus is a new slider in Settings and a new decision the player has to understand, so the
 * bar for a third one is a category of sound that a reasonable person would want at a different level
 * from both of these — not merely a different <i>kind</i> of sound. Ambience, if it ever exists, is
 * an argument for a third; a louder alarm is not (that is a per-effect gain, which
 * {@link Sfx#gain()} already carries).
 */
public enum Bus {

    /**
     * Music beds. One voice at a time in the steady state, two briefly during a crossfade.
     *
     * <p>⚠ Ducked rather than silenced when an effect needs to be heard over it — see
     * {@link SoftMixer#duck}. A bus that cut out entirely would be far more distracting than one that
     * steps down for a moment.
     */
    MUSIC,

    /**
     * Everything momentary: notifications, confirmations, refusals, the interface's own noises.
     *
     * <p>⚠ This is the bus that carries <b>information</b>, so it is the one that must not be
     * drowned. Effects are never ducked; music is ducked under them.
     */
    EFFECTS
}

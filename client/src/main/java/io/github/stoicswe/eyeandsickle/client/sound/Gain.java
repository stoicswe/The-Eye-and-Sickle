package io.github.stoicswe.eyeandsickle.client.sound;

/**
 * The arithmetic of loudness. Pure, so every rule below is checkable without a sound card.
 *
 * <p>Everything here is a plain function of its arguments — no state, no line, no thread. That is
 * deliberate and it is the same seam {@code SecurityCenterView.latestOf} and {@code DirectView.state}
 * exist for: the rules that decide what a player hears must be testable without starting the thing
 * that plays it, because a machine that cannot open an audio device is exactly where the build runs.
 */
final class Gain {

    private Gain() {}

    /**
     * Turns a 0–100 slider into an amplitude multiplier.
     *
     * <h2>⚠ A LINEAR SLIDER IS NOT A LINEAR LOUDNESS</h2>
     *
     * Perceived loudness goes roughly as the square root of amplitude, so a slider that maps straight
     * onto amplitude spends most of its travel in a band that all sounds nearly full: the useful
     * adjustment is crushed into the bottom fifth and the top three-quarters do almost nothing. A
     * <b>square-law taper</b> — amplitude = fraction² — is the classic correction, and it puts a real
     * difference under every part of the slider's travel.
     *
     * <h2>⚠ THIS IS A DELIBERATE CHANGE FROM WHAT THE ONE-CHIME VERSION DID, AND IT IS QUIETER</h2>
     *
     * The previous implementation set the line's own {@code MASTER_GAIN} to {@code 20·log10(fraction)}
     * decibels, which is <i>exactly</i> amplitude = fraction — a linear taper. So the same slider
     * position is quieter now: the default 60 was 0.60 of full amplitude and is now 0.36. That is the
     * safe direction for a default to move, and the reasoning recorded for choosing 60 in the first
     * place ("a game that announces itself at full volume the first time it is opened is one people
     * mute permanently instead of turning down") is about <i>perceived</i> loudness, which is the
     * thing this curve now actually models.
     *
     * <h2>⚠ ZERO IS EXACTLY ZERO, AND NOT A VERY SMALL NUMBER</h2>
     *
     * No logarithm is taken and no floor constant is needed, so muting is exact rather than −80 dB.
     * That matters because {@link SoftMixer} uses a zero master to skip opening the device at all: a
     * muted client should not be holding a mixer line, and on some drivers a zero-gain write is still
     * an audible click.
     */
    static float amplitude(int percent) {
        int clamped = Math.max(0, Math.min(100, percent));
        float fraction = clamped / 100.0f;
        return fraction * fraction;
    }

    /**
     * The outgoing side of an equal-power crossfade, for {@code progress} in 0–1.
     *
     * <h2>⚠ EQUAL POWER, NOT LINEAR — A LINEAR CROSSFADE AUDIBLY DIPS</h2>
     *
     * Two uncorrelated signals sum in <i>power</i>, not in amplitude. Fading one down linearly while
     * the other comes up linearly leaves the midpoint at 0.5 + 0.5 = 1.0 of amplitude but only
     * √(0.5² + 0.5²) ≈ 0.71 of power — a hole in the middle of every track change, which is heard as
     * the music dropping out and coming back rather than as one bed becoming another. Taking the two
     * gains from cosine and sine keeps cos² + sin² = 1, so the power is constant the whole way across.
     *
     * <h2>⚠ THIS IS NOT AN EASING CURVE, AND §5 IS NOT IN PLAY</h2>
     *
     * {@code docs/design/ui-design-language.md} §5 permits no easing anywhere in the interface and
     * {@code UiContractTest} makes it build-blocking. That rule is about <b>motion</b> — a control
     * that slides into place on a curve — and its whole argument is that eased movement in a
     * measurement-dense interface reads as imprecision. A gain ramp is not motion, moves nothing on
     * screen, and is inaudible as anything but "the music changed"; the trigonometry here is the
     * definition of constant power, not a decorative shape. Nothing in this file touches
     * {@code Interpolator}, {@code Timeline} or {@code AnimationTimer}, which is what those contract
     * tests actually scan for.
     */
    static float fadeOut(double progress) {
        return (float) Math.cos(clampProgress(progress) * Math.PI / 2.0);
    }

    /** The incoming side of the same crossfade. See {@link #fadeOut}. */
    static float fadeIn(double progress) {
        return (float) Math.sin(clampProgress(progress) * Math.PI / 2.0);
    }

    private static double clampProgress(double progress) {
        return Math.max(0.0, Math.min(1.0, progress));
    }

    /**
     * Keeps a summed buffer inside ±1 without the harshness of a hard clamp.
     *
     * <h2>⚠ SOMETHING MUST BOUND THE SUM, AND WRAPPING IS THE FAILURE TO AVOID</h2>
     *
     * Voices are summed, so several loud ones at once can exceed full scale. Converting an
     * out-of-range float to a 16-bit integer without bounding it <b>wraps</b> — a sample just over
     * +1.0 comes out as a large negative number — which is heard as a violent crack rather than as
     * distortion. That is the one outcome worth engineering against, because it is loud, it is
     * sudden, and it happens precisely when the game is busiest.
     *
     * <p>A hard clamp fixes the wrap and flattens the peaks into a square edge, which is audible as a
     * buzz. So this is <b>piecewise</b>: exactly transparent below {@link #KNEE}, where nearly all
     * real content sits, and a hyperbolic-tangent curve above it that approaches ±1 without ever
     * reaching it.
     *
     * <h2>⚠ IT IS PIECEWISE BECAUSE A SINGLE CUBIC CANNOT DO THIS, AND THE FIRST VERSION HERE TRIED</h2>
     *
     * The tempting shape is the well-known soft clipper {@code y = 1.5x − 0.5x³}, which is smooth and
     * flat-topped at ±1. It was written here, with a comment claiming it had unit slope at the origin.
     * It does not: its derivative at zero is <b>1.5</b>, so it was quietly making the entire game
     * 3.5 dB louder and pushing material that was already at full scale straight into the hard bound.
     * {@code GainTest.transparentWhereItMatters} caught it on the first run.
     *
     * <p>⚠ The reason is worth keeping, because it rules out the whole family: for {@code y = ax +
     * bx³} through the origin, unit slope forces {@code a = 1}, and passing through {@code (1,1)} then
     * forces {@code b = 0} — the identity. <b>No cubic is both transparent at the origin and
     * saturating at ±1.</b> A curve that is transparent where it matters has to stop being a single
     * polynomial somewhere, which is what the knee is.
     *
     * <p>⚠ It is a <b>limiter, not a compressor</b> — there is no attack, release or lookahead, and no
     * state at all. Those would be a dynamics processor, which is a much larger thing to get right and
     * would make this function untestable in one line.
     */
    static float limit(float sample) {
        float magnitude = Math.abs(sample);
        if (magnitude <= KNEE) {
            // ⚠ Returned unchanged, not multiplied by anything. Ordinary material must come out bit
            // for bit as it went in, or this is a colouring effect rather than a safety net.
            return sample;
        }
        float over = (magnitude - KNEE) / (1.0f - KNEE);
        float curved = KNEE + (1.0f - KNEE) * (float) Math.tanh(over);
        return Math.copySign(curved, sample);
    }

    /**
     * Where the limiter stops being transparent.
     *
     * <p>⚠ 0.7 — about −3 dB. High enough that a normal mix never touches it, low enough that there
     * is room for the curve to bend before full scale rather than turning a corner. {@code Math.tanh}
     * is only evaluated above this, so the common path is a compare and a return.
     */
    private static final float KNEE = 0.7f;
}

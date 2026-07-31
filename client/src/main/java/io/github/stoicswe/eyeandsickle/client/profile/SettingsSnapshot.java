package io.github.stoicswe.eyeandsickle.client.profile;

/**
 * The <b>machine-wide</b> settings the setup assistant can change, captured so Cancel can undo them.
 *
 * <h2>Why this shrank</h2>
 *
 * It used to carry the palette and the pointer too. It does not any more, and that is the point of
 * {@link VisualSettings}: the assistant now previews a look on a <em>detached</em>
 * {@code VisualSettings} that belongs to no character until one is created, so cancelling out of the
 * palette pane costs nothing to undo — there was never anything written to put back.
 *
 * <p>What is left is the handful the assistant still writes globally: the rig's name, the teaching
 * level, and the two accessibility floors. Those genuinely are the machine's, so backing out of the
 * assistant genuinely does have to restore them.
 *
 * <h2>Why a type and not four local variables</h2>
 *
 * Four locals in {@code EyeAndSickleClient.showSetupWizard} would do the job exactly once, and would
 * then silently stop doing it the day somebody adds a pane. Written down as a record, the set of
 * restorable fields is a thing {@code SettingsSnapshotTest} can compare against the set of fields
 * the assistant actually assigns — so a new pane whose value nobody restores fails the build instead
 * of quietly changing a setting on a machine the player was only looking around.
 *
 * <p>⚠ Restoring the values is only half of it. Two of them need a runtime call before anything on
 * screen changes — {@code themes.setReducedMotionOverride} and {@code applyWindowSettings} — and
 * this type deliberately does not make them. It knows what the values were; it does not know which
 * {@code Scene}s are live. The caller does both.
 *
 * @param reducedMotionOverride nullable, and the null is meaningful: it means "follow the system"
 */
public record SettingsSnapshot(
        String rigHostname, String teachingLevel, int uiScalePercent, Boolean reducedMotionOverride) {

    /** Every machine-wide setting the assistant may write. Kept in sync with the record by its test. */
    public static SettingsSnapshot of(ClientProfile.Settings settings) {
        return new SettingsSnapshot(
                settings.rigHostname, settings.teachingLevel, settings.uiScalePercent, settings.reducedMotionOverride);
    }

    /** Puts the captured values back. The caller re-applies them to the live scene. */
    public void restoreTo(ClientProfile.Settings settings) {
        settings.rigHostname = rigHostname;
        settings.teachingLevel = teachingLevel;
        settings.uiScalePercent = uiScalePercent;
        settings.reducedMotionOverride = reducedMotionOverride;
    }
}

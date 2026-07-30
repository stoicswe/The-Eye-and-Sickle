package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * Whether an upgrade is ordinary software or firmware.
 *
 * <h2>Why the distinction is mechanical rather than flavour</h2>
 *
 * Firmware sits below the program it upgrades. That one fact produces every rule attached to
 * {@link #FIRMWARE} and each rule is the real-world behaviour rather than a game-ism:
 *
 * <ul>
 *   <li><b>The tool must be stopped.</b> You cannot rewrite the firmware of a device while the device
 *       is using it. Real flashing tools refuse for the same reason, and a half-written firmware is
 *       how a device is bricked — which is why the refusal here is a refusal and not a warning.
 *   <li><b>It takes two things to install.</b> The schematic is the authorisation and the image is the
 *       payload. Neither alone does anything.
 *   <li><b>It costs more.</b> A firmware image is a permanent capability's payload, not a consumable.
 * </ul>
 *
 * <p>⚠ The two-part requirement is <b>not</b> a second unlock gate, and the distinction matters
 * because Invariant <b>I3</b> allows exactly one gate per item. {@code docs/design/02-unlock-gates.md}
 * §1.1 sanctions precisely this split — <em>"Rainbow Table is EC + schematic (buy the table, but the
 * capability to use it is found)"</em> — with the standing condition that <b>the ceiling component is
 * always on the non-EC side</b>. Here the schematic is the ceiling and the image is a purchasable
 * payload, so money alone buys nothing (<b>I2</b>).
 */
public enum UpgradeKind {

    /** An ordinary tool upgrade. Installs on its own, whatever else the rig is doing. */
    SOFTWARE,

    /**
     * Firmware: needs its schematic, needs the affected tool stopped, and costs more.
     *
     * <p>See {@code docs/design/11-rig-infrastructure.md} §3 and §4's gate-discipline checklist —
     * rule 2 in particular, which requires that anything touching mining income must not add
     * <em>cycles</em>, or it creates the compute-buys-compute loop {@code docs/design/00} §4 forbids.
     */
    FIRMWARE;

    public boolean isFirmware() {
        return this == FIRMWARE;
    }
}

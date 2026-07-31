package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;

/**
 * What an upgrade sitting on somebody else's machine actually is, and how it compares to yours.
 *
 * <h2>Why this exists</h2>
 *
 * A stolen upgrade used to be an opaque file: {@code sweep-upgrade.pkg}, forty to three hundred
 * megabytes, and no way to find out what it was without spending the download and the exposure to
 * take it. That made every copy of every tool the same proposition — a player who already owned the
 * Wide Sweep had no reason to look at another one, and no reason to prefer a hard target over an easy
 * one. The decision this record feeds is the one worth having: <em>is this worth the transfer?</em>
 *
 * <h2>⚠ Everything here is readable WITHOUT taking the file, and that is deliberate</h2>
 *
 * This is what a package's own metadata says about itself — a real one carries exactly this, which is
 * how a package manager can tell you what it is about to install before it installs it. Nothing here
 * is a secret the machine is keeping; the secret is the payload, and that still costs a download.
 *
 * @param itemType the catalogue id this would install as
 * @param displayName what the tool is called
 * @param summary what the tool does, in the catalogue's own words
 * @param version the build this machine is carrying
 * @param yourVersion the newest build of the same tool you already hold, or
 *     {@link UpgradeVersion#UNKNOWN} if you hold none
 * @param standing how installing this would relate to what you have — see {@link Standing}
 * @param gate the unlock gate the installed item sits behind
 * @param sizeBytes the payload's size, which is what the transfer will cost in time
 * @param resaleWei what this copy fetches if sold rather than installed, or 0 if it may not
 *     be sold
 * @param sellable whether it may be turned into money at all — Invariant <b>I2</b> decides this
 * @param equippedCycles what the installed tool reserves when equipped, or 0
 * @param kind software or firmware — see {@link UpgradeKind} for what firmware implies
 * @param requiresSchematic the schematic that authorises flashing this, or empty for software
 * @param haveSchematic whether the player holds that schematic
 * @param blockedBy why it cannot be flashed right now (the affected tool is running), or empty
 */
public record UpgradeOffer(
        String itemType,
        String displayName,
        String summary,
        UpgradeVersion version,
        UpgradeVersion yourVersion,
        Standing standing,
        UnlockGate gate,
        long sizeBytes,
        BigInteger resaleWei,
        boolean sellable,
        long equippedCycles,
        UpgradeKind kind,
        String requiresSchematic,
        boolean haveSchematic,
        String blockedBy) {

    /** The shape before firmware existed — ordinary software, no schematic, nothing blocking. */
    public UpgradeOffer(
            String itemType,
            String displayName,
            String summary,
            UpgradeVersion version,
            UpgradeVersion yourVersion,
            Standing standing,
            UnlockGate gate,
            long sizeBytes,
            BigInteger resaleWei,
            boolean sellable,
            long equippedCycles) {
        this(
                itemType,
                displayName,
                summary,
                version,
                yourVersion,
                standing,
                gate,
                sizeBytes,
                resaleWei,
                sellable,
                equippedCycles,
                UpgradeKind.SOFTWARE,
                "",
                true,
                "");
    }

    public UpgradeOffer {
        kind = kind == null ? UpgradeKind.SOFTWARE : kind;
        requiresSchematic = requiresSchematic == null ? "" : requiresSchematic;
        blockedBy = blockedBy == null ? "" : blockedBy;
    }

    /** Whether this is firmware, with everything that implies. */
    public boolean firmware() {
        return kind == UpgradeKind.FIRMWARE;
    }

    /**
     * Whether it could be flashed right now, if it were on this rig.
     *
     * <p>⚠ Distinct from {@link #worthInstalling()}, which is about the <em>build</em>. A newer
     * firmware image the player has no schematic for is worth having and cannot be flashed, and
     * collapsing the two questions would either hide a good acquisition or promise an install that
     * refuses.
     */
    public boolean readyToFlash() {
        return !firmware() || (haveSchematic && blockedBy.isEmpty());
    }

    /**
     * What still stands between this image and a flashed one, in words. Empty for software.
     *
     * <p>⚠ The schematic is named first when both are missing, for the same reason
     * {@code Repac.install} checks it first: a player told to stop mining, who then hits a schematic
     * refusal they were never going to clear, has lost their hashrate for nothing.
     */
    public String flashRequirement() {
        if (!firmware()) {
            return "";
        }
        if (!haveSchematic) {
            return "Needs the " + requiresSchematic + " schematic, which is recovered rather than "
                    + "bought. The image is inert without it — and it does not expire.";
        }
        return blockedBy.isEmpty()
                ? "Schematic held. Ready to flash."
                : blockedBy + " Firmware sits underneath the program using it.";
    }

    /**
     * How a candidate build relates to what the player already holds.
     *
     * <p>Four answers rather than a boolean, because "you have none", "this replaces yours", "this is
     * the one you have" and "yours is newer" are four different decisions and a player told the wrong
     * one will act on it. ⚠ {@link #OLDER} is <b>not</b> "don't bother": an older build is still worth
     * real ethecoin, and that is the case the compare view has to make rather than bury.
     */
    public enum Standing {
        NEW,
        UPGRADE,
        SAME,
        OLDER
    }

    /** Whether this is a build the player does not already have in some form. */
    public boolean worthInstalling() {
        return standing == Standing.NEW || standing == Standing.UPGRADE;
    }

    /**
     * The one-line verdict — what the compare view leads with.
     *
     * <p>⚠ It always names a reason to care, including when the answer is "yours is newer". A verdict
     * that read only "older" would be the interface deciding for the player, and the resale value is
     * exactly what makes that decision theirs.
     */
    public String verdict() {
        return switch (standing) {
            case NEW -> "You do not have this tool.";
            case UPGRADE -> "Newer than yours (" + yourVersion + ") — installing replaces it.";
            case SAME -> "The same build you already have (" + yourVersion + ").";
            case OLDER -> "Older than yours (" + yourVersion + "). Worth taking to sell, not to install.";
        };
    }
}

package io.github.stoicswe.eyeandsickle.client.ui.widgets;

import io.github.stoicswe.eyeandsickle.client.ui.WallpaperMode;
import javafx.scene.layout.Region;

/**
 * The desk backdrop, whichever kind the player chose.
 *
 * <h2>Why a container rather than a second backdrop</h2>
 *
 * {@code DeskManager.setBackdrop} takes one node and inserts it under the windows and under the snap
 * preview. Two wallpapers would mean either calling it again on every settings change — which
 * repeatedly adds and removes a child of the desk while the player is dragging a window across it —
 * or teaching the window manager that there is more than one kind of wallpaper, which is not its
 * question. So the deck hands it one node and this decides what is inside.
 *
 * <p>Both layers exist for the life of the deck and one of them is visible. That is deliberate: a
 * layer built on demand would have to be laid out and measured before its first frame, and the
 * wallpaper is the one surface where a frame of nothing is very visible.
 */
public final class Wallpaper extends Region {

    private final Substrate substrate = new Substrate();
    private final RingField ring = new RingField();

    private WallpaperMode mode = WallpaperMode.DRIFT;

    public Wallpaper() {
        // ⚠ Mouse-transparent, or DeskManager's click-bare-desk-to-drop-focus stops working — its
        // tests target node identity rather than coordinates, so this would fail as a focus bug
        // rather than as a wallpaper one.
        setMouseTransparent(true);
        substrate.setManaged(false);
        ring.setManaged(false);
        getChildren().addAll(substrate, ring);
        setMode(WallpaperMode.DRIFT);
    }

    /** Off, texture, or emblem. Safe to call repeatedly with the same value. */
    public void setMode(WallpaperMode next) {
        this.mode = next == null ? WallpaperMode.DRIFT : next;
        boolean isRing = this.mode.isRing();
        substrate.setVisible(!isRing && this.mode != WallpaperMode.OFF);
        ring.setVisible(isRing);
        // ⚠ The hidden layer's ticker is STOPPED, not just hidden. A Pulse subscription on an
        // invisible node is work nobody can see, and the drift ticker rewrites the whole character
        // field every time it fires.
        substrate.setMode(isRing ? WallpaperMode.OFF : this.mode);
        ring.setGlitching(this.mode == WallpaperMode.RING_GLITCH);
        requestLayout();
    }

    public WallpaperMode mode() {
        return mode;
    }

    /** Chromatic aberration, which only the character texture has. */
    public void setAberration(boolean on) {
        substrate.setAberration(on);
    }

    /**
     * Whether the wallpaper's colour separation shifts rather than sitting still.
     *
     * <p>Handed to <b>both</b> layers: it fringes the ring's tears and it makes the character
     * texture's channels breathe. One setting, applied to whichever wallpaper the player is on —
     * a per-wallpaper duplicate would be two controls that look identical and do the same thing.
     */
    public void setChromatic(boolean on) {
        ring.setChromatic(on);
        substrate.setChromatic(on);
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth();
        double h = getHeight();
        substrate.resizeRelocate(0, 0, w, h);
        ring.resizeRelocate(0, 0, w, h);
    }

    /** Stops both drivers. Called by {@code DeckShell.dispose}. */
    public void dispose() {
        substrate.dispose();
        ring.dispose();
    }
}

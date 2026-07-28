package io.github.stoicswe.eyeandsickle.client.ui;

import java.util.Random;

/**
 * The operator's picture — the one they set, or the one the rig makes up for them.
 *
 * <h2>The default is generated, not shipped</h2>
 *
 * A player who has not chosen a picture gets a silhouette torn up by static. It is drawn from the
 * handle, so it is <b>theirs</b> — two operators get different noise — and it is drawn rather than
 * loaded because a shipped placeholder is a file to package for five platforms in exchange for one
 * image nobody chose.
 *
 * <p>⚠ The silhouette is deliberately unflattering. A neutral grey head reads as "loading"; a figure
 * breaking up under interference reads as <em>somebody who has not told you who they are</em>, which
 * is the correct thing for this game to say about an operator with no picture. It is also the state
 * the interface most wants the player to leave, and a placeholder that looks finished never gets
 * replaced.
 *
 * <h2>Pixels, not a Canvas</h2>
 *
 * Everything here returns {@code int[]} ARGB. That makes it encodable by {@link Png} and checkable
 * without a toolkit — a generator that drew onto a {@code Canvas} could only be tested by starting
 * JavaFX, and this one has real arithmetic in it worth testing.
 */
public final class Avatar {

    private Avatar() {}

    /** Stored and displayed at this size. Small enough that a PNG of one fits in a save file. */
    public static final int SIZE = 96;

    /**
     * The generated silhouette, as ARGB pixels.
     *
     * <p>Deterministic from {@code handle}: the same operator gets the same noise every launch. A
     * picture that reshuffled on each start would be a picture nobody could recognise, which is the
     * one job it has.
     */
    public static int[] placeholder(String handle) {
        int[] out = new int[SIZE * SIZE];
        Random random = new Random(handle == null ? 0 : handle.hashCode());

        // The palette is the deck's, in spirit: a near-black ground and a cold grey figure. Written
        // as constants here rather than looked up, because this produces PIXELS and there is no
        // stylesheet at this level — §10 criterion 2 governs CSS colours, not image data.
        int ground = 0xFF0B0E11;
        int figure = 0xFF2A3138;
        int highlight = 0xFF48525C;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                out[y * SIZE + x] = ground;
            }
        }

        // A head and shoulders, in two ellipses. Crude on purpose — at ninety-six pixels a detailed
        // figure is mud, and the shape only has to read as a person at a glance.
        double headCx = SIZE / 2.0;
        double headCy = SIZE * 0.36;
        double headR = SIZE * 0.17;
        double bodyCx = SIZE / 2.0;
        double bodyCy = SIZE * 1.02;
        double bodyRx = SIZE * 0.34;
        double bodyRy = SIZE * 0.44;

        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                boolean head = distance(x, y, headCx, headCy, headR, headR) <= 1.0;
                boolean body = distance(x, y, bodyCx, bodyCy, bodyRx, bodyRy) <= 1.0;
                if (head || body) {
                    out[y * SIZE + x] = figure;
                }
            }
        }

        // ── the interference ────────────────────────────────────────────────────────────────
        //
        // Three effects, because one of them alone reads as a mistake rather than as a signal:
        // horizontal tear lines, a scatter of bright pixels, and a block offset. Together they read
        // as a picture arriving badly.
        for (int band = 0; band < 7; band++) {
            int y = random.nextInt(SIZE);
            int height = 1 + random.nextInt(3);
            int shift = random.nextInt(17) - 8;
            for (int row = y; row < Math.min(SIZE, y + height); row++) {
                int[] line = new int[SIZE];
                for (int x = 0; x < SIZE; x++) {
                    int from = Math.floorMod(x - shift, SIZE);
                    line[x] = out[row * SIZE + from];
                }
                System.arraycopy(line, 0, out, row * SIZE, SIZE);
            }
        }
        int speckles = SIZE * SIZE / 40;
        for (int i = 0; i < speckles; i++) {
            int x = random.nextInt(SIZE);
            int y = random.nextInt(SIZE);
            out[y * SIZE + x] = random.nextInt(3) == 0 ? highlight : ground;
        }
        return out;
    }

    /** Whether a point is inside an ellipse, as a normalised squared distance. */
    private static double distance(int x, int y, double cx, double cy, double rx, double ry) {
        double dx = (x + 0.5 - cx) / rx;
        double dy = (y + 0.5 - cy) / ry;
        return dx * dx + dy * dy;
    }

    /** The generated silhouette as a PNG, ready to store or draw. */
    public static byte[] placeholderPng(String handle) {
        return Png.encode(placeholder(handle), SIZE, SIZE);
    }

    /**
     * Decodes what a save holds, or generates the placeholder when it holds nothing.
     *
     * <p>⚠ A stored value that will not decode falls back rather than throwing. The save is a plain
     * JSON file a player can edit, and a corrupt avatar must cost them a picture, not a character.
     */
    public static javafx.scene.image.Image image(String base64, String handle) {
        byte[] bytes = decode(base64);
        if (bytes != null) {
            try {
                javafx.scene.image.Image image =
                        new javafx.scene.image.Image(new java.io.ByteArrayInputStream(bytes));
                if (!image.isError()) {
                    return image;
                }
            } catch (RuntimeException unreadable) {
                // Fall through to the placeholder.
            }
        }
        return new javafx.scene.image.Image(
                new java.io.ByteArrayInputStream(placeholderPng(handle)));
    }

    private static byte[] decode(String base64) {
        if (base64 == null || base64.isBlank()) {
            return null;
        }
        try {
            return java.util.Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException notBase64) {
            return null;
        }
    }

    public static String encode(byte[] png) {
        return png == null ? "" : java.util.Base64.getEncoder().encodeToString(png);
    }
}

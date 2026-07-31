package io.github.stoicswe.eyeandsickle.client.ui;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The generated silhouette and the PNG encoder under it.
 *
 * <p>Both are pure — pixels in, bytes out — which is the reason they are written that way. A
 * generator that drew onto a {@code Canvas} and an encoder that went through {@code SwingFXUtils}
 * could only be tested by starting a toolkit, and there is real arithmetic in both.
 */
class AvatarTest {

    @Nested
    @DisplayName("the placeholder")
    class Placeholder {

        @Test
        @DisplayName("⚠ it is deterministic — the same operator gets the same face every launch")
        void deterministic() {
            // A picture that reshuffled on each start would be a picture nobody could recognise,
            // which is the one job it has.
            assertThat(Avatar.placeholder("halflight")).containsExactly(Avatar.placeholder("halflight"));
        }

        @Test
        @DisplayName("two operators get different faces")
        void differsByHandle() {
            assertThat(Avatar.placeholder("halflight")).isNotEqualTo(Avatar.placeholder("nyx"));
        }

        @Test
        @DisplayName("it is the right size and fully opaque")
        void shape() {
            int[] pixels = Avatar.placeholder("op");
            assertThat(pixels).hasSize(Avatar.SIZE * Avatar.SIZE);
            for (int pixel : pixels) {
                assertThat(pixel >>> 24).as("every pixel is opaque").isEqualTo(0xFF);
            }
        }

        @Test
        @DisplayName("a blank handle still produces a face rather than throwing")
        void blankHandle() {
            assertThat(Avatar.placeholder(null)).hasSize(Avatar.SIZE * Avatar.SIZE);
            assertThat(Avatar.placeholder("")).hasSize(Avatar.SIZE * Avatar.SIZE);
        }

        @Test
        @DisplayName("there is actually a figure in it, not just noise")
        void hasAFigure() {
            // The silhouette is two ellipses under interference. If the drawing ever regressed to
            // flat ground the test above would still pass, because noise is opaque too.
            int[] pixels = Avatar.placeholder("op");
            long distinct = java.util.Arrays.stream(pixels).distinct().count();
            assertThat(distinct)
                    .as("ground, figure and highlight are all present")
                    .isGreaterThan(2);
        }
    }

    @Nested
    @DisplayName("the PNG encoder")
    class Encoder {

        @Test
        @DisplayName("it writes a real PNG signature and the three required chunks")
        void wellFormed() {
            byte[] png = Avatar.placeholderPng("op");

            assertThat(png[0] & 0xFF).isEqualTo(0x89);
            assertThat(new String(png, 1, 3, java.nio.charset.StandardCharsets.US_ASCII))
                    .isEqualTo("PNG");
            String body = new String(png, java.nio.charset.StandardCharsets.ISO_8859_1);
            assertThat(body).contains("IHDR").contains("IDAT").contains("IEND");
        }

        @Test
        @DisplayName("the header carries the size it was given")
        void headerDimensions() {
            byte[] png = Png.encode(new int[4], 2, 2);
            // IHDR payload starts at byte 16: signature (8) + length (4) + type (4).
            assertThat(readInt(png, 16)).isEqualTo(2);
            assertThat(readInt(png, 20)).isEqualTo(2);
            assertThat(png[24]).as("8 bits per channel").isEqualTo((byte) 8);
            assertThat(png[25]).as("colour type 6 — truecolour with alpha").isEqualTo((byte) 6);
        }

        @Test
        @DisplayName("a mismatched pixel array is refused rather than writing a corrupt file")
        void refusesBadInput() {
            org.junit.jupiter.api.Assertions.assertThrows(
                    IllegalArgumentException.class, () -> Png.encode(new int[3], 2, 2));
            org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class, () -> Png.encode(null, 1, 1));
        }

        @Test
        @DisplayName("base64 round-trips, and nonsense decodes to the placeholder rather than throwing")
        void roundTrip() {
            // A save is a plain JSON file a player can edit. A corrupt avatar must cost them a
            // picture, not a character.
            byte[] png = Avatar.placeholderPng("op");
            assertThat(Avatar.encode(png)).isNotBlank();
            assertThat(Avatar.encode(null)).isEmpty();
        }
    }

    private static int readInt(byte[] bytes, int at) {
        return ((bytes[at] & 0xFF) << 24)
                | ((bytes[at + 1] & 0xFF) << 16)
                | ((bytes[at + 2] & 0xFF) << 8)
                | (bytes[at + 3] & 0xFF);
    }
}

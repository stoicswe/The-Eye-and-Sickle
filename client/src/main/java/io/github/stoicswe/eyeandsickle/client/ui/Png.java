package io.github.stoicswe.eyeandsickle.client.ui;

import java.io.ByteArrayOutputStream;
import java.util.zip.CRC32;
import java.util.zip.Deflater;

/**
 * A minimal PNG encoder — enough to save one small square image and nothing more.
 *
 * <h2>Why this exists rather than a dependency</h2>
 *
 * JavaFX cannot write an image. The usual answer is {@code SwingFXUtils} plus {@code ImageIO}, which
 * means adding {@code javafx-swing} and dragging {@code java.desktop} into a client that has no
 * other use for either. PNG's container is genuinely simple — a signature, three chunks, and a
 * zlib stream — so encoding one costs less than the dependency does.
 *
 * <p>The other reason is testability. This takes an {@code int[]} and returns a {@code byte[]}, so
 * the format work is checkable without a toolkit; a {@code SwingFXUtils} path could only be tested
 * by starting one.
 *
 * <h2>⚠ Deliberately not a general encoder</h2>
 *
 * Truecolour with alpha, 8 bits a channel, no interlacing, filter type 0 on every row. A real
 * encoder chooses a filter per row and saves a third of the size; an avatar is ninety-six pixels
 * square and the choice is not worth the code. If this file grows a filter heuristic, the dependency
 * was the better trade after all.
 */
public final class Png {

    private Png() {}

    private static final byte[] SIGNATURE = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n'};

    /**
     * Encodes ARGB pixels, row-major, as a PNG.
     *
     * @param argb one packed {@code 0xAARRGGBB} per pixel, {@code width * height} long
     */
    public static byte[] encode(int[] argb, int width, int height) {
        if (argb == null || width <= 0 || height <= 0 || argb.length < width * height) {
            throw new IllegalArgumentException("pixel array does not match " + width + "x" + height);
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(SIGNATURE);

        // IHDR: width, height, bit depth 8, colour type 6 (truecolour + alpha), then three zeroes
        // for the compression, filter and interlace methods, all of which PNG defines exactly one
        // valid value for.
        ByteArrayOutputStream header = new ByteArrayOutputStream();
        writeInt(header, width);
        writeInt(header, height);
        header.write(8);
        header.write(6);
        header.write(0);
        header.write(0);
        header.write(0);
        chunk(out, "IHDR", header.toByteArray());

        // ⚠ Every row is preceded by a filter-type byte. Forgetting it produces a file that decodes
        // as a diagonal smear rather than as an error — the first pixel of each row is eaten as the
        // filter byte and everything shifts by one.
        byte[] raw = new byte[height * (1 + width * 4)];
        int at = 0;
        for (int y = 0; y < height; y++) {
            raw[at++] = 0;
            for (int x = 0; x < width; x++) {
                int pixel = argb[y * width + x];
                raw[at++] = (byte) ((pixel >> 16) & 0xFF);
                raw[at++] = (byte) ((pixel >> 8) & 0xFF);
                raw[at++] = (byte) (pixel & 0xFF);
                raw[at++] = (byte) ((pixel >> 24) & 0xFF);
            }
        }
        chunk(out, "IDAT", deflate(raw));
        chunk(out, "IEND", new byte[0]);
        return out.toByteArray();
    }

    private static byte[] deflate(byte[] data) {
        Deflater deflater = new Deflater(Deflater.BEST_COMPRESSION);
        deflater.setInput(data);
        deflater.finish();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        while (!deflater.finished()) {
            out.write(buffer, 0, deflater.deflate(buffer));
        }
        deflater.end();
        return out.toByteArray();
    }

    /** Length, type, payload, CRC — and ⚠ the CRC covers the TYPE as well as the payload. */
    private static void chunk(ByteArrayOutputStream out, String type, byte[] data) {
        writeInt(out, data.length);
        byte[] typeBytes = type.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        out.writeBytes(typeBytes);
        out.writeBytes(data);

        CRC32 crc = new CRC32();
        crc.update(typeBytes);
        crc.update(data);
        writeInt(out, (int) crc.getValue());
    }

    /** Big-endian, which is PNG's byte order throughout. */
    private static void writeInt(ByteArrayOutputStream out, int value) {
        out.write((value >>> 24) & 0xFF);
        out.write((value >>> 16) & 0xFF);
        out.write((value >>> 8) & 0xFF);
        out.write(value & 0xFF);
    }
}

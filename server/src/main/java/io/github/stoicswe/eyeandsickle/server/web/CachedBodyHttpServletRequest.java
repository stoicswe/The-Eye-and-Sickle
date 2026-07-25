package io.github.stoicswe.eyeandsickle.server.web;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * A request whose body has been read once into memory and can be replayed.
 *
 * <p>Validating an inbound {@code Content-Digest} means hashing the whole request body — but the
 * handler downstream still needs to read that same body. A servlet input stream is single-pass, so
 * without buffering, one reader starves the other. This wrapper reads the body eagerly in {@link
 * ContentDigestFilter}, hands the filter the bytes to check, and then serves the handler a fresh
 * stream over the same buffer.
 *
 * <p>The buffer is bounded by the filter before this wrapper is constructed, so an over-large body is
 * rejected before it can be held in memory.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request, byte[] body) {
        super(request);
        this.body = body;
    }

    /** The buffered body, for digest validation. */
    byte[] body() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        ByteArrayInputStream source = new ByteArrayInputStream(body);
        return new ServletInputStream() {
            @Override
            public boolean isFinished() {
                return source.available() == 0;
            }

            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setReadListener(ReadListener readListener) {
                // Blocking reads only; this wrapper never operates in async mode.
                throw new UnsupportedOperationException("Async reads are not supported on a cached body");
            }

            @Override
            public int read() {
                return source.read();
            }
        };
    }

    @Override
    public BufferedReader getReader() {
        Charset charset =
                getCharacterEncoding() == null ? StandardCharsets.UTF_8 : Charset.forName(getCharacterEncoding());
        return new BufferedReader(new InputStreamReader(new ByteArrayInputStream(body), charset));
    }

    @Override
    public int getContentLength() {
        return body.length;
    }

    @Override
    public long getContentLengthLong() {
        return body.length;
    }

    static byte[] readAll(HttpServletRequest request) throws IOException {
        return request.getInputStream().readAllBytes();
    }
}

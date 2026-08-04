package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;

/**
 * One candle.
 *
 * @param openedAt when the interval began
 * @param open first price in it
 * @param high highest
 * @param low lowest
 * @param close last price — for the newest candle this is the current mid, because it is still
 *     forming. A finished candle here would make the chart lag a whole interval behind the price the
 *     order form is quoting.
 * @param volume how much traded
 */
public record ShadowCandle(
        Instant openedAt, BigInteger open, BigInteger high, BigInteger low, BigInteger close, long volume) {

    /** Whether it closed at or above its open — which is the only thing the colour may mean. */
    public boolean up() {
        return close.compareTo(open) >= 0;
    }
}

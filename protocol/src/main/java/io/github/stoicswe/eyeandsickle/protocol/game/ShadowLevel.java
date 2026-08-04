package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;

/**
 * One resting order in the book, and who is behind it.
 *
 * <h2>⚠ The RATING travels with the price, because it is part of the price</h2>
 *
 * A well-rated seller asks more and delivers; a shady one undercuts and might not. Sending the
 * prices without the standing would leave the client rendering a list where the best row is simply
 * the best row — which is the one reading of this book that is wrong.
 *
 * @param price what they are offering
 * @param size how many
 * @param handle what they call themselves
 * @param standing {@code trusted}, {@code known}, {@code unrated} or {@code shady}
 * @param fillPercent how often they actually deliver
 * @param mine whether this is the player's own resting order
 */
public record ShadowLevel(
        BigInteger price, long size, String handle, String standing, int fillPercent, boolean mine) {}

package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;

/**
 * A trade that happened.
 *
 * @param at when
 * @param price at what
 * @param size how many
 * @param buyerTaker whether the buyer crossed the spread — which is what makes a print read as
 *     pressure rather than as a number
 * @param handle who took it
 * @param mine whether the player was on one side of it
 */
public record ShadowPrint(
        Instant at, BigInteger price, long size, boolean buyerTaker, String handle, boolean mine) {}

package io.github.stoicswe.eyeandsickle.protocol.game;

import java.math.BigInteger;
import java.time.Instant;

/**
 * One of the player's own orders.
 *
 * @param orderId what cancel names
 * @param itemType which listing
 * @param displayName what to call it
 * @param buy true to buy, false to sell
 * @param limitPrice the price it rests at
 * @param quantity how many are still working
 * @param placedAt when it went in
 * @param escrowWei ethecoin held against a buy — ⚠ shown because it is money the player cannot
 *     spend, and a balance that silently excludes it reads as a balance that is wrong
 */
public record ShadowOrder(
        String orderId,
        String itemType,
        String displayName,
        boolean buy,
        BigInteger limitPrice,
        int quantity,
        Instant placedAt,
        BigInteger escrowWei) {}

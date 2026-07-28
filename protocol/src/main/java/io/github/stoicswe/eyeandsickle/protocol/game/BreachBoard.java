package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * The state of whichever minigame this layer is running.
 *
 * <h2>Sealed, so a snapshot cannot carry a board nothing can draw</h2>
 *
 * A view switches over this to pick a widget. Sealing it means that switch is exhaustive at compile
 * time: adding a third puzzle class without adding a renderer for it is a build failure rather than a
 * blank panel in the middle of somebody's breach.
 *
 * <h2>⚠ A board carries only what the player may see</h2>
 *
 * The rule that governed the boards this replaced governs these too, and it is the reason the
 * engine's own state classes are not published directly. {@link MatrixBoard} publishes the grid and
 * the goals because {@code BREACH_PROTOCOL} is an open-information game — but not which cells the
 * <em>solver</em> would take. {@link OffsetBoard} publishes both hex rows because the arithmetic is
 * the game — but never the computed offsets, which are the answer.
 */
public sealed interface BreachBoard permits MatrixBoard, OffsetBoard {}

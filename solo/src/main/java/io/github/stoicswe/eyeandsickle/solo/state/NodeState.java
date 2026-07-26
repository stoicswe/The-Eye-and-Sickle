package io.github.stoicswe.eyeandsickle.solo.state;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * A machine the player has discovered.
 *
 * <p>Only discovered nodes exist here, and that rule is load-bearing rather than tidy: the virtual
 * namespace, tab completion and {@code ls /net/} are all built from this list, so a node that has not
 * been paid for cannot leak through any of them. {@code docs/client/04-terminology-and-education.md}
 * §3.2 and §3.6 both call this out as the least obvious way a client could accidentally hand the
 * player something recon is meant to sell them.
 */
public final class NodeState {

    public String address = "";
    public String label = "";

    /** How much the player has learned. Recon raises it; it never decreases. */
    public int reconLevel = 0;

    public Instant discoveredAt = Instant.now();

    /** Difficulty tier for a breach attempt against this node. */
    public int tier = 1;

    /** Miners the player has deployed here. Each costs the deployer a control channel. */
    public List<MinerState> deployedMiners = new ArrayList<>();

    /** Foreign miners discovered on this node — the four-response decision in {@code design/04} §5. */
    public boolean hostsForeignMiner = false;
}

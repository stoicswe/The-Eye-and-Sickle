package io.github.stoicswe.eyeandsickle.solo.state;

import java.util.ArrayList;
import java.util.List;

/**
 * The generated world: five to seven virtual servers, up to fifty machines each, and the player's own
 * rig sitting at one link from home's gateway.
 *
 * <h2>Written once, and then only mutated in place</h2>
 *
 * {@code TopologyGenerator.generate} is the only writer of this object's <em>shape</em>. It runs once,
 * at {@code newCharacter}, and refuses to run again — {@code NetRules} treats a non-null topology as
 * final. Everything after that is a flag flip on an existing {@link HostState}: discovered,
 * identified, foothold, looted, documentTaken. A world that could regenerate would be a world the
 * player could reroll, which is the same save-scumming failure {@code Rng}'s javadoc is written
 * against, one level up.
 *
 * <h2>⚠ Size discipline: this is rewritten every thirty seconds</h2>
 *
 * Worst case is 7 × 50 = 350 hosts; typical is around 160. The save file is written on a timer, so
 * every field added to {@link HostState} is multiplied by three hundred and fifty on every autosave.
 * That is the reason there is no per-host history, no per-host log and no cached derived value in
 * here: {@link HostState#signal} is derived at generation and the miner step-up is applied at read
 * time, hop distances are computed on demand, and the map the client draws is built fresh from
 * {@code knownNodes} rather than persisted.
 *
 * <h2>The vantage, and why traversal is repositioning</h2>
 *
 * {@link #vantageAddress} is where sweeps are run <em>from</em>, and it is the reason a one-hop
 * ceiling is survivable across a seven-server world. Reach is fixed — 1 hop, or 2 with the Topology
 * Mapper schematic, and no purchase at any price moves it (Invariant I2). What the player can move is
 * their position: breach a host, take a foothold, {@code connect} to it, sweep again from there.
 * Position substitutes for reach, and it is earned rather than bought.
 */
public final class TopologyState {

    public List<ServerState> servers = new ArrayList<>();

    public List<HostState> hosts = new ArrayList<>();

    public String homeServerId = "";

    /**
     * The player's own rig.
     *
     * <p>{@code 10.0.0.1}, a real private-range address for a real interface. Loopback
     * {@code 127.0.0.1} was considered and rejected: loopback is by definition not reachable from
     * another host, and adjacency is the entire subject of this graph — an address that teaches the
     * player their machine cannot be reached from the network would be teaching something false about
     * the one machine they will look at most.
     */
    public String playerAddress = "10.0.0.1";

    /** Where the player is operating from right now. Moves only via a foothold — see the class note. */
    public String vantageAddress = "10.0.0.1";

    /**
     * Document ids recovered so far, in order.
     *
     * <p>Ids only; the prose lives in the client's resources. ⚠ Not the authority on <em>ordering</em>
     * when a fragment repeats — twelve ids are spread across up to 350 hosts, so two hosts can carry
     * the same one. {@code NetRules.documents} reads the hosts and orders by
     * {@link HostState#documentTakenAt}, which is correct for duplicates; this list stays as the flat
     * "which fragments have I seen at all" record.
     */
    public List<String> documents = new ArrayList<>();
}

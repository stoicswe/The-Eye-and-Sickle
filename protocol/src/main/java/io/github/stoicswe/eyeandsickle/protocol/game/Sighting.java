package io.github.stoicswe.eyeandsickle.protocol.game;

/**
 * One machine as the player knows it.
 *
 * <p>The name is chosen over {@code Node} or {@code Host} on purpose: this is not a machine, it is a
 * <em>report</em> of one. Everything on it is knowledge the player has paid for — with a sweep, with a
 * recon tool, or with a breach — and the discipline the whole network view rests on is that there is no
 * field here the truth could hide in. {@link BreachTarget} carries the same warning for the same reason,
 * and this record and that one are the two halves of "what recon has established": this one describes
 * where a machine sits, that one describes whether to open a breach on it.
 *
 * <h2>Undiscovered machines have no {@code Sighting}, and that is the encoding</h2>
 *
 * There is deliberately no {@code discovered} flag, and no dark or placeholder instance. A machine the
 * player has not detected is simply absent from {@link NetMap#sightings()} — the shell does not list it,
 * completion does not offer it, {@code Targets} produces no {@link BreachTarget} for it, and the graph
 * draws no cell where it is: no marker, no ellipsis, no "3 contacts nearby". Absence is the only encoding
 * of "not discovered" that cannot leak, because there is nothing to leak <em>from</em>. A boolean would
 * have put the machine's address, server and hop count on the wire and asked every renderer, forever, to
 * remember not to draw them.
 *
 * <p>{@link #kind} being {@link HostKind#UNKNOWN} is the <em>other</em> thing, and the two are worth
 * keeping straight: that is a machine the sweep did find, whose type the sweep is not licensed to name.
 * "Something is there and I do not know what" is honest and drawable; upgrading it to a named type is
 * what the 15 EC Passive Sniffer sells ({@code docs/design/07-recon-tools.md} §1).
 *
 * <h2>What is missing from this record, and why each absence is load-bearing</h2>
 *
 * <ul>
 *   <li><strong>No {@code defended}, no {@code firewallTier}, no loot figure.</strong> Those reach the
 *       player through {@link BreachTarget}, which already documents them as recon output rather than
 *       truth. Duplicating them here would give the same question two answers on the same screen, and
 *       the two would drift — the interesting half of {@code liveOrDormant} is that it reads
 *       {@link TargetState#DORMANT} both for a dormant machine and for one nobody has analysed, and a
 *       second copy would eventually be built without that subtlety.
 *   <li><strong>No detection roll.</strong> Whether a sweep finds a machine is decided by a value fixed
 *       when the world is generated and compared against the instrument's sensitivity; putting it on the
 *       wire would let a client compute exactly what a better sweep would find, which is the purchase
 *       decision the upgraded sweeps exist to sell. It is also the reason re-running the same sweep is
 *       not a re-roll, and a client that could see the number would be a client that could prove it.
 *   <li><strong>No {@code honeypot}.</strong> {@link #honeypotSuspected} is a suspicion and is named as
 *       one. {@code docs/design/07-recon-tools.md} §2 requires the Honeypot Detector to keep a
 *       false-negative rate — "a perfect detector removes the fear the traps exist to create" — so a
 *       truthful boolean would delete a schematic-gated item at the point of rendering. Same discipline
 *       as {@link BreachTarget#honeypotSuspected()}; the name carries the uncertainty so nobody later
 *       "cleans it up".
 * </ul>
 *
 * <h2>{@code hopsFromVantage}, not hops from the rig</h2>
 *
 * Distance is measured from wherever the player is currently operating, which is what makes a one-hop
 * horizon survivable across a whole world: traversal is repositioning. Breach a machine, take a foothold,
 * connect to it, and everything is renumbered from there. A player who cannot afford reach can walk to
 * it instead, and the same machine that was out of range at two hops is at one hop from the foothold next
 * to it. Reach is bought only with a schematic (Invariant I2); position is bought with skill.
 *
 * @param address where the machine is, and the join key everything else about it hangs off
 * @param label what to call it on screen
 * @param serverId which {@link ServerRef} it sits on
 * @param kind what it is, once a type-revealing tool has run; {@link HostKind#UNKNOWN} until then, which
 *     is the state a sweep alone leaves every machine in
 * @param tier how hard it is expected to be, on the one shared scale; {@code null} when there is none to
 *     state — the player's own rig has no difficulty, and neither has a machine whose tier recon has not
 *     established
 * @param signal how loud it is — one of the two inputs to whether a sweep finds it, the other being the
 *     instrument
 * @param hopsFromVantage distance from where the player is operating right now, not from their rig;
 *     {@code 0} for the vantage itself
 * @param vantage whether this is where the player is operating from
 * @param foothold whether the player has breached it and may {@code connect} to it
 * @param looted whether its one-time payout has already been taken — loot is a stock, not a flow
 * @param honeypotSuspected whether this looks like a trap; a suspicion, never a finding
 * @param hostsDeployedMiner whether a miner is known to be running on it
 * @param documentAvailable whether there is something here worth reading and the player has not taken it
 * @param bridgePeerServerName the <em>server</em> on the far side of a bridge; {@code ""} otherwise. Never
 *     an address, never a host count — a bridge's published function is to name the network on the other
 *     side, and anything past that is the far side's topology crossing a boundary it must not cross
 */
public record Sighting(
        String address,
        String label,
        String serverId,
        HostKind kind,
        DifficultyTier tier,
        SignalStrength signal,
        int hopsFromVantage,
        boolean vantage,
        boolean foothold,
        /**
         * Breached once, and shut out since — the host has been patched.
         *
         * <p>⚠ Distinct from {@code !foothold}, which is "never breached". A patched host is one the
         * player <em>did</em> get into and cannot any more, which is a different fact and a
         * different decision: the route it opened is closed, the intelligence it gave is stale, and
         * breaching it again is a known quantity rather than a gamble.
         *
         * <p>⚠ <b>Nothing sets this true yet.</b> No rule patches a host — see
         * {@code docs/design/15-open-questions.md}, where the mechanic is proposed rather than
         * decided. The field and its rendering exist so the state has one meaning the day it does,
         * rather than being invented twice in two places.
         */
        boolean patched,
        boolean looted,
        boolean honeypotSuspected,
        boolean hostsDeployedMiner,
        boolean documentAvailable,
        String bridgePeerServerName,
        /**
         * Whether a scan has ever come back from this machine — what the list's {@code [i]} marks.
         *
         * <p>⚠ "There is a file", not "the file is complete". A machine scanned once for its firewall
         * and a machine taken apart down to its vault both carry the marker, because the marker's job
         * is to say <em>there is something to open</em>. How much is in it is the report's own first
         * line, which is the right place for it — a marker that tried to carry completeness would need
         * seven states and would be read as none of them.
         */
        boolean reported) {

    /**
     * The reading without a patch state — every producer that has one today.
     *
     * <h2>⚠ A convenience constructor, and a deliberate one</h2>
     *
     * {@link #patched} was added on 2026-07-27 for a mechanic that <b>does not exist yet</b>: nothing
     * in the engine patches a host. Threading a literal {@code false} through every producer and
     * every fixture to say "no rule has run" would be noise at fourteen call sites, and the reader
     * of each one would have to work out which boolean it was.
     *
     * <p>⚠ It defaults to {@code false}, which is not merely the safe value — it is the <b>true</b>
     * one. A host nobody has locked the player out of is not patched, and the day something can
     * patch one, that producer uses the canonical constructor and this keeps meaning what it says.
     */
    public Sighting(
            String address,
            String label,
            String serverId,
            HostKind kind,
            DifficultyTier tier,
            SignalStrength signal,
            int hopsFromVantage,
            boolean vantage,
            boolean foothold,
            boolean looted,
            boolean honeypotSuspected,
            boolean hostsDeployedMiner,
            boolean documentAvailable,
            String bridgePeerServerName) {
        this(
                address,
                label,
                serverId,
                kind,
                tier,
                signal,
                hopsFromVantage,
                vantage,
                foothold,
                false,
                looted,
                honeypotSuspected,
                hostsDeployedMiner,
                documentAvailable,
                bridgePeerServerName,
                false);
    }

    /**
     * The reading without a report flag — every fixture and test that has no scan behind it.
     *
     * <p>Same reasoning as the overload above: {@code reported} was added on 2026-07-29 and a literal
     * {@code false} threaded through every producer would be noise the reader of each call site has to
     * decode. It defaults to the true value — a machine nobody has scanned has no file.
     */
    public Sighting(
            String address,
            String label,
            String serverId,
            HostKind kind,
            DifficultyTier tier,
            SignalStrength signal,
            int hopsFromVantage,
            boolean vantage,
            boolean foothold,
            boolean patched,
            boolean looted,
            boolean honeypotSuspected,
            boolean hostsDeployedMiner,
            boolean documentAvailable,
            String bridgePeerServerName) {
        this(
                address,
                label,
                serverId,
                kind,
                tier,
                signal,
                hopsFromVantage,
                vantage,
                foothold,
                patched,
                looted,
                honeypotSuspected,
                hostsDeployedMiner,
                documentAvailable,
                bridgePeerServerName,
                false);
    }

    public Sighting {
        address = address == null ? "" : address;
        label = label == null ? "" : label;
        serverId = serverId == null ? "" : serverId;

        // UNKNOWN and LOW are the readings that claim least, so they are what a producer that said
        // nothing gets. Defaulting kind to anything else would invent a type the player never bought,
        // and defaulting signal upward would make an unstated machine look easier to find than it is.
        kind = kind == null ? HostKind.UNKNOWN : kind;
        signal = signal == null ? SignalStrength.LOW : signal;
        bridgePeerServerName = bridgePeerServerName == null ? "" : bridgePeerServerName;

        // tier is deliberately NOT defaulted. DifficultyTier's scale starts at 1 and has no "unknown"
        // member, so any default would be a difficulty claim about a machine nobody has assessed — and
        // the cheapest one to invent (tier 1) is the claim most likely to get a player killed.

        if (hopsFromVantage < 0) {
            throw new IllegalArgumentException("hops must be >= 0");
        }
        // A non-bridge naming a peer server is how the far side's topology starts leaking: the name is
        // the one fact a bridge is allowed to publish, and it is allowed because a bridge advertising
        // the network it links to is what a bridge is for. On anything else it is a fact the player has
        // no instrument for.
        if (!bridgePeerServerName.isEmpty() && kind != HostKind.BRIDGE) {
            throw new IllegalArgumentException("only a BRIDGE may name a peer server");
        }
    }
}

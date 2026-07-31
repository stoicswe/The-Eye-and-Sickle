package io.github.stoicswe.eyeandsickle.solo.net;

import io.github.stoicswe.eyeandsickle.protocol.game.ComputeConsumer;
import io.github.stoicswe.eyeandsickle.solo.Balance;
import io.github.stoicswe.eyeandsickle.solo.fs.VirtualFs;
import io.github.stoicswe.eyeandsickle.solo.rules.ComputeRules;
import io.github.stoicswe.eyeandsickle.solo.state.AllocationState;
import io.github.stoicswe.eyeandsickle.solo.state.HostState;
import io.github.stoicswe.eyeandsickle.solo.state.SessionState;
import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Opening, holding and closing shell sessions on machines.
 *
 * <h2>⚠ A session is not the vantage, and this class never touches the vantage</h2>
 *
 * {@link NetRules#connect} moves the vantage — the single point a sweep measures hop distance from,
 * and a hard ceiling no purchase moves (Invariant <b>I2</b>). Opening a session does not, and nothing
 * here reads or writes {@code vantageAddress}. A player may sit on eight machines and still sweep
 * from exactly one, which is both how remote access actually works and the only arrangement in which
 * reach is not silently multiplied by how many windows are open.
 *
 * <h2>What a session costs, and why it costs anything</h2>
 *
 * {@link Balance#SESSION_CYCLES} while open, charged to {@link ComputeConsumer#SHELL_SESSION}. The
 * point is not the two cycles; it is that "how many machines can I be on at once" gets answered by
 * the rig rather than by a constant in a view. {@code docs/design/00} §4's meta-rule is that compute
 * is the master scarcity, and a free unlimited resource sitting next to it is exactly the kind of
 * thing that quietly stops being a trade-off.
 *
 * <h2>A foothold is required, and the refusal says so</h2>
 *
 * You cannot open a shell on a machine you have not broken into — that is what breaking in is
 * <em>for</em> ({@code docs/design/05}). The player's own rig is always available and is not a
 * foothold. Refusals come back empty with a reason the caller words, matching every other rule in
 * this engine: a rules class that threw on an unaffordable action would be deciding how the client
 * reports it.
 */
public final class SessionRules {

    private SessionRules() {}

    /** Why a session could not be opened. {@code null} in {@link Opened#refusal} means it was. */
    public enum Refusal {
        /** The address is not a machine this player has discovered. */
        UNKNOWN_HOST,

        /** Discovered, but not broken into. */
        NO_FOOTHOLD,

        /** The rig cannot spare the cycles. */
        NOT_ENOUGH_COMPUTE
    }

    /** The outcome of an open: the session, or the reason there is none. */
    public record Opened(SessionState session, Refusal refusal) {

        public boolean succeeded() {
            return session != null;
        }

        static Opened refused(Refusal refusal) {
            return new Opened(null, refusal);
        }
    }

    /**
     * Opens a session, or says why not.
     *
     * <p>Idempotent: asking for a session that is already open returns the existing one and reserves
     * nothing. That is what makes it safe for a view to call this on every click of a CONNECT
     * control — the alternative is a player who double-clicks paying twice and holding two
     * allocations for one window.
     *
     * @param now the session clock. ⚠ Never {@code Instant.now()} — the same warning
     *     {@code NetRules.beginSweep} and {@code RunningTask} carry, for the same reason
     */
    public static Opened open(SoloSave save, String address, Instant now) {
        String wanted = address == null ? "" : address.trim();
        if (save == null || wanted.isEmpty()) {
            return Opened.refused(Refusal.UNKNOWN_HOST);
        }
        Optional<SessionState> already = find(save, wanted);
        if (already.isPresent()) {
            return new Opened(already.get(), null);
        }
        boolean self = wanted.equals(NetRules.vantageAddress(save)) && isOwnRig(save, wanted);
        if (!self) {
            HostState host = host(save, wanted);
            if (host == null || !host.discovered) {
                return Opened.refused(Refusal.UNKNOWN_HOST);
            }
            if (!host.foothold) {
                return Opened.refused(Refusal.NO_FOOTHOLD);
            }
        }
        AllocationState allocation = ComputeRules.reserve(
                save.rig, ComputeConsumer.SHELL_SESSION, "shell " + wanted, Balance.SESSION_CYCLES);
        if (allocation == null) {
            return Opened.refused(Refusal.NOT_ENOUGH_COMPUTE);
        }
        allocation.startedAt = now;

        SessionState session = new SessionState();
        session.address = wanted;
        session.openedAt = now;
        session.allocationId = allocation.allocationId;
        session.cycles = Balance.SESSION_CYCLES;
        // Sessions land in the machine's own operator home rather than at `/`, because that is where
        // a real login puts you and because it is where anything worth finding on a host actually is.
        session.cwd = VirtualFs.home(isOwnRig(save, wanted) ? save.handle : VirtualFs.hostUser(host(save, wanted)));
        save.sessions.add(session);
        return new Opened(session, null);
    }

    /**
     * Closes a session and gives its cycles straight back.
     *
     * <p>⚠ <b>Released, not put into thermal recovery</b>, and that is a deliberate difference from
     * how a scan or a sweep ends. Recovery is the cost of having driven the silicon hard
     * ({@code docs/design/01} §1.3); an idle shell has driven nothing. Charging recovery here would
     * mean closing a window you opened by mistake cost you real capacity for real minutes, which
     * would teach players to leave sessions open — the exact opposite of what the hold is for.
     *
     * @return whether there was a session to close
     */
    public static boolean close(SoloSave save, String address) {
        Optional<SessionState> session = find(save, address);
        if (session.isEmpty()) {
            return false;
        }
        ComputeRules.release(save.rig, session.get().allocationId);
        save.sessions.remove(session.get());
        return true;
    }

    /**
     * Drops sessions on machines the player no longer holds, releasing their compute.
     *
     * <p>A foothold can be lost while a window is open — a host gets patched, or the player is
     * pushed off it. Without this the session would sit there holding cycles against a machine that
     * would refuse every command, and the player would have a window that had quietly become a
     * two-cycle leak. Called on load and after anything that can revoke a foothold.
     *
     * @return the addresses that were dropped, so the caller can log them by name
     */
    public static List<String> prune(SoloSave save) {
        if (save == null) {
            return List.of();
        }
        List<String> dropped = new java.util.ArrayList<>();
        for (SessionState session : List.copyOf(save.sessions)) {
            if (isOwnRig(save, session.address)) {
                continue;
            }
            HostState host = host(save, session.address);
            if (host == null || !host.foothold) {
                ComputeRules.release(save.rig, session.allocationId);
                save.sessions.remove(session);
                dropped.add(session.address);
            }
        }
        return List.copyOf(dropped);
    }

    /** Moves a session's working directory. Refuses silently for a path that is not a directory. */
    public static boolean changeDirectory(SoloSave save, String address, String path, Instant now) {
        Optional<SessionState> session = find(save, address);
        if (session.isEmpty()) {
            return false;
        }
        String target = VirtualFs.resolve(session.get().cwd, path);
        // ⚠ The real item list, not an empty one. A vault holding two items and a vault holding
        // none are different directories, and passing List.of() here made `cd ~/.VaultStore/vault`
        // refuse whenever the tier happened to be empty — a directory that exists, refusing to be
        // entered, with the message "No such file or directory".
        boolean ok = isOwnRig(save, address)
                ? target.equals("/")
                        || !VirtualFs.listRig(target, save.handle, installed(save), 0, List.of(), List.of(), now)
                                .isEmpty()
                        || VirtualFs.listRig(
                                        VirtualFs.parentOf(target),
                                        save.handle,
                                        installed(save),
                                        0,
                                        List.of(),
                                        List.of(),
                                        now)
                                .stream()
                                .anyMatch(e -> e.path().equals(target) && e.directory())
                : VirtualFs.isDirectory(host(save, address), target, now);
        if (!ok) {
            return false;
        }
        session.get().cwd = target;
        return true;
    }

    /** Owned items with their tiers — what the rig's own listing needs. See {@code AccessLog}. */
    private static List<VirtualFs.Installed> installed(SoloSave save) {
        return save.items.stream()
                .map(i -> new VirtualFs.Installed(i.itemType, i.displayName, i.tier, i.equipped))
                .toList();
    }

    public static Optional<SessionState> find(SoloSave save, String address) {
        if (save == null || address == null) {
            return Optional.empty();
        }
        return save.sessions.stream()
                .filter(s -> s.address.equals(address.trim()))
                .findFirst();
    }

    public static List<SessionState> all(SoloSave save) {
        return save == null ? List.of() : List.copyOf(save.sessions);
    }

    /** Whether an address is the player's own machine, which is always reachable. */
    public static boolean isOwnRig(SoloSave save, String address) {
        HostState host = host(save, address);
        return host != null && "SELF".equalsIgnoreCase(host.kind);
    }

    /** The world's record for an address, or null. Public because the engine's facade needs it. */
    public static HostState host(SoloSave save, String address) {
        if (save == null || save.topology == null || address == null) {
            return null;
        }
        for (HostState host : save.topology.hosts) {
            if (host.address.equals(address.trim())) {
                return host;
            }
        }
        return null;
    }
}

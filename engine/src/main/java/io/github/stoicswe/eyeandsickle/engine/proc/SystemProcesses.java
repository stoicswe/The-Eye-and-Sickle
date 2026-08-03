package io.github.stoicswe.eyeandsickle.engine.proc;

import java.util.List;

/**
 * The daemons and kernel threads the rig runs whether or not the player asked it to.
 *
 * <h2>They exist so the parasite has somewhere to stand</h2>
 *
 * A process table containing only the player's own tools is a table with nothing to hide in: any row
 * the player did not start is the parasite, and the audit is a single glance forever. These rows are
 * the haystack. They also make the table read like a machine rather than a task list, which is the
 * whole reason {@code docs/client/04-terminology-and-education.md} lets this client use real
 * vocabulary — a player who learns to read this here can read {@code ps} outside.
 *
 * <h2>⚠ The rig runs a FreeBSD-shaped system, and these are FreeBSD's names</h2>
 *
 * <b>This reverses an earlier call in this file, deliberately.</b> The names used to be invented, on
 * the reasoning that a row called {@code launchd} would be teaching the player the rig is a Mac —
 * which it is not, and {@code docs/education/00-curriculum-and-method.md} treats a wrong mapping as
 * worse than no mapping. The premise was the part that was wrong: uOS is <b>FreeBSD-flavoured</b>, so
 * FreeBSD's process table is the correct one and inventing names was teaching nothing where it could
 * have been teaching something true.
 *
 * <p>Three FreeBSD conventions come with it, and all three transfer to a real machine:
 *
 * <ul>
 *   <li><b>Kernel threads are bracketed</b> — {@code [pagedaemon]}, {@code [g_up]}. A player learns
 *       at a glance which rows are the kernel's and which are userland's.
 *   <li><b>pid 0 is the kernel and pid 1 is {@code init}</b>, and everything else descends from one
 *       of them.
 *   <li><b>Service accounts are real names, not a convention</b> — {@code daemon}, {@code operator},
 *       {@code nobody}, {@code unbound}, {@code _dhcp}. Which is what makes a mimic's invented
 *       one-off account stand out in the USER column.
 * </ul>
 *
 * <p>⚠ A handful of rows are marked {@code uOS} rather than FreeBSD, because they are the fiction's
 * own — the compute ledger, the vault, the provenance signer. They are kept apart from the real ones
 * in {@link Daemon#real} so that nothing here quietly asserts FreeBSD ships a {@code cyclesd}. If any
 * of this ever becomes a curriculum entry, only the {@code real} rows may be cited, and they will
 * need the usual {@code verified:} line.
 *
 * <h2>⚠ Restartable, never killable</h2>
 *
 * The rig needs them. Offering {@code kill} would mean either a rig the player can brick or a refusal
 * dressed as a menu item, and both are worse than an honest {@code restart}. Restarting is not free:
 * anything depending on the daemon goes with it ({@link Daemon#provides}), which is the cost that
 * makes the choice interesting when a player suspects one of these rows and is not sure.
 */
public final class SystemProcesses {

    private SystemProcesses() {}

    /**
     * One system process.
     *
     * @param name the command, as {@code ps} would print it — bracketed for a kernel thread
     * @param user the account it runs as. Only real, repeated accounts appear here, which is what
     *     makes a mimic's one-off account a tell
     * @param pid small and stable — these start at boot, and a five-figure "daemon" did not
     * @param threads a plausible resting count; {@link ProcessTable} wanders it slowly from here
     * @param kernel a kernel thread: no disk, no network, almost no resident memory. Modelling that
     *     honestly is what makes a parasite claiming to be one conspicuous on three tabs at once
     * @param provides the facility a user tool may depend on, or {@code ""} for a leaf. Restarting a
     *     daemon takes down every running tool that names it
     * @param real whether FreeBSD genuinely ships this. False for the fiction's own daemons; see the
     *     class note on why the distinction is recorded rather than assumed
     * @param blurb the row's one-line detail
     */
    public record Daemon(
            String name,
            String user,
            int pid,
            int threads,
            boolean kernel,
            String provides,
            boolean real,
            String blurb) {}

    /**
     * The facility a network tool needs. A sweep is packets on somebody else's machine, and the thing
     * that puts them there is this.
     *
     * <p>⚠ Carried by {@code netd}, which is one of the fiction's own. Real FreeBSD has no single
     * networking daemon — the stack is in the kernel — so there was no honest real name to hang this
     * on, and inventing a plausible-sounding FreeBSD one would have been exactly the wrong mapping
     * the class note warns about.
     */
    public static final String FACILITY_NET = "net";

    /** The facility an audit needs. Carried by {@code auditd}, which FreeBSD genuinely ships. */
    public static final String FACILITY_AUDIT = "audit";

    private static final List<Daemon> DAEMONS = List.of(
            // ── the kernel and its threads ────────────────────────────────────────────────────
            new Daemon("[kernel]", "root", 0, 96, true, "", true, "the kernel itself"),
            new Daemon("[idle]", "root", 11, 8, true, "", true, "one thread per core, doing nothing"),
            new Daemon("[intr]", "root", 12, 24, true, "", true, "interrupt handling"),
            new Daemon("[pagedaemon]", "root", 3, 3, true, "", true, "reclaims pages when memory runs short"),
            new Daemon("[vmdaemon]", "root", 4, 1, true, "", true, "swaps whole processes out under pressure"),
            new Daemon("[bufdaemon]", "root", 6, 2, true, "", true, "flushes dirty buffers"),
            new Daemon("[syncer]", "root", 8, 1, true, "", true, "writes the filesystem out on a timer"),
            new Daemon("[vnlru]", "root", 7, 1, true, "", true, "recycles unused vnodes"),
            new Daemon("[rand_harvestq]", "root", 5, 1, true, "", true, "gathers entropy"),
            new Daemon("[g_event]", "root", 13, 1, true, "", true, "GEOM: the storage layer's event thread"),
            new Daemon("[g_up]", "root", 14, 1, true, "", true, "GEOM: completions travelling up the stack"),
            new Daemon("[g_down]", "root", 15, 1, true, "", true, "GEOM: requests travelling down it"),
            new Daemon("[usb]", "root", 16, 6, true, "", true, "the USB bus threads"),
            new Daemon("[cam]", "root", 17, 4, true, "", true, "the storage transport layer"),

            // ── userland, FreeBSD's ───────────────────────────────────────────────────────────
            new Daemon("init", "root", 1, 1, false, "", true, "the first process; userland descends from it"),
            new Daemon("devd", "root", 254, 2, false, "", true, "reacts to hardware appearing and leaving"),
            new Daemon("syslogd", "root", 388, 1, false, "", true, "the system log socket"),
            new Daemon(
                    "auditd",
                    "root",
                    402,
                    3,
                    false,
                    FACILITY_AUDIT,
                    true,
                    "the audit trail — what a scan asks about other processes"),
            new Daemon("cron", "root", 441, 1, false, "", true, "runs things on a schedule"),
            new Daemon("sshd", "root", 468, 1, false, "", true, "listens for shells"),
            new Daemon("ntpd", "ntpd", 503, 2, false, "", true, "keeps the clock honest"),
            new Daemon("powerd", "root", 517, 1, false, "", true, "clocks the processor up and down"),
            new Daemon("dhclient", "_dhcp", 544, 1, false, "", true, "holds the interface's lease"),
            new Daemon("local_unbound", "unbound", 561, 2, false, "", true, "the caching resolver"),
            new Daemon("moused", "root", 578, 1, false, "", true, "the pointer"),
            new Daemon("getty", "root", 592, 1, false, "", true, "waits on a console that nobody uses"),
            new Daemon("casper", "root", 604, 2, false, "", true, "hands capabilities to sandboxed processes"),

            // ── userland, the fiction's own ───────────────────────────────────────────────────
            new Daemon("cyclesd", "operator", 631, 4, false, "", false, "keeps the compute ledger honest"),
            new Daemon("netd", "root", 648, 6, false, FACILITY_NET, false, "the interface a sweep sends through"),
            new Daemon("ledgerd", "operator", 662, 3, false, "", false, "writes the ethecoin ledger to disk"),
            new Daemon("vaultd", "operator", 679, 2, false, "", false, "the encrypted vault's mount"),
            new Daemon("provenanced", "daemon", 694, 3, false, "", false, "signs and checks item provenance"),
            new Daemon("attestd", "daemon", 711, 2, false, "", false, "holds the rig's key material"),
            new Daemon("syspolicyd", "root", 728, 4, false, "", false, "decides what is allowed to run"),
            new Daemon("pulsed", "operator", 745, 2, false, "", false, "drives the console's own refresh"));

    public static List<Daemon> all() {
        return DAEMONS;
    }

    /**
     * A name worth typosquatting.
     *
     * <p>⚠ Only <b>userland</b> names are eligible, and only ones long enough to hide a swapped
     * character in. A bracketed kernel thread is the wrong victim twice over: the brackets are a
     * shape a player reads before the letters, and a userland process claiming to be a kernel one is
     * given away by the brackets alone rather than by the typo.
     *
     * <p>Every candidate is <b>in {@link #DAEMONS}</b>, which is the mechanic rather than a
     * convenience: a typosquat is findable because the thing it is imitating is sitting in the same
     * table, so sorting by name lands the two next to each other. Squatting a daemon the rig does not
     * run would be an unfalsifiable name — not a disguise, a riddle.
     */
    public static String squattableName(int seed) {
        List<String> eligible = DAEMONS.stream()
                .map(Daemon::name)
                .filter(name -> !name.startsWith("["))
                .filter(name -> name.length() >= 7)
                .toList();
        return eligible.get(Math.floorMod(seed, eligible.size()));
    }

    /** Whether any daemon runs under this account — the check behind the mimic's tell. */
    public static boolean isKnownUser(String user) {
        return DAEMONS.stream().anyMatch(daemon -> daemon.user().equals(user));
    }
}

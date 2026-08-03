package io.github.stoicswe.eyeandsickle.engine.fs;

import io.github.stoicswe.eyeandsickle.protocol.game.FsEntry;
import io.github.stoicswe.eyeandsickle.protocol.game.FsKind;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /System} — the base operating system, laid out as FreeBSD lays one out.
 *
 * <h2>⚠ Nothing in here opens, and that is the design rather than a limitation</h2>
 *
 * Every entry is {@code readable = false}. A player can see the whole shape of an operating system —
 * where its libraries are, where its boot loader lives, which directory holds the things nobody
 * packaged — and cannot read a byte of any of it. What they get instead is the <b>manual</b>:
 * {@code man hier} and the pages beside it explain what each of these is for.
 *
 * <p>That is deliberate on two counts. It is honest — a game cannot ship a real kernel, and a
 * {@code /System/kernel} that printed invented bytes would be teaching something false about the one
 * subject this tree exists to teach. And it is better teaching: a directory you cannot open but can
 * look up is a directory you end up reading <em>about</em>, which is how anybody actually learns a
 * filesystem hierarchy.
 *
 * <h2>Why FreeBSD and not Linux</h2>
 *
 * uOS's root is macOS-shaped ({@code /Applications}, {@code /Library}, {@code /System},
 * {@code /Users}) and its base system is FreeBSD-shaped, which is not an arbitrary pairing: macOS's
 * own userland descends from FreeBSD, so the combination is the one real system this layout is
 * closest to. FreeBSD also has the single most teachable property in this area —
 *
 * <p><b>the base system and third-party software are separate things.</b> Everything under
 * {@code /bin}, {@code /sbin}, {@code /lib}, {@code /usr/bin} and {@code /usr/share} is developed,
 * versioned and shipped as one coherent whole; anything installed afterwards goes in
 * {@code /usr/local} and nowhere else. Linux has no such line, and a player who learns it here has
 * learned the thing that most distinguishes the two families.
 *
 * <h2>⚠ Every claim in {@link #NOTES} is a real-world claim</h2>
 *
 * {@code CLAUDE.md}: never state a real-world fact you have not checked. The notes below describe
 * FreeBSD's {@code hier(7)} layout, which is stable across releases and is what the shipped
 * {@code hier(7)} page cites. Anything version-specific to FreeBSD 15 in particular is deliberately
 * <b>not</b> asserted here — the layout is the durable part, and a claim about one release is the
 * kind that quietly goes stale.
 */
public final class SystemTree {

    private SystemTree() {}

    /** The root of the base system. macOS's name for it; FreeBSD's contents. */
    public static final String ROOT = "/System";

    /**
     * Directory → one line on what it is for.
     *
     * <p>Ordered as {@code hier(7)} orders them, which is alphabetical by path — so a reader who
     * goes looking for the real page finds the same sequence.
     */
    private static final Map<String, String> NOTES = new LinkedHashMap<>();

    static {
        NOTES.put("bin", "User utilities fundamental to both single-user and multi-user modes.");
        NOTES.put(
                "boot",
                "What the machine needs before it has an operating system: the loader, "
                        + "the kernel, and its modules.");
        NOTES.put(
                "etc",
                "System configuration and scripts. On FreeBSD this is one file more than "
                        + "you expect: rc.conf turns the whole machine on and off.");
        NOTES.put("lib", "Libraries the programs in /bin and /sbin cannot start without.");
        NOTES.put("libexec", "System daemons and utilities run by other programs, not by people.");
        NOTES.put(
                "rescue",
                "Statically linked copies of the essential tools. They work when the "
                        + "shared libraries are gone, which is the only time you will ever want them.");
        NOTES.put("sbin", "System programs and administration utilities. The s is for system, not " + "for super.");
        NOTES.put(
                "usr",
                "The majority of user utilities and applications — and the boundary "
                        + "between the base system and everything else.");
        NOTES.put("var", "Files that change: logs, mail, spools, databases, run-time state.");
    }

    /** {@code /System/usr} — where the base/ports boundary actually is. */
    private static final Map<String, String> USR = new LinkedHashMap<>();

    static {
        USR.put("bin", "Common utilities that are part of the base system.");
        USR.put("include", "Standard C include files.");
        USR.put("lib", "Archive and shared libraries.");
        USR.put("libdata", "Miscellaneous utility data files.");
        USR.put("libexec", "System daemons and utilities executed by other programs.");
        USR.put(
                "local",
                "EVERYTHING NOT PART OF THE BASE SYSTEM. Ports and packages install "
                        + "here and only here. This one directory is the whole FreeBSD/Linux difference.");
        USR.put("sbin", "System daemons and utilities administrators run.");
        USR.put("share", "Architecture-independent files — documentation, timezone data, manuals.");
        USR.put(
                "src",
                "The source of the base system itself, which FreeBSD ships and expects you "
                        + "to be able to rebuild from.");
    }

    /** Files worth naming individually, because each one teaches something. */
    private static final Map<String, Long> FILES = new LinkedHashMap<>();

    static {
        FILES.put("/System/boot/loader.conf", 2_400L);
        FILES.put("/System/boot/kernel/kernel", 28_400_000L);
        FILES.put("/System/etc/rc.conf", 1_180L);
        FILES.put("/System/etc/defaults/rc.conf", 42_800L);
        FILES.put("/System/etc/fstab", 620L);
        FILES.put("/System/etc/passwd", 1_700L);
        FILES.put("/System/etc/master.passwd", 2_140L);
        FILES.put("/System/etc/group", 480L);
        FILES.put("/System/etc/hosts", 1_020L);
        FILES.put("/System/etc/resolv.conf", 140L);
        FILES.put("/System/etc/login.conf", 6_900L);
        FILES.put("/System/etc/motd", 380L);
        FILES.put("/System/libexec/ld-elf.so.1", 240_000L);
    }

    /**
     * Files that stay closed even to their owner, because the real thing is mode 0600.
     *
     * <p>⚠ This is the <b>real</b> reason, not a game one — and that is the point. Every FreeBSD
     * machine keeps its password hashes in {@code master.passwd} at mode 0600 and leaves a
     * world-readable {@code passwd} beside it with an asterisk where the hash would be. Meeting that
     * split by being refused is how a player learns why it exists.
     */
    public static final java.util.Set<String> MODE_RESTRICTED = java.util.Set.of("/System/etc/master.passwd");

    /**
     * What a system file actually says.
     *
     * <h2>⚠ Text configuration reads; binaries do not — which is exactly what a real machine does</h2>
     *
     * The earlier version of this class refused <b>everything</b>, on the argument that a game cannot
     * ship a real kernel. That argument is sound for {@code /boot/kernel/kernel} and wrong for
     * {@code /etc/rc.conf}: one is twenty-eight megabytes of machine code and the other is nine lines
     * of text anybody can read on their own laptop right now. Refusing both taught that an operating
     * system is a closed box, which is the opposite of what this tree is for.
     *
     * <p>So: config files return their real contents, in FreeBSD's real formats. Binaries return one
     * honest line saying what they are — which is also what happens if you {@code cat} one for real,
     * minus the terminal beeping at you.
     *
     * @return the lines, or empty when this path has no text behind it
     */
    public static List<String> contents(String path) {
        return CONTENTS.getOrDefault(VirtualFs.normalise(path), List.of());
    }

    /**
     * Whether a path is a FILE with no text behind it — a binary, a library, a kernel module.
     *
     * <h2>⚠ A directory is not a binary, and this used to say it was</h2>
     *
     * The check was "is a system path with no text contents", and a directory has no text contents,
     * so {@code /System/bin} came back as an ELF executable. It is not a subtle failure: the file
     * manager showed a folder described as a stripped x86-64 binary. Directories are excluded
     * explicitly, and the caller passes {@code directory} rather than this class guessing from the
     * path — a path cannot tell you what it is, which is the whole reason {@link FsKind} exists.
     */
    public static boolean isBinary(String path, boolean directory) {
        String p = VirtualFs.normalise(path);
        if (directory || !isSystem(p) || MODE_RESTRICTED.contains(p)) {
            return false;
        }
        return contents(p).isEmpty();
    }

    /**
     * Whether this path is a directory in the base system, without needing an {@link FsEntry}.
     *
     * <p>Answered by asking whether listing it produces anything — the same question the tree asks
     * itself everywhere else, rather than a second table of which names are folders.
     */
    public static boolean isDirectory(String path, Instant now) {
        String p = VirtualFs.normalise(path);
        return isSystem(p) && (p.equals(ROOT) || !list(p, true, now).isEmpty());
    }

    /**
     * ⚠ Real FreeBSD file formats. Every one of these is a factual claim.
     *
     * <p>{@code CLAUDE.md}: never state a real-world fact you have not checked. These are the
     * standard shapes — {@code rc.conf}'s {@code name="value"} lines, {@code fstab}'s six columns,
     * {@code passwd}'s seven colon-separated fields with an asterisk in the password position. The
     * <em>values</em> are this machine's; the <em>formats</em> are FreeBSD's, and the formats are the
     * part being taught.
     */
    private static final Map<String, List<String>> CONTENTS = new LinkedHashMap<>();

    static {
        CONTENTS.put(
                "/System/etc/rc.conf",
                List.of(
                        "# What this machine turns on at boot. One file; every service in it.",
                        "# Defaults live in /System/etc/defaults/rc.conf and are NOT edited.",
                        "",
                        "hostname=\"rig\"",
                        "ifconfig_em0=\"DHCP\"",
                        "sshd_enable=\"YES\"",
                        "ntpd_enable=\"YES\"",
                        "powerd_enable=\"YES\"",
                        "zfs_enable=\"YES\"",
                        "dumpdev=\"AUTO\""));

        CONTENTS.put(
                "/System/etc/defaults/rc.conf",
                List.of(
                        "# The defaults for every service the base system knows about.",
                        "# NOTE: do not edit this file. Override in /System/etc/rc.conf instead —",
                        "# an upgrade replaces this one and would take your changes with it.",
                        "",
                        "sshd_enable=\"NO\"",
                        "ntpd_enable=\"NO\"",
                        "zfs_enable=\"NO\"",
                        "# ... roughly nine hundred more lines, all of them off by default.",
                        "#",
                        "# That is the design: a FreeBSD machine starts with nothing running and",
                        "# you turn on what you want, rather than turning off what you do not."));

        CONTENTS.put(
                "/System/boot/loader.conf",
                List.of(
                        "# Read before the kernel exists. Everything here happens earlier than",
                        "# anything in rc.conf, which is why the two files are separate.",
                        "",
                        "zfs_load=\"YES\"",
                        "autoboot_delay=\"3\"",
                        "kern.geom.label.disk_ident.enable=\"0\""));

        CONTENTS.put(
                "/System/etc/fstab",
                List.of(
                        "# Device            Mountpoint  FStype  Options  Dump  Pass#",
                        "/dev/ada0p2         /           ufs     rw       1     1",
                        "/dev/ada0p3         none        swap    sw       0     0",
                        "",
                        "# Pass# is the order fsck checks them in on an unclean boot.",
                        "# The root filesystem is 1 because nothing else can be checked first."));

        CONTENTS.put(
                "/System/etc/passwd",
                List.of(
                        "# name:password:uid:gid:class:change:expire:gecos:home:shell",
                        "#",
                        "# NOTE: the second field is an asterisk on every line, and that is the",
                        "# interesting part: the password HASHES ARE NOT IN THIS FILE. They are",
                        "# in master.passwd, which is mode 0600 and root-only. This file is",
                        "# world-readable because programs need to turn a uid into a name.",
                        "",
                        "root:*:0:0:Charlie &:/root:/bin/sh",
                        "daemon:*:1:1:Owner of many system processes:/root:/usr/sbin/nologin",
                        "operator:*:2:5:System &:/:/usr/sbin/nologin",
                        "nobody:*:65534:65534:Unprivileged user:/nonexistent:/usr/sbin/nologin"));

        CONTENTS.put(
                "/System/etc/group",
                List.of(
                        "# name:password:gid:members",
                        "wheel:*:0:root",
                        "daemon:*:1:",
                        "operator:*:5:",
                        "nogroup:*:65533:",
                        "",
                        "# wheel is the group allowed to su to root. The name is from the old",
                        "# phrase 'big wheel' and it predates almost everything else here."));

        CONTENTS.put(
                "/System/etc/hosts",
                List.of(
                        "::1                 localhost localhost.my.domain",
                        "127.0.0.1           localhost localhost.my.domain",
                        "",
                        "# Consulted before DNS. Which is why editing it is the oldest trick",
                        "# there is for pointing a name somewhere it does not belong."));

        CONTENTS.put("/System/etc/resolv.conf", List.of("nameserver 10.0.0.1", "search local"));

        CONTENTS.put(
                "/System/etc/motd",
                List.of(
                        "uOS 15.0-RELEASE (GENERIC)",
                        "",
                        "Welcome. The base system is read-only; /usr/local is yours."));
    }

    /**
     * Lists a path inside {@code /System}.
     *
     * <p>Everything comes back unreadable. The listing is generated the same way the rest of the
     * tree is — see {@code VirtualFs}'s class comment on why nothing here is stored.
     */
    public static List<FsEntry> list(String path, Instant now) {
        return list(path, true, now);
    }

    /**
     * Lists a path inside {@code /System}.
     *
     * <p>⚠ {@code readable} is the caller's answer, not this class's. <b>This used to be hard-coded
     * false and that was wrong</b>: it meant a player could not read their own machine's
     * {@code rc.conf}, and the file manager told them to "breach it first" — about their own rig.
     * The base system is not yours to <em>edit</em> (every mode here is {@code r-xr-xr-x}); it was
     * never supposed to be a thing you could not <em>look at</em>.
     *
     * <p>{@link #MODE_RESTRICTED} files stay closed regardless, for the real reason rather than a
     * game one — see {@link #contents}.
     */
    public static List<FsEntry> list(String path, boolean readable, Instant now) {
        String p = VirtualFs.normalise(path);
        List<FsEntry> out = new ArrayList<>();

        if (p.equals(ROOT)) {
            for (String dir : NOTES.keySet()) {
                out.add(dir(ROOT + "/" + dir, readable, now));
            }
        } else if (p.equals(ROOT + "/usr")) {
            for (String dir : USR.keySet()) {
                out.add(dir(ROOT + "/usr/" + dir, readable, now));
            }
        } else if (p.equals(ROOT + "/usr/local")) {
            // Empty on a fresh machine, and that is the lesson rather than an omission: a FreeBSD
            // system with nothing installed has nothing here, because the base system is complete
            // without it.
            return List.of();
        } else if (p.equals(ROOT + "/boot")) {
            out.add(dir(ROOT + "/boot/kernel", readable, now));
            out.add(dir(ROOT + "/boot/defaults", readable, now));
            file(out, readable, ROOT + "/boot/loader.conf", now);
        } else if (p.equals(ROOT + "/etc")) {
            out.add(dir(ROOT + "/etc/defaults", readable, now));
            out.add(dir(ROOT + "/etc/rc.d", readable, now));
            out.add(dir(ROOT + "/etc/ssl", readable, now));
            out.add(dir(ROOT + "/etc/periodic", readable, now));
            for (String name : FILES.keySet()) {
                if (VirtualFs.parentOf(name).equals(ROOT + "/etc")) {
                    file(out, readable, name, now);
                }
            }
        } else if (p.equals(ROOT + "/etc/defaults")) {
            file(out, readable, ROOT + "/etc/defaults/rc.conf", now);
        } else if (p.equals(ROOT + "/etc/rc.d")) {
            // A handful of the real service scripts. Each is a program, and naming them is how a
            // player finds out that "starting a service" on FreeBSD is running a shell script.
            for (String service : List.of("sshd", "netif", "routing", "syslogd", "cron", "ntpd")) {
                out.add(new FsEntry(
                        service,
                        ROOT + "/etc/rc.d/" + service,
                        FsKind.EXECUTABLE,
                        3_200,
                        "-r-xr-xr-x",
                        "root",
                        "wheel",
                        now,
                        readable));
            }
        } else if (p.equals(ROOT + "/boot/kernel")) {
            file(out, readable, ROOT + "/boot/kernel/kernel", now);
            for (String module : List.of("zfs.ko", "if_em.ko", "nullfs.ko", "tmpfs.ko")) {
                out.add(new FsEntry(
                        module,
                        ROOT + "/boot/kernel/" + module,
                        FsKind.FILE,
                        400_000,
                        "-r--r--r--",
                        "root",
                        "wheel",
                        now,
                        readable));
            }
        } else if (p.equals(ROOT + "/bin") || p.equals(ROOT + "/usr/bin")) {
            for (String tool : binariesIn(p)) {
                out.add(new FsEntry(
                        tool,
                        p + "/" + tool,
                        FsKind.EXECUTABLE,
                        180_000,
                        "-r-xr-xr-x",
                        "root",
                        "wheel",
                        now,
                        readable));
            }
        } else if (p.equals(ROOT + "/sbin")) {
            for (String tool : List.of(
                    "init",
                    "mount",
                    "umount",
                    "ifconfig",
                    "route",
                    "fsck",
                    "shutdown",
                    "reboot",
                    "dmesg",
                    "sysctl",
                    "zfs",
                    "zpool")) {
                out.add(new FsEntry(
                        tool,
                        p + "/" + tool,
                        FsKind.EXECUTABLE,
                        210_000,
                        "-r-xr-xr-x",
                        "root",
                        "wheel",
                        now,
                        readable));
            }
        } else if (p.equals(ROOT + "/rescue")) {
            for (String tool : List.of("sh", "ls", "cp", "mv", "mount", "fsck", "zfs")) {
                out.add(new FsEntry(
                        tool,
                        p + "/" + tool,
                        FsKind.EXECUTABLE,
                        11_800_000,
                        "-r-xr-xr-x",
                        "root",
                        "wheel",
                        now,
                        readable));
            }
        } else if (p.equals(ROOT + "/libexec")) {
            file(out, readable, ROOT + "/libexec/ld-elf.so.1", now);
        } else if (p.equals(ROOT + "/var")) {
            for (String dir : List.of("log", "mail", "run", "spool", "tmp", "db", "cache", "empty")) {
                out.add(dir(ROOT + "/var/" + dir, readable, now));
            }
        } else if (p.equals(ROOT + "/lib")) {
            for (String library :
                    List.of("libc.so.7", "libcrypto.so.30", "libedit.so.8", "libncursesw.so.9", "libthr.so.3")) {
                out.add(new FsEntry(
                        library,
                        p + "/" + library,
                        FsKind.FILE,
                        1_900_000,
                        "-r--r--r--",
                        "root",
                        "wheel",
                        now,
                        readable));
            }
        } else if (p.equals(ROOT + "/usr/share")) {
            for (String dir : List.of("man", "doc", "zoneinfo", "misc", "examples")) {
                out.add(dir(ROOT + "/usr/share/" + dir, readable, now));
            }
        }
        out.sort(java.util.Comparator.comparing((FsEntry e) -> !e.directory()).thenComparing(FsEntry::name));
        return List.copyOf(out);
    }

    private static List<String> binariesIn(String path) {
        // /bin is what you need before /usr is mounted; /usr/bin is everything else. That split is
        // the reason /bin is so short, and it is worth being able to see.
        return path.endsWith("/usr/bin")
                ? List.of(
                        "awk", "grep", "sed", "make", "vi", "ssh", "top", "find", "sort", "tar", "xargs", "fetch",
                        "pkg", "man", "less")
                : List.of(
                        "cat", "chmod", "cp", "date", "dd", "df", "echo", "kill", "ln", "ls", "mkdir", "mv", "ps",
                        "pwd", "rm", "sh", "sleep", "sync", "test");
    }

    /**
     * What the manual has to say about a path, if anything.
     *
     * <p>⚠ This is the <b>substitute for opening the file</b>. Nothing under {@code /System} reads,
     * so a player who double-clicks one gets this instead — which means the note has to be worth
     * getting. Anything without one points at {@code man hier}, which is the real page this whole
     * tree is a rendering of.
     */
    public static String note(String path) {
        String p = VirtualFs.normalise(path);
        if (!p.startsWith(ROOT)) {
            return "";
        }
        String rest = p.length() > ROOT.length() ? p.substring(ROOT.length() + 1) : "";
        if (rest.isEmpty()) {
            return "The base operating system. Laid out as FreeBSD lays one out — see `man hier`. "
                    + "None of it opens: a game cannot ship a real kernel, and a file here that "
                    + "printed invented bytes would be teaching something false.";
        }
        String top = rest.contains("/") ? rest.substring(0, rest.indexOf('/')) : rest;
        if (rest.startsWith("usr/")) {
            String second = rest.substring(4);
            String key = second.contains("/") ? second.substring(0, second.indexOf('/')) : second;
            String usrNote = USR.get(key);
            if (usrNote != null) {
                return usrNote;
            }
        }
        String note = NOTES.get(top);
        return note == null ? "Part of the base system. `man hier` describes the layout." : note;
    }

    /** Whether a path is inside the base system, and therefore unreadable. */
    public static boolean isSystem(String path) {
        String p = VirtualFs.normalise(path);
        return p.equals(ROOT) || p.startsWith(ROOT + "/");
    }

    private static FsEntry dir(String path, boolean readable, Instant now) {
        // r-xr-xr-x and owned by root:wheel throughout. FreeBSD's base system is not yours to edit,
        // and the mode string is where a player can see that before they try.
        return new FsEntry(
                VirtualFs.nameOf(path), path, FsKind.DIRECTORY, 0, "dr-xr-xr-x", "root", "wheel", now, readable);
    }

    private static void file(List<FsEntry> out, boolean readable, String path, Instant now) {
        // ⚠ A mode-restricted file is closed even on your own machine, and for the REAL reason —
        // /etc/master.passwd is 0600 on every FreeBSD box alive. That is a fact worth meeting.
        boolean open = readable && !MODE_RESTRICTED.contains(path);
        out.add(new FsEntry(
                VirtualFs.nameOf(path),
                path,
                FsKind.FILE,
                FILES.getOrDefault(path, 1_024L),
                MODE_RESTRICTED.contains(path) ? "-rw-------" : "-r--r--r--",
                "root",
                "wheel",
                now,
                open));
    }
}

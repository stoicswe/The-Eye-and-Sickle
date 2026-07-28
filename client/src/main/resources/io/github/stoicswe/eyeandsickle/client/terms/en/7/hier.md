---
id: hier
section: 7
name: hier
canonical: hier
gloss: Where things live on a Unix machine, and why it is not arbitrary.
status: real, simplified
aliases: layout
seeAlso: storage-tiers(7), ls(1), df(1), shell(7)
reading: hier(7) on any FreeBSD or macOS machine | Filesystem Hierarchy Standard 3.0
revision: 1
---

## DESCRIPTION

A Unix filesystem is not one pile of files. It is split by a question:
**what has to work before the rest of the disk is available?**

    /bin  /sbin  /lib     what the machine needs to start at all
    /usr                  everything else the system ships
    /usr/local            everything nobody shipped with it
    /etc                  configuration, so it survives a reinstall
    /var                  files that change while it runs

That is the whole logic. `/bin` is short because almost nothing has to be
there. `/usr` is enormous because almost everything else does. `/usr/local`
exists so that upgrading the operating system cannot delete the software you
installed afterwards.

On this rig the four things at the root are:

    /Applications         programs
    /Library              shared support that is not the OS and not yours
    /System               the operating system itself
    /Users                people

and the hierarchy above lives inside `/System`.

### The one that matters: `/usr/local`

FreeBSD develops its base system — kernel, libraries, and the tools in `/bin`,
`/sbin`, `/usr/bin` and `/usr/sbin` — as one coherent thing, versioned and
released together. Anything from a port or a package installs under
`/usr/local` and nowhere else. So "what came with this machine" and "what
somebody added" are answerable by looking at a path.

Linux draws no such line. The same `/usr/bin` holds the C library's tools and
yesterday's package install, and telling them apart means asking the package
manager.

That difference explains a whole category of problem — *the upgrade removed my
software* — that one design has and the other does not.

## REAL-WORLD COUNTERPART

real, simplified — the layout is exact and is what `hier(7)` documents on any
FreeBSD or macOS machine. Run `man hier` on either and you will get a longer
version of this page.

What is simplified is depth: `/System` here shows a representative subset of
each directory, not the tens of thousands of files a real base system holds.

Worth trying, on any Mac or Linux box: `ls /usr/local/bin` — everything in it
was installed after the operating system. Then `ls /bin` — that is what the
machine needs to start. The two lists being wildly different lengths is the
whole idea.

## CAVEATS

**This rig's root is macOS's, not FreeBSD's.** A real FreeBSD machine has
`/bin`, `/etc`, `/usr` and the rest *at* the root. uOS puts the macOS four on
top and the FreeBSD hierarchy inside `/System`. macOS is the system where both
halves are true at once, which is why uOS is shaped this way — but on a real
FreeBSD box, `/System` does not exist.

**Nothing in `/System` opens, and that is a limit of this game rather than a
fact about Unix.** A real `/System/boot/kernel/kernel` is a real kernel. This
one cannot be, and a file here that printed invented bytes would be teaching
something false about the exact subject this page exists to teach. So the tree
is complete, closed, and documented instead.

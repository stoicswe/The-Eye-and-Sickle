---
id: ls
section: 1
name: ls
canonical: ls
gloss: Lists what is in a place.
status: real
seeAlso: glob(7), df(1), storage-tiers(7), quoting(7)
reading: ls(1) | POSIX.1-2024 XCU "ls"
revision: 1
---

## SYNOPSIS

       ls [--] [<path>]

## DESCRIPTION

Lists what is at a place in the rig's namespace. With no argument it lists
the root.

The tree is:

    /rig/compute/         one entry per compute consumer
    /rig/storage/vault/   the three tiers, as mount points
    /rig/tools/           what you own and have equipped
    /net/<address>/       machines you have discovered — only those
    /ledger/              ethecoin movements
    /man/<section>/       this manual

Paths take globs: `ls /rig/tools/*-sweep`. See glob(7).

`/net/` contains only what you have actually discovered. That is not a
nicety — recon costs compute and ethecoin, and a listing that showed you
unscanned machines would be giving away what you are meant to pay for.

## OPTIONS

       --    end of options, so a name may begin with a dash

## EXIT STATUS

       0    the listing was produced
       1    no such place

## REAL-WORLD COUNTERPART

real — `ls` is the first command most people ever type, and it does this.

The real one has far more flags: `-l` for the long form with permissions and
sizes, `-a` to include the dotfiles that are hidden by convention rather than
by any rule, `-h` for human-readable sizes. Try `ls -la ~` and notice how
much was there that you were not being shown.

The paths here are not real paths. Nothing you type in this terminal reaches
your actual filesystem.

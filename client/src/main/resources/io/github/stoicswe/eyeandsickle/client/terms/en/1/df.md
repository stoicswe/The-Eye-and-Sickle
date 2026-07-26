---
id: df
section: 1
name: df
canonical: df
gloss: Shows how full each storage area is, and how exposed.
status: real, simplified
seeAlso: storage-tiers(7), ls(1), compute(7)
reading: df(1) | mount(8) | statvfs(3)
revision: 1
---

## SYNOPSIS

       df [-h] [--]

## DESCRIPTION

Shows the three storage tiers as mount points, with what each holds and — the
column a real `df` has no equivalent for — how exposed each one is.

    /rig/storage/vault      safe
    /rig/storage/standard   exposed while online
    /rig/storage/high       always exposed

## OPTIONS

       -h    human-readable sizes

## EXIT STATUS

       0    the listing was produced

## REAL-WORLD COUNTERPART

real, simplified — `df` reports free space on mounted filesystems, and the
mount-point model here is the real one: a filesystem appears at a place in
the single tree rather than as a separate lettered drive.

Try `df -h` on any Mac or Linux machine. The `-h` is worth having: without it
you get numbers in 512-byte or 1K blocks, which is a unit almost nobody wants.

## CAVEATS

A real `df` has no exposure column, because a real filesystem does not have a
notion of who might be able to reach it — that is decided by permissions,
network configuration and encryption, all separately. The tiers compress a
genuinely multi-part question into one axis.

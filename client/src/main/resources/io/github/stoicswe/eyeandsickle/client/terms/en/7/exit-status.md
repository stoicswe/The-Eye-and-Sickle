---
id: exit-status
section: 7
name: exit status
canonical: exit status
gloss: The number a finished command leaves behind to say how it went.
status: real
aliases: exit code, return code, status
seeAlso: shell(7), pipeline(7), ps(1), man(1)
reading: POSIX.1-2024 XCU §2.8.2 | sysexits(3) | intro(1) | wait(2)
revision: 1
---

## DESCRIPTION

Every command that finishes leaves a number behind. The terminal shows the
last one as `$?`.

Zero means it worked. Anything else means it did not, and which number says
something about how:

    0    the request was accepted
    1    a rule refused it, and nothing changed
    2    bad invocation — unknown flag, missing argument
    69   could not reach the server
    75   sent, no answer yet; retrying is safe
    77   a gate blocks this, and the requirement is printed
    126  the command exists, but you cannot field that tool
    127  no such command
    130  you aborted it

`1` and `69` are deliberately different. `1` means your request arrived and
was declined; `69` means it never arrived. Those are not the same situation
and must never look like it.

`130` is the most interesting number here. It is 128 plus 2, signal 2 is
`SIGINT`, and `SIGINT` is what Ctrl-C sends.

## REAL-WORLD COUNTERPART

real — the whole scheme is. Zero-for-success is specified in POSIX.1-2024
and stated in `intro(1)`. The 128+N convention for signal deaths is universal
across `bash`, `zsh`, `dash` and `ksh`.

`69`, `75` and `77` are borrowed exactly from `sysexits.h`, a real header
shipped on BSD and macOS systems, where they are `EX_UNAVAILABLE`,
`EX_TEMPFAIL` and `EX_NOPERM`. You can read it: it is in
`/usr/include/sysexits.h`.

Try it now: run `ls /` then `echo $?` — zero. Run `ls /nonexistent` then
`echo $?` — non-zero. Run `sleep 30`, press Ctrl-C, then `echo $?`: 130.

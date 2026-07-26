---
id: glob
section: 7
name: glob
canonical: glob
gloss: A pattern that stands for a set of names without listing them.
status: real
aliases: wildcard, globbing, wildcards
seeAlso: shell(7), grep(1), ls(1), quoting(7)
reading: POSIX.1-2024 XCU §2.13 "Pattern Matching Notation" | fnmatch(3) | bash(1) "Pathname Expansion"
notes: "Glob" is a technical term from the original glob command in Unix v1. Do not translate as "globe".
revision: 1
---

## DESCRIPTION

`ls /rig/tools/*-sweep` lists every tool whose name ends in `-sweep` without
you naming any of them.

The syntax is small and worth knowing completely:

    *        any run of characters, including none
    ?        exactly one character
    [abc]    any one of these
    [a-z]    any one in this range
    [!abc]   any one that is NOT one of these

The important part is not the syntax, it is *when* it happens. The shell
expands the pattern first, into a list of names that actually exist, and only
then runs the command. The command never sees the asterisk.

That single fact explains most glob surprises. A pattern matching nothing
does not become an empty list — it is passed through unchanged.

## REAL-WORLD COUNTERPART

real — standardised in POSIX.1-2024 as Pattern Matching Notation, and
available to programs through `fnmatch(3)`. The name comes from `glob`, a
separate program in the earliest Unix that did this expansion before the
shell absorbed it.

One extension exists in real shells and not here: `**`, which crosses
directory boundaries. `bash` needs `shopt -s globstar` to enable it.

Try it: `mkdir -p /tmp/g && cd /tmp/g && touch a ab abb ac` then `echo ab*`.
It prints `ab abb` — not `a`, not `ac`. Now compare that with grep(1).

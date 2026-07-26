---
id: quoting
section: 7
name: quoting
canonical: quoting
gloss: Telling a system that these words are one word, punctuation and all.
status: real, simplified
aliases: quotes, escaping
seeAlso: shell(7), glob(7), ls(1)
reading: POSIX.1-2024 XCU §2.2 "Quoting" | bash(1) "Quoting" | CWE-78 "OS Command Injection"
notes: The CAVEATS divergence is the largest on this surface and must not be trimmed for length.
revision: 1
---

## DESCRIPTION

A line you type is split into words at the spaces, and each word becomes one
argument. That is fine until something you are naming has a space in it.

`mv Old Ledger Dump vault` is four words. The command sees four arguments,
none of which is the thing you meant. `mv "Old Ledger Dump" vault` is two,
and works.

Both quote characters work here and both are fully literal: everything
between them, including spaces and punctuation, is one word.

A related job: `--` marks the end of flags, so a name beginning with `-` can
be given as an argument without being mistaken for one. Quoting does not
solve that — a quoted `"-n"` is still a flag.

## REAL-WORLD COUNTERPART

real, simplified — quoting exists on every shell and does the job above.

Why it matters beyond convenience: quoting is the boundary between data and
instructions. A filename containing a space is harmless. A filename
containing a semicolon, handed unquoted to a program that builds a command
out of it, is the oldest security bug there is.

## CAVEATS

**In a real shell the two quote characters are not the same, and this is the
largest divergence on this surface.**

Single quotes are fully literal, as ours are. Double quotes are *not*: inside
them, `$HOME` becomes your home directory and `$(cmd)` runs a command. Try
`echo "$HOME"` and `echo '$HOME'` on a real machine and watch only one of
them expand. Both of ours print the four characters.

We do this because there is nothing to expand — this surface has no variables
and no command substitution. That is a property of this game, not of quoting.

Carry the real rule, not ours: in a real shell, quote every variable —
`"$file"`, not `$file` — because an unquoted variable containing a space
becomes two words.

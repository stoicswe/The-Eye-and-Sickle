---
id: shell
section: 7
name: shell
canonical: shell
gloss: The program that reads what you type and decides what it names.
status: real, simplified
aliases: command line, terminal, command interpreter
seeAlso: exit-status(7), pipeline(7), glob(7), quoting(7), man(1), ps(1)
reading: sh(1) | bash(1) "Shell Grammar" | POSIX.1-2024 XCU §2 "Shell Command Language"
notes: Do not translate "shell" as a metaphor for a covering or husk. Transliterate or keep.
revision: 1
---

## DESCRIPTION

The prompt in the terminal window is a program. It does one small job in a
loop: read the line you typed, split it into words, look up the first word,
run what it names, collect the number that comes back, print a new prompt.

Everything else in this section — flags, quoting, globs, pipelines — is a
rule about how the line gets split up before the lookup happens.

Two things surprise people. The shell is not privileged: it can do what you
can do and no more. And there is no single shell — the one in front of you
is one program among several, and swapping it changes the grammar without
changing anything underneath.

## REAL-WORLD COUNTERPART

real, simplified — this is what a shell is, and `sh`, `bash`, `zsh`, `fish`
and `dash` are all instances of it. The read-split-run-report loop above is
theirs, not ours.

The standard is POSIX.1-2024, chapter 2, and it is readable: it defines the
grammar as a set of rules about how a line becomes words. macOS ships `zsh`
as its default with `bash` and `sh` alongside; most Linux systems default to
`bash`. Run `echo $SHELL` to see which one is yours, then `cat /etc/shells`
to see the others already installed.

## CAVEATS

This one is narrower than a real shell, and the differences matter if you
carry a habit to a real machine:

Commands here are case-insensitive. On a real machine `PS` and `ps` are two
different names and only one exists. We are forgiving because typing
accurately against a trace timer is not the skill being tested.

There is no redirection (`>`), no chaining (`&&`, `;`), no command
substitution (`$( )`) and no background operator (`&`). All of them are real
and worth learning; this surface simply is not a shell, and the error message
tells you so rather than pretending your syntax was wrong.

Double quotes here are literal. In a real shell, `"$x"` becomes the value of
`x`. See quoting(7).

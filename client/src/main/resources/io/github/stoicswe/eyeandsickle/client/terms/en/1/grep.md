---
id: grep
section: 1
name: grep
canonical: grep
gloss: Keeps only the lines that match, and throws the rest away.
status: real
seeAlso: pipeline(7), glob(7), ps(1), ls(1)
reading: grep(1) | POSIX.1-2024 XCU "grep" | re_format(7)
revision: 1
---

## SYNOPSIS

       grep [-i] [-v] [-E] [--] <pattern>

## DESCRIPTION

Reads lines, prints the ones that match, discards the ones that do not. Here
it is always used after a `|`.

`ps | grep miner` shows only the compute consumers whose line mentions
"miner". `ss | grep -v ESTAB` shows every connection that is *not*
established — a much more interesting list than the one it came from.

The pattern is a regular expression, not a glob. This matters: in a glob,
`*` means "any characters". Here it means "zero or more of the thing before
it", and "any characters" is `.*`.

## OPTIONS

       -i    ignore case
       -v    invert — keep the lines that do NOT match
       -E    extended regular expressions, enabling + ? and |

## EXIT STATUS

       0    at least one line matched
       1    nothing matched — an answer, not an error
       2    the pattern or invocation was wrong

## REAL-WORLD COUNTERPART

real — `grep` is exactly this on every Unix machine, and these flags mean
what its flags mean. The name is from the `ed` editor command `g/re/p` —
globally, regular expression, print — which is genuine history rather than a
backronym.

Its most under-used flag is `-v`. Its most common real use is not searching
files at all: it is the second stage of a pipeline, exactly as here.

Try the contrast with glob(7): `printf 'a\nab\nabb\nac\n' | grep -E '^ab*$'`
prints `a ab abb`. The same pattern as a glob printed `ab abb`. Same three
characters, different answers, no error to warn you.

## CAVEATS

It matches the whole line, not a column. `ps | grep miner` will match a
process whose owner or path contains "miner", not just its name. When
precision matters, real machines reach for `awk` or structured output.

On a real machine, grep finds itself: `ps aux | grep ssh` matches the
`grep ssh` process too. This is a rite of passage, not a bug.

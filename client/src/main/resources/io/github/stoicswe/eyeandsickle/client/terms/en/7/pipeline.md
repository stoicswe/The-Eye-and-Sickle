---
id: pipeline
section: 7
name: pipeline
canonical: pipeline
gloss: Joining commands so each one's output becomes the next one's input.
status: real, simplified
aliases: pipe, pipes
seeAlso: shell(7), grep(1), ps(1), exit-status(7)
reading: POSIX.1-2024 XCU §2.9.2 "Pipelines" | bash(1) "Pipelines"
revision: 1
---

## DESCRIPTION

`ps | grep miner` is two commands. `ps` lists what is consuming compute;
`grep` keeps only the lines containing "miner". The `|` says: whatever the
left one prints, hand it to the right one as input.

They do not take turns. Both start immediately and run at the same time, with
output flowing left to right as it is produced.

Two rules catch people out. **The pipeline's exit status is the last
command's** — if the left side fails and the right side succeeds, the failure
is silently lost. And **here, a pipeline may only read.** Putting anything
with a side effect into one is refused at parse time, with a message naming
the offending command.

This is the most useful thing in the terminal. A hidden miner is found by
comparing two views of your own machine and noticing they disagree — and a
pipeline is how you compare them.

## REAL-WORLD COUNTERPART

real, simplified — the syntax, the concurrency and the exit-status rule are
exactly the real thing.

The restriction to read-only commands is ours. Real shells impose no such
rule, and a real pipeline will happily half-apply an action if the right side
exits early.

Try the exit-status surprise yourself: `false | true; echo $?` prints 0 — the
failure on the left vanished. Then `set -o pipefail` and run it again: 1.
That setting is off by default, which has caused a great many scripts to
report success while doing nothing.

## CAVEATS

Filters work on rendered text, not on structure. `ps | grep miner` matches
the word anywhere on the line, including in a column you did not mean. That
is true of real pipelines too, and it is exactly why `awk`, `jq` and
structured output exist.

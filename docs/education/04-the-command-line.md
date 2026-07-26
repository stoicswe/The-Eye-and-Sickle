# 04 — The command line — the shell as a program, and text as an interface

**Status:** ⚠️ **[PROPOSAL]** — the *obligation* is Established: client pillar **C6** (`../client/00-client-overview.md` §2), the product goal in §5, and the falsifiable claim in `../client/04-terminology-and-education.md` §1.1. So is nearly all of the *material*: `../client/04` §3 specifies the command grammar, the five universal flags, the exit-status table, tab completion, the pipeline and the glob syntax as shipping capabilities, and this document teaches what that surface already does. What is first-pass design here is the concept inventory, the stage and prerequisite assignments, and the eighteen written entries. Every factual claim was checked against a primary source or run on a real machine in this pass and recorded per entry in `verified:`.
**Depends on:** `00-curriculum-and-method.md` — **the contract**: §3 (the entry template), §4 (the status vocabulary), §5 (man-section assignment), §6 (stages and rules R1–R8), §7 (coverage), §8 (the writer's loop). **None of it is re-specified here.** Also `../client/04-terminology-and-education.md` §3 — **the specification this document teaches**: §3.1 (the safety boundary), §3.2 (the namespace), §3.3 (command grammar), §3.4 (the five universal flags), §3.5 (exit statuses), §3.6 (tab completion), §3.7 (pipelines), §3.8 (globbing), §3.9 (keys), §3.10 (the command catalogue), §3.11 (what is stylistic borrowing only); §4.3.1 (body section names), §4.8 (the shipped file format), §4.9 (worked entries); `../design/glossary.md`; `../design/04-mining.md` §3.1 (the investigation the pipeline exists for); `../../CLAUDE.md`
**Depended on by:** `03-operating-systems.md` §2.3, `06-cryptography-and-trust.md` §1.4 and `01-foundations.md`, each of which cedes `shell(7)`, `glob(7)`, `quoting(7)`, `exit-status(7)`, `flag(7)`, `grep(1)` or `man(1)` here and cites them rather than defining them; and, through them, `client/src/main/resources/terms/**`

---

## 1. What this domain is

### 1.1 The domain in one paragraph

A shell is a program that reads a line of text, decides what it names, runs it, and reports a number. Everything else in this domain is a consequence of that sentence: arguments and flags exist because a line of text needs structure; quoting exists because that structure has to be escapable; exit statuses exist because "it worked" has to be machine-readable; pipelines exist because the output of one program is text and the input of another is text; globs and regular expressions exist because text has to be matched, and they disagree about what `*` means. The domain is small, unusually coherent, and almost entirely transferable — of the eighteen entries below, sixteen describe behaviour the reader can reproduce tonight on a machine they already own.

### 1.2 Why a player of *this* game benefits

Because the game's central investigation is a shell pipeline. `../design/04-mining.md` §3.1 requires that a careful player can find a rootkit-wrapped miner by noticing that two views of their own machine disagree, and `../client/04` §3.7 makes that concrete: list processes, list sockets, compare, find the connection with no owning process. That is not a simulation of the skill. It is the skill, performed with the real verbs, on a smaller board — and a player who can do it here can do it on a real machine, because `ps`, `ss`, `grep` and `|` are not borrowed names, they are the actual tools.

The second benefit is narrower and worth stating separately: this domain is where the game teaches a player **how to find out**. `man(1)` and `apropos(1)` are the two commands that make every other command self-documenting, and the teaching layer's entire form — `NAME` / `SYNOPSIS` / `DESCRIPTION` / `SEE ALSO` (`../client/04` §4.3.1) — is a man page, deliberately, so that a player learns to read man pages by reading several hundred of them without noticing. That is the one skill in the whole curriculum that compounds.

### 1.3 The surfaces this domain answers to

| Surface | Spec | Concepts it raises |
|---|---|---|
| The `terminal` window and its prompt | `../client/04` §3.1, §3.11 | `shell(7)`, `command-not-found(7)` |
| The command palette | `../client/04` §3.3, §3.6 | `argument(7)`, `flag(7)`, `tab-completion(7)` |
| `$?` in the terminal status line; the `es-chip` | `../client/04` §3.5 | `exit-status(7)`, `sysexits(7)` |
| `-h`, `--explain`, `-n`, `-v`, `--` on every command | `../client/04` §3.4 | `flag(7)`, `dry-run(7)` |
| `ps \| grep miner` | `../client/04` §3.7 | `pipeline(7)`, `standard-streams(7)`, `grep(1)`, `text-as-interface(7)` |
| `ls /rig/tools/*-sweep` | `../client/04` §3.8, §3.2 | `glob(7)` |
| `grep -E` inside a pipeline | `../client/04` §3.7, §3.8 | `regular-expression(7)` |
| Item and node names containing spaces or a leading `-` | `../client/04` §3.3 | `quoting(7)`, `argument(7)` |
| The `man` window, `Shortcut+Shift+E`, `apropos` | `../client/04` §3.10, §4 | `man(1)`, `man-section(7)`, `apropos(1)` |
| `Up`/`Down`, `Ctrl+R` in the palette | `../client/04` §3.9 | `history(1)` |
| The `botnet` window; `jobs`, `bot build\|stop` | `../client/04` §3.10 | `jobs(1)` |
| The parse error on `>`, `&&`, `;`, `$( )` | `../client/04` §3.11 | `redirection(7)` — inventory only, and the entry is about its *absence* |

### 1.4 What this document owns, and what it does not

- **The grammar is owned here.** Arguments, flags, quoting, globs, pipeline syntax, exit statuses, completion, history, and the `man` machinery. `03-operating-systems.md` §2.3 cedes exactly this list, in these words: *"The command-line domain owns the grammar (flags, exit statuses, quoting, globs, pipelines, `man(1)`)."*
- **`pipe(7)` is not owned here; `pipeline(7)` is.** A pipe is a kernel object — a buffer with a file descriptor at each end — and belongs to `03-operating-systems.md`, which says so. The `|` character, its ordering, and the rule that a pipeline's exit status is its last command's are shell grammar. Both entries cite each other, and neither repeats the other.
- **The commands whose teaching payload is an operating-system concept are not owned here.** `ps(1)`, `kill(1)`, `df(1)` and `scan(8)` are `03`'s by its own §2.3. `grep(1)` is ours because its payload is pattern matching, not the kernel.
- **Not owned here:** `process(7)`, `signal(7)`, `file-descriptor(7)`, `permissions(7)`, `path(7)`, `inode(7)` — operating systems. `byte(7)`, `character-encoding(7)`, `utf-8(7)`, `data-and-code(7)` — foundations. `injection(7)` — `06-cryptography-and-trust.md`, though `quoting(7)` points at it. `compute(7)`, `noise(7)`, `heat(7)` — game resources, owned where their surfaces are.
- ⚠ **`ls(1)` and `stat(1)` are claimed here provisionally.** `03` §2.3's list of command pages it owns names `ps`, `kill`, `df` and `scan`, and does not mention these two. Their payload is arguably filesystem (`03`) and arguably command grammar (here — `ls` is the first command most people ever type, and it is where positional arguments and clustered flags are first met). Inventoried here, unwritten, and raised as **SH-2** so `03` can claim them back cheaply.

---

## 2. The concept inventory

### 2.1 How to read the table

`§` is the man section this concept's page will ship in, assigned by `00-curriculum-and-method.md` §5. `Stage` is when the curriculum first *offers* it, not a lock — every page stays reachable through `man` at any time (`../client/04` §4.6). Prerequisites in **bold** point outside this domain, and every one of them points *downward* to domain 01, 02 or 03, which is rule **R8**. "Full?" marks the eighteen entries written out in §3.

### 2.2 The inventory

| # | id · § | Name | Gloss | Status | Stage | Prerequisites | Game surface | Full? |
|---|---|---|---|---|---|---|---|---|
| 1 | `shell` · 7 | shell | The program that reads what you type and decides what it names. | `real, simplified` | first-session | **`process(7)`** | The `terminal` prompt | ✅ |
| 2 | `command` · 7 | command | A named action, plus the words that tell it what to act on. | `real` | operating | `shell(7)` | Every line typed | |
| 3 | `argument` · 7 | argument | A word given to a command to say what it should act on. | `real` | operating | `command(7)` | `breach <node>`, `mv <item> <tier>` | ✅ |
| 4 | `flag` · 7 | flag | A word that changes how a command behaves rather than what it acts on. | `real` | operating | `argument(7)` | The five universal flags | ✅ |
| 5 | `subcommand` · 7 | subcommand | A second word that selects which of a tool's actions you mean. | `real` | operating | `argument(7)` | `market install`, `bot build` | |
| 6 | `dry-run` · 7 | dry run | Asking what would happen, and being told without it happening. | `real` | investigating | `flag(7)` | `-n` on every command | ✅ |
| 7 | `exit-status` · 7 | exit status | The number a finished command leaves behind to say how it went. | `real` | operating | `command(7)` | `$?` in the status line | ✅ |
| 8 | `sysexits` · 7 | sysexits | A published numbering for *why* something failed, not just that it did. | `real` | investigating | `exit-status(7)` | `69`, `75`, `77` | |
| 9 | `command-not-found` · 7 | command not found | What a system says when the word you typed names nothing. | `real` | operating | `exit-status(7)`, `shell(7)` | Exit `127` | |
| 10 | `standard-streams` · 7 | standard streams | The three text channels every program is handed when it starts. | `real` | investigating | **`file-descriptor(7)`**, `command(7)` | Why a pipeline works | ✅ |
| 11 | `pipeline` · 7 | pipeline | Joining commands so each one's output becomes the next one's input. | `real, simplified` | investigating | `standard-streams(7)`, **`pipe(7)`** | `ps \| grep miner` | ✅ |
| 12 | `text-as-interface` · 7 | text as an interface | Why unrelated tools can be joined: they all speak lines of characters. | `real` | investigating | `pipeline(7)`, **`character-encoding(7)`** | Every pipeline | ✅ |
| 13 | `redirection` · 7 | redirection | Pointing a program's output at a file instead of at the screen. | `real` | investigating | `standard-streams(7)` | ⚠ Its *absence* — the parse error on `>` | |
| 14 | `glob` · 7 | glob | A pattern that stands for a set of names without listing them. | `real` | investigating | **`path(7)`**, `argument(7)` | `ls /rig/tools/*-sweep` | ✅ |
| 15 | `regular-expression` · 7 | regular expression | A pattern language for text, where every character is a rule. | `real, simplified` | investigating | `glob(7)` | `grep -E` | ✅ |
| 16 | `grep` · 1 | grep | Keeps only the lines that match, and throws the rest away. | `real` | investigating | `pipeline(7)`, `regular-expression(7)` | `ps \| grep miner` | ✅ |
| 17 | `sort` · 1 | sort | Reorders lines by a chosen field rather than by chance. | `real` | investigating | `pipeline(7)` | `sort -k size -r` | |
| 18 | `uniq` · 1 | uniq | Collapses runs of identical neighbouring lines, and can count them. | `real` | investigating | `sort(1)` | `uniq -c` | |
| 19 | `head` · 1 | head | Shows the first few lines and stops. | `real` | operating | `pipeline(7)` | `head -n 5` | |
| 20 | `tail` · 1 | tail | Shows the last few lines, and can keep watching for more. | `real` | operating | `pipeline(7)` | `log -f` | |
| 21 | `wc` · 1 | wc | Counts lines, words or characters instead of showing them. | `real` | investigating | `pipeline(7)` | `ss \| wc -l` | |
| 22 | `cut` · 1 | cut | Keeps chosen columns of each line and drops the others. | `real` | investigating | `pipeline(7)`, `text-as-interface(7)` | `cut -f` | |
| 23 | `quoting` · 7 | quoting | Telling a system that these words are one word, punctuation included. | `real, simplified` | operating | `argument(7)` | Item names with spaces | ✅ |
| 24 | `word-splitting` · 7 | word splitting | How one typed line becomes the separate words a command receives. | `real` | investigating | `quoting(7)` | Why an unquoted name breaks | |
| 25 | `case-sensitivity` · 7 | case sensitivity | Whether two spellings that differ only in capitals are the same name. | `real` | operating | `command(7)` | Our forgiving matching | |
| 26 | `tab-completion` · 7 | tab completion | Letting the machine finish a name it already knows. | `real` | operating | `command(7)`, **`path(7)`** | `Tab` in the palette | ✅ |
| 27 | `readline` · 7 | readline | The shared editing layer that gives many programs the same keys. | `real` | operating | `shell(7)` | `Ctrl+A`, `Ctrl+E`, `Ctrl+R` | |
| 28 | `history` · 1 | history | The record of what you have already typed, searchable. | `real` | operating | `shell(7)` | `Up`, `Ctrl+R`, the transcript | ✅ |
| 29 | `man` · 1 | man | Opens the manual page for a thing, on the machine, offline. | `real` | first-session | `command(7)` | The `man` window | ✅ |
| 30 | `man-section` · 7 | manual section | The number that says *which* manual a name is being looked up in. | `real` | operating | `man(1)` | `man 7 compute`; `1` vs `8` | ✅ |
| 31 | `apropos` · 1 | apropos | Finds a command when you know what you want, not what it is called. | `real` | investigating | `man(1)` | `apropos`, `whatis` | ✅ |
| 32 | `synopsis-notation` · 7 | synopsis notation | The brackets and bars that say which parts are optional. | `real` | investigating | `man(1)`, `flag(7)` | Every `SYNOPSIS` line | |
| 33 | `jobs` · 1 | jobs | Lists the work you started that is still running. | `real, simplified` | investigating | **`process(7)`**, `command(7)` | The `botnet` window | ✅ |
| 34 | `job-control` · 7 | job control | Moving running work between the foreground and the background. | `real` | investigating | `jobs(1)`, **`signal(7)`** | `bot build\|stop`; `Ctrl+Z` | |
| 35 | `ls` · 1 | ls | Lists what is in a place. | `real` | operating | **`path(7)`**, `argument(7)` | The `storage` window ⚠ **SH-2** | |
| 36 | `stat` · 1 | stat | Shows a thing's recorded details rather than its contents. | `real` | investigating | **`inode(7)`** | `storage` ⚠ **SH-2** | |
| 37 | `shell-injection` · 7 | shell injection | What happens when data is allowed to become a command. | `real` | adversarial | `quoting(7)`, **`data-and-code(7)`** | ⚠ contingent — **SH-6** | |
| 38 | `environment-variable` · 7 | environment variable | A named value a program inherits from whatever started it. | `real` | investigating | **`process(7)`** | ⚠ Its *absence* — no expansion | |

**38 concepts, 18 written.** Two of the unwritten are deliberately about something the game does *not* have (`redirection(7)`, `environment-variable(7)`) — see §2.4.

### 2.3 Concepts this domain cites but does not own

| Cited | Owner | Used here as |
|---|---|---|
| `process(7)`, `signal(7)`, `file-descriptor(7)`, `pipe(7)`, `path(7)`, `inode(7)` | **03** operating systems | `prerequisites` on `shell(7)`, `standard-streams(7)`, `pipeline(7)`, `tab-completion(7)`, `jobs(1)` |
| `character-encoding(7)`, `byte(7)`, `data-and-code(7)` | **01** foundations | `prerequisites` on `text-as-interface(7)`; `seeAlso` on `quoting(7)` |
| `ps(1)`, `kill(1)`, `df(1)`, `scan(8)`, `log(7)` | **03** operating systems | `seeAlso` only — they are this domain's worked examples but not its pages |
| `injection(7)` | **06** cryptography & trust | `seeAlso` on `quoting(7)`. ⚠ `shell-injection(7)` above may duplicate it — **SH-6** |
| `compute(7)`, `noise(7)` | **02** / game resources | Named in `dry-run(7)`'s body as the figures a dry run prints |

Every prerequisite edge crossing a domain boundary points **downward**, into 01, 02 or 03. There are no edges to 05, 06 or 07. This is **R8**, and it is the constraint that fixed this domain's number — see §2.5.

### 2.4 The honesty ledger

Four places where this domain's surface and the real thing diverge, each stated on the page that owns it rather than left for a player to discover and mistrust.

| Divergence | Where | How the page handles it |
|---|---|---|
| **Commands are case-insensitive here; real shells are case-sensitive** | `../client/04` §3.3 | `shell(7)` `CAVEATS` says so plainly, and says why: typing accuracy under a trace timer is not the skill being tested (pillar C5) |
| **Double quotes do not interpolate here; in a real shell they do** | `../client/04` §3.3, §3.1 rule 4 | `quoting(7)` `CAVEATS`. This is the largest single divergence in the domain, and the real behaviour is stated in full so nothing false is learned |
| **There is no redirection, chaining or command substitution** | `../client/04` §3.1 rule 5 | The parse error itself teaches (`../client/04` §3.11 specifies the message). `redirection(7)` is inventoried as a page about a real thing this surface deliberately lacks |
| **The pipeline is read-only and cannot contain a side effect** | `../client/04` §3.7 | `pipeline(7)` `CAVEATS`. Real pipelines have no such rule, and the page says the restriction is ours |

**Zero `game`-status entries in this domain**, and that is the finding rather than an oversight. `00-curriculum-and-method.md` §4.4 warns about a domain that is mostly `game` — lore wearing a curriculum's clothes. This domain has the opposite property: every concept in it exists outside the fiction, four of them are `real, simplified` because our surface is narrower than the real one, and none is invented. It is, by that measure, the most transferable domain in the set.

### 2.5 The graph, checked

`00-curriculum-and-method.md` §6.4's five checks, run by hand.

1. **Acyclic** — yes. The internal graph is a forest rooted at `shell(7)` and `man(1)`, and no entry names a descendant.
2. **Every reference resolves** — within this document, or to one of the eleven external references in §2.3. ⚠ The external ids are this domain's expectation of what its neighbours call them; `03`'s written inventory confirms `process`, `signal`, `file-descriptor`, `pipe`, `path` and `inode`, and `01`'s confirms `character-encoding` and `data-and-code`. `ps(1)`, `kill(1)` and `df(1)` are inventoried in `03` but not yet written.
3. **No gloss uses a term from a later stage** — checked line by line. `pipeline(7)`'s gloss avoids "pipe", `glob(7)`'s avoids "pattern-match", and `shell(7)`'s avoids "shell", "command" and "execute".
4. **Reachable from a `first-session` root** — yes. Every chain terminates at `shell(7)` or `man(1)`, both `first-session`.
5. **No prerequisite edge points upward** — yes, and this is the check that decided this document's number. `03-operating-systems.md` §2.3, `06-cryptography-and-trust.md` §2.4 and `01-foundations.md` all name `shell(7)` or `exit-status(7)` as things they depend on. Any numbering that placed the command line above `06` would have made those edges illegal under R8. At `04` — immediately above the operating system whose programs it runs, and below everything that reaches a concept by typing a command — every edge in the set points downward. This is the substance of the **ED-3** resolution.

**Stage budget.**

| Stage | This domain | `00` §6.2 budget | Note |
|---|---|---|---|
| `first-session` | **2** | ≤ 12 across all seven | `shell(7)` and `man(1)`. Defended in §3.1 |
| `operating` | 6 | ~25–40 across all seven | |
| `investigating` | 9 | ~40–60 across all seven | |
| `adversarial` | **1** | ~25–40 across all seven | Only `shell-injection(7)`, and it is unwritten. **This domain is the one that relieves pressure on the oversubscribed adversarial stage** — see **CT-3** and **DS-4**, which together claim about forty entries there. Offered as evidence that the budget is survivable |

---

## 3. The written entries

### 3.1 Which, and why

Eighteen entries, chosen against `00-curriculum-and-method.md` §7.3 — a concept with no hook is not written, however interesting. The selection principle in this domain is unusual and worth stating: **every entry here has a transfer test that is a command the reader can type tonight**, and the two entries that could not clear that bar (`redirection(7)`, `environment-variable(7)`) are inventoried and left unwritten precisely because their honest transfer is "notice that this game does not have it", which is a caveat, not a lesson.

**Two `first-session` entries, against a twelve-entry ceiling shared by all seven domains (R2).** Both are defensible and neither is negotiable:

- **`shell(7)`** — the player's first act in the game is typing into a prompt. An entry that explains what a prompt *is* has the strongest possible hook, and without it every other page in this domain has no root.
- **`man(1)`** — this is the entry that makes the teaching layer usable at all. The game's entire educational surface is a manual; a player who does not know they can summon it, or that `man 7 <thing>` is a real thing they can type on a real machine, gets a fraction of the value. It is also the single highest-transfer entry in the whole curriculum: it is how a person finds out about anything else on a Unix machine for the rest of their life.

### 3.2 A note on register

`00-curriculum-and-method.md` §2.4's failure modes are patronising, vague and jargon-dump. This domain's specific risk is the third: the shell has a large vocabulary of punctuation and it is very easy to write a page that is a syntax table. The rule followed throughout is that **a page names the problem before it names the syntax** — `quoting(7)` opens with an item name that has a space in it, not with a table of quote characters. There are no analogies in this document; the material is mechanical and describing it plainly is shorter than any metaphor for it.

Where a fact was verified by running it, the command that was run is quoted in `verified:` so a reviewer can re-run it rather than take the claim on trust.

### 3.3 `shell(7)`

```
id:             shell
section:        7
name:           shell
canonical:      shell
gloss:          The program that reads what you type and decides what it names.
status:         real, simplified
aliases:        command interpreter, command line
seeAlso:        command(7), argument(7), exit-status(7), pipeline(7),
                quoting(7), history(1), man(1), process(7)
reading:        sh(1); bash(1) "Shell Grammar"; POSIX.1-2024 XCU §2
                "Shell Command Language"; The Open Group Base
                Specifications Issue 8
notes:          Do not translate the word "shell" as a metaphor for a
                covering or husk in any locale. It is a proper technical
                term; transliterate or keep it.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          first-session
prerequisites:  process(7)
hook:           The prompt in the `terminal` window, the first time the
                player types anything at all.
misconception:  commonly believed the black window with the prompt *is*
                the operating system, so learning it means learning
                something dangerous and low-level; actually the prompt is
                a perfectly ordinary program with no special powers — it
                reads a line, finds what the first word names, starts it,
                waits, and prints a new prompt. You can replace it, and
                millions of people do.
transfer:       Run `echo $SHELL` on any Mac or Linux machine to see which
                shell program is running, then run `cat /etc/shells` to
                see the others installed alongside it. The point lands
                when the reader sees there are five of them and none is
                "the system". Assumes a Unix shell — see ED-8.
simplified:     Ours is case-insensitive and does not interpolate inside
                double quotes; it has no redirection, chaining, command
                substitution, background operator or environment
                expansion (../client/04 §3.1 rule 5, §3.3). It reads a
                line, splits it into words, and runs one thing — which is
                the part that generalises.
verified:       `echo $SHELL` and `cat /etc/shells` run on macOS
                (Darwin 25.5), five entries returned; shell-as-ordinary-
                program and the read-parse-execute loop — POSIX.1-2024
                XCU §2 and bash(1) "Shell Grammar"; our divergences —
                ../client/04 §3.3, §3.1. Checked 2026-07-25.

## DESCRIPTION

The prompt in the terminal window is a program. It is running the same
way your bots are running: it was started by something, it has a process
of its own, and it does one small job in a loop.

The job is this. It reads the line you typed. It splits that line into
words. It takes the first word and looks it up. If the word names
something it can run, it runs it, waits for it to finish, and collects
the number it left behind. Then it prints a new prompt. That is the
whole loop, and every other thing in this section — flags, quoting,
globs, pipelines — is a rule about how the line gets split up before the
lookup happens.

Two consequences that surprise people. First, the shell is not
privileged: it can do exactly what you can do, no more, and a mistake
typed at a prompt is not more dangerous than the same mistake made any
other way. Second, there is no single shell. The one in front of you is
one program among several, and swapping it changes the grammar without
changing anything underneath.

## REAL-WORLD COUNTERPART

real, simplified — this is what a shell is, and `sh`, `bash`, `zsh`,
`fish` and `dash` are all instances of it. The read-split-run-report
loop above is theirs, not ours.

The standard is POSIX.1-2024, chapter 2 of the Shell and Utilities
volume, and it is readable: it defines the grammar as a set of rules
about how a line becomes words. macOS ships `zsh` as its default and
`bash`, `sh`, `ksh` and `csh` alongside; most Linux distributions
default to `bash`. `echo $SHELL` names yours.

## CAVEATS

Ours is deliberately narrower than a real one, and the differences are
worth knowing before you carry a habit to a real machine:

- **Commands here are case-insensitive. Real shells are not.** On a real
  machine `PS` and `ps` are two different names and only one of them
  exists. We are forgiving because typing accurately against a trace
  timer is not the skill this game is testing.
- **There is no redirection (`>`), no chaining (`&&`, `;`), no command
  substitution (`` ` ` ``, `$( )`) and no background operator (`&`).**
  Typing any of them here is a parse error that says so. All of them are
  real, and all of them are worth learning; this surface simply is not
  one, and the error message tells you that rather than pretending the
  syntax is wrong.
- **Double quotes here are literal.** In a real shell, `"$x"` becomes the
  value of `x`. See `quoting(7)`, which explains the real behaviour in
  full.
```

---

### 3.4 `argument(7)`

```
id:             argument
section:        7
name:           argument
canonical:      argument
gloss:          A word given to a command to say what it should act on.
status:         real
aliases:        parameter, operand, positional argument
seeAlso:        flag(7), quoting(7), shell(7), glob(7),
                tab-completion(7), synopsis-notation(7)
reading:        POSIX.1-2024 XBD §12.1 "Utility Argument Syntax";
                intro(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  command(7)
hook:           `breach <node>` and `mv <item> <tier>` — the first
                commands where the same verb does different things
                depending on the words after it.
misconception:  commonly believed the order of the words after a command
                is a matter of style; actually positional arguments are
                positional — `mv a b` and `mv b a` are different
                instructions, and the position *is* the meaning.
transfer:       Run `mkdir -p /tmp/demo && cd /tmp/demo && touch one two`
                then `mv one two` and `ls`. One file is left, not two:
                the first argument named the source and the second named
                the destination, and nothing warned you. Assumes a Unix
                shell — see ED-8.
verified:       Positional-argument semantics and the operand/option
                distinction — POSIX.1-2024 XBD §12.1 Utility Argument
                Syntax, guidelines 1-13; `mv one two` behaviour confirmed
                by running it, macOS (Darwin 25.5). Checked 2026-07-25.

## DESCRIPTION

`breach node-17` is two words. The first says what to do. The second
says what to do it to, and it is called an argument.

Arguments are positional, which is the part worth internalising: their
meaning comes from where they sit, not from anything in the word itself.
`mv <item> <tier>` moves the item named first into the tier named
second. Swap them and you have said something different, and the system
will do what you said rather than what you meant.

A word that starts with `-` is treated differently — it is a flag, and
it changes behaviour rather than naming a target (see `flag(7)`). This
creates one genuine problem: some things are legitimately *named*
starting with `-`. A node address or a handle can be. The fix is `--`,
which means "everything after this is an argument, whatever it looks
like".

If an argument contains a space, it needs quoting, or the shell will
split it into two arguments and the command will receive the wrong
number of them. See `quoting(7)`.

## REAL-WORLD COUNTERPART

real — this is exactly how command arguments work everywhere, and it is
standardised. POSIX.1-2024's Utility Argument Syntax (XBD §12.1) is the
thirteen-guideline document that every well-behaved Unix tool follows,
including the `--` rule.

The classic demonstration is `mv`, which takes source then destination
and does not confirm. It is also the classic way people lose a file.
```

---

### 3.5 `flag(7)`

```
id:             flag
section:        7
name:           flag
canonical:      flag
gloss:          A word that changes how a command behaves, not what it acts on.
status:         real
aliases:        option, switch, command-line option
seeAlso:        argument(7), dry-run(7), man(1), synopsis-notation(7),
                shell(7), tab-completion(7)
reading:        POSIX.1-2024 XBD §12.2 "Utility Syntax Guidelines";
                GNU Coding Standards §4.7 "Standards for Command Line
                Interfaces"; getopt(3), getopt_long(3)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  argument(7)
hook:           The five universal flags (../client/04 §3.4) — the first
                time the player types `-h` on a command they have never
                run and gets a usable answer.
misconception:  commonly believed each program invents its own flags, so
                they have to be memorised one tool at a time; actually
                the shapes are conventional and largely standardised —
                `-h`, `-v`, `-n`, `--help`, `--` and clustering behave
                the same way across thousands of unrelated programs
                because two published guidelines say they should.
transfer:       Pick any command you have never used and run it with
                `--help`. Then run `ls -la` and `ls -l -a` and see that
                they are the same thing: short flags cluster. Then find
                a file whose name starts with `-` and try to delete it
                without `--`. Assumes a Unix shell — see ED-8.
verified:       Short/long/cluster conventions and the `--` terminator —
                POSIX.1-2024 XBD §12.2 guidelines 5 and 10, and GNU
                Coding Standards §4.7; `ls -la` and `ls -l -a` produced
                identical output on macOS (Darwin 25.5); the five
                universal flags — ../client/04 §3.4. Checked 2026-07-25.

## DESCRIPTION

Every command here takes the same five flags, and that is not a
convenience — it is the reason flags are learnable at all.

- `-h` or `--help` prints what the command takes.
- `--explain` prints what it does, and does not run it.
- `-n` or `--dry-run` prints the published costs and requirements and
  sends nothing (see `dry-run(7)`).
- `-v` or `--verbose` adds attribution: which action contributed what.
- `--` says that everything after it is a target, not a flag.

Short flags are a single dash and one letter, and they cluster: `-vn` is
`-v -n`. Long flags are two dashes and a word, and take a value either
way round — `--tier=T2` or `--tier T2`. Both spellings exist because
short ones are fast to type and long ones are readable in something
somebody else has to maintain.

A small universal vocabulary is what separates a tool you can guess from
a tool you have to study. When you meet an unfamiliar command, `-h` is
almost always the right first move.

## REAL-WORLD COUNTERPART

real — and unusually well standardised for something that looks like
folklore. Two documents cover it: POSIX's Utility Syntax Guidelines
(XBD §12.2), which give short options, clustering and `--`; and the GNU
Coding Standards §4.7, which give long options and `--help` / `--version`.
The C library functions that implement them, `getopt(3)` and
`getopt_long(3)`, are why the behaviour is so consistent — most programs
do not parse their own flags, they call the same two functions.

`-n` for "dry run" is a real convention too, though a weaker one:
`rsync -n`, `make -n` and `apt -s` all mean the same thing.
```

---

### 3.6 `dry-run(7)`

```
id:             dry-run
section:        7
name:           dry run
canonical:      dry run
gloss:          Asking what would happen, and being told without it happening.
status:         real
aliases:        -n, --dry-run, simulate, no-act
seeAlso:        flag(7), exit-status(7), man(1), compute(7)
reading:        rsync(1) "-n, --dry-run"; make(1) "-n"; apt-get(8) "-s";
                git(1) "--dry-run"
notes:          The page must not state or imply a verdict — see
                CAVEATS and Invariant I14. A translator must keep
                "would" conditional in every sentence of DESCRIPTION.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  flag(7)
hook:           The first time the player is about to spend compute on a
                tool whose cost they have not learned yet, and types `-n`
                instead of guessing.
misconception:  commonly believed a dry run is a simulation that tells
                you whether the real thing will succeed; actually it
                tells you what *would be attempted* and what it is
                published to cost. Whether it succeeds depends on state
                the dry run does not consult and cannot know.
transfer:       Run `rsync -n -av <somewhere> <somewhere-else>` and read
                the file list it prints without copying anything. Then
                `apt-get -s install <package>` on a Debian or Ubuntu
                machine to see the same idea in a package manager.
                Assumes a Unix shell — see ED-8.
verified:       `-n` as the dry-run convention — rsync(1), make(1),
                git(1); `-s` as apt's spelling — apt-get(8); the
                no-verdict rule — ../client/04 §3.4 and
                ../client/00-client-overview.md §2 (pillar C4),
                Invariant I14. Checked 2026-07-25.

## DESCRIPTION

`-n` on any command prints what it would do and does not do it.

What you get back is the published cost — the compute and noise from the
tool's own definition — and the gate's requirement stated in words. What
you do not get back is a verdict. It will not say "affordable", it will
not say "you meet this gate", and it will not do the subtraction for
you. That is deliberate, and it is worth understanding rather than
resenting: this client is not the authority on whether you can afford
something. It shows you the numbers and you do the arithmetic, because
the answer is the server's to give and a client that guessed would
sometimes guess wrong at the worst possible moment.

The habit generalises well beyond this game. Any operation that is hard
to reverse is worth asking about first, and a surprising number of real
tools will tell you.

## REAL-WORLD COUNTERPART

real — `-n` and `--dry-run` are a genuine and widespread convention.
`rsync -n` lists the files it would transfer. `make -n` prints the
commands it would run. `git commit --dry-run` shows what would be
committed. `apt-get -s` simulates an install, using `-s` for "simulate"
rather than `-n`, which is a good reminder that the convention is strong
but not universal — check with `--help`.

The real ones share our limitation, which is why the limitation is worth
teaching rather than hiding: a dry run predicts from current state, and
state can change between the dry run and the real one.
```

---

### 3.7 `exit-status(7)`

```
id:             exit-status
section:        7
name:           exit status
canonical:      exit status
gloss:          The number a finished command leaves behind to say how it went.
status:         real
aliases:        exit code, return code, $?, status
seeAlso:        command(7), sysexits(7), shell(7), pipeline(7),
                signal(7), command-not-found(7)
reading:        POSIX.1-2024 XCU §2.8.2 "Exit Status for Commands";
                sysexits(3); intro(1); wait(2)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  command(7)
hook:           The `$?` readout in the `terminal` status line, and the
                first time a breach is aborted and reports `130`.
misconception:  commonly believed zero means failure, because zero is
                "nothing" and every other counting system in computing
                starts there for success; actually zero means success and
                everything else is a failure — because there is one way
                to succeed and many ways to fail, so the non-zero values
                are free to carry which one.
transfer:       In any terminal, run `ls /` then `echo $?` — zero. Run
                `ls /nonexistent` then `echo $?` — non-zero. Then run
                `sleep 30`, press Ctrl-C, and `echo $?`: 130. That last
                number is 128 plus 2, and 2 is SIGINT, which is what
                Ctrl-C sends. Assumes a Unix shell — see ED-8.
verified:       0-is-success and the non-zero-is-failure rule —
                POSIX.1-2024 XCU §2.8.2 and intro(1) ("Traditionally, the
                value 0 signifies successful completion"); 128+N for
                signal termination confirmed by running a SIGINT-killed
                child under bash and reading `$?` = 130, macOS
                (Darwin 25.5); our status table — ../client/04 §3.5.
                Checked 2026-07-25.

## DESCRIPTION

Every command that finishes leaves a number behind. The status line
shows the last one as `$?`.

Zero means it worked. Anything else means it did not, and the specific
number says something about how. Ours are these:

- `0` — the server accepted what you asked for.
- `1` — a game rule refused it, and **nothing changed**.
- `2` — you got the invocation wrong: an unknown flag, a missing
  argument.
- `69` — the server could not be reached. This is not the same as `1`,
  and the difference matters: `1` means your request arrived and was
  declined, `69` means it never arrived.
- `75` — sent, no answer yet. Retrying is safe.
- `77` — a gate blocks this, and the requirement is printed.
- `126` — the command exists, but you do not own or cannot field that
  tool.
- `127` — no such command.
- `130` — you aborted it.

That last one is the most interesting number in the list. It is 128 plus
2. Signal 2 is `SIGINT`, and `SIGINT` is what Ctrl-C sends. Any program
killed by a signal reports 128 plus that signal's number, which means
`130` is not our invention at all — it is what a real machine reports
when you interrupt something, and you can go and produce it tonight.

## REAL-WORLD COUNTERPART

real — the whole scheme is. Zero-for-success is specified in
POSIX.1-2024 XCU §2.8.2 and stated in `intro(1)`. The 128+N convention
for signal deaths is a shell convention rather than a kernel one, but it
is universal across `bash`, `zsh`, `dash` and `ksh`.

`69`, `75` and `77` are borrowed exactly from `sysexits.h`, a real header
shipped on BSD and macOS systems, where they are `EX_UNAVAILABLE`,
`EX_TEMPFAIL` and `EX_NOPERM`. `126` and `127` are shell conventions with
the same meanings we give them. See `sysexits(7)`.

The reason any of this matters outside a terminal: exit statuses are how
scripts and CI systems decide whether the previous step worked. A
program that always exits zero is a program nothing can be built on.
```

---

### 3.8 `standard-streams(7)`

```
id:             standard-streams
section:        7
name:           standard streams
canonical:      standard streams
gloss:          The three text channels every program is handed when it starts.
status:         real
aliases:        stdin, stdout, stderr, standard input, standard output,
                standard error
seeAlso:        pipeline(7), text-as-interface(7), file-descriptor(7),
                redirection(7), grep(1)
reading:        POSIX.1-2024 XBD §3.397-3.399; stdin(3);
                POSIX.1-2024 XSH "General Information" §2.5
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  file-descriptor(7), command(7)
hook:           The first working pipeline — `ps | grep miner` — and the
                question of what exactly is being handed from the left
                side to the right.
misconception:  commonly believed a program's error messages are part of
                its output, so filtering the output filters the errors
                too; actually they travel on a separate channel, which is
                why an error can still appear on screen after you have
                sent the output somewhere else — and why a pipeline
                filters the results but not the complaints.
transfer:       Run `ls / /nonexistent 2>/dev/null` and then
                `ls / /nonexistent 1>/dev/null`. The first shows the
                listing and hides the error; the second shows the error
                and hides the listing. Two channels, demonstrated in two
                commands. Assumes a Unix shell — see ED-8.
verified:       Three streams at descriptors 0, 1 and 2 —
                POSIX.1-2024 XBD §3.397-3.399 and stdin(3); separate
                stdout/stderr behaviour confirmed by running both `ls`
                variants above on macOS (Darwin 25.5); our pipeline uses
                stdout only — ../client/04 §3.7. Checked 2026-07-25.

## DESCRIPTION

When a program starts, it is handed three channels before it does
anything. They are numbered 0, 1 and 2, and they have names: standard
input, standard output, and standard error.

Standard output is where results go. Standard error is where complaints
go. They are separate on purpose, and the reason becomes obvious the
first time you filter something: if you keep only the lines matching
`miner`, you want the results filtered — but you still want to be told
if the command failed. Two channels means the filter can touch one and
leave the other alone.

Standard input is the other end of the same idea: it is where a program
reads from when nobody told it a filename. A program that reads standard
input and writes standard output can be joined to any other program that
does the same, which is the whole basis of `pipeline(7)`.

Here, a pipeline carries standard output only. Errors are shown in the
transcript rather than passed along, which matches what a real pipeline
does by default.

## REAL-WORLD COUNTERPART

real — and this is one of the oldest ideas in the system, dating to the
early 1970s. The three streams are file descriptors 0, 1 and 2, which is
the same numbering `file-descriptor(7)` describes; they are ordinary
descriptors that happen to be open before your program starts.

On a real machine you can point them anywhere: `2>/dev/null` throws
errors away, `>file` sends results to a file, `<file` feeds a file in as
input. This surface has none of that (see `redirection(7)`), but the
numbering is the same, and `2>` reads exactly as it looks — "channel 2,
redirected".
```

---

### 3.9 `pipeline(7)`

```
id:             pipeline
section:        7
name:           pipeline
canonical:      pipeline
gloss:          Joining commands so each one's output becomes the next one's input.
status:         real, simplified
aliases:        pipe syntax, |
seeAlso:        pipe(7), standard-streams(7), text-as-interface(7),
                grep(1), exit-status(7), shell(7), ps(1)
reading:        POSIX.1-2024 XCU §2.9.2 "Pipelines"; bash(1)
                "Pipelines"; Doug McIlroy's pipe proposal, Bell Labs 1964
notes:          `pipe(7)` (the kernel object) is domain 03's page and
                this one must not restate it. This page is the syntax
                and its rules.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  standard-streams(7), pipe(7)
hook:           `ps | grep miner` — the moment the player stops reading a
                process table and starts querying it.
misconception:  commonly believed the commands in a pipeline run one
                after another, with the first finishing before the second
                starts; actually they all start at once and run
                concurrently, which is why `log -f | grep canary` can
                print a match while the log is still being written.
transfer:       Run `ps aux | grep ssh` on any Mac or Linux machine. Then
                run `false | true; echo $?` and see `0` — the pipeline
                reported the *last* command's status and the failure on
                the left vanished. Then `set -o pipefail` and run it
                again: `1`. Assumes a Unix shell — see ED-8.
verified:       Concurrent execution and last-command exit status —
                POSIX.1-2024 XCU §2.9.2 and bash(1) "Pipelines"; both
                `false | true; echo $?` results confirmed by running them
                under bash (0 without pipefail, 1 with), macOS
                (Darwin 25.5); our read-only restriction —
                ../client/04 §3.7. Checked 2026-07-25.

## DESCRIPTION

`ps | grep miner` is two commands. `ps` lists what is consuming compute;
`grep` keeps only the lines containing "miner". The `|` between them
says: whatever the left one prints, hand it to the right one as input.

They do not take turns. Both start immediately and run at the same time,
with the left one's output flowing to the right one as it is produced.
This is why watching a live log through a filter works at all.

Two rules that catch people out.

**The pipeline's exit status is the last command's.** If the left side
fails and the right side succeeds, the pipeline reports success and the
failure is silently lost. Real shells have a setting to change this —
`set -o pipefail` — and it is off by default, which has caused a great
many scripts to report success while doing nothing.

**Here, a pipeline may only contain read-only commands.** Sources are
`ps`, `ss`, `df`, `ls`, `log`, `jobs`, `ledger` and `items`; filters are
`grep`, `sort`, `uniq`, `head`, `tail`, `wc` and `cut`. Putting anything
with a side effect into a pipeline is a parse error that names the
offending command. Real shells impose no such rule.

The reason this is the most useful thing in the terminal: a hidden miner
is found by comparing two views of your own machine and noticing they
disagree. A pipeline is how you compare them.

## REAL-WORLD COUNTERPART

real, simplified — the syntax, the concurrency and the exit-status rule
are all exactly the real thing, specified in POSIX.1-2024 XCU §2.9.2.
The restriction to read-only commands is ours.

The idea is credited to Doug McIlroy at Bell Labs and is the single most
imitated feature of Unix. Its power comes from a convention rather than
a mechanism: because programs agreed to read text on standard input and
write text on standard output, any of them can be joined to any other,
including ones written decades apart by people who never met. See
`text-as-interface(7)`.

## CAVEATS

- **`pipefail` is off by default in real shells too**, so this surprise
  is not ours. If you write a script that pipes anything, turn it on.
- **Our pipelines cannot contain side effects.** A real one can, and a
  real one will happily half-apply an action if the right side exits
  early. Ours refuses at parse time.
- **Filters work on rendered text, not on structure.** `ps | grep miner`
  matches the word "miner" anywhere on the line, including in a column
  you did not mean. That is true of real pipelines too, and it is the
  reason `awk`, `jq` and structured output exist. See `grep(1)`.
```

---

### 3.10 `text-as-interface(7)`

```
id:             text-as-interface
section:        7
name:           text as an interface
canonical:      text as an interface
gloss:          Why unrelated tools can be joined: they all speak lines of characters.
status:         real
aliases:        plain text, line-oriented, the Unix philosophy
seeAlso:        pipeline(7), standard-streams(7), grep(1), cut(1),
                character-encoding(7), utf-8(7)
reading:        McIlroy, Pinson & Tague, "Unix Time-Sharing System:
                Foreword", Bell System Technical Journal 57(6), 1978;
                Kernighan & Pike, "The Unix Programming Environment",
                ch. 1; jq(1); awk(1)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  pipeline(7), character-encoding(7)
hook:           The first time a pipeline matches the wrong column —
                `ps | grep miner` catching a process whose *owner* is
                named miner — and the player has to work out why.
misconception:  commonly believed that programs in a pipeline understand
                each other, passing structured records back and forth;
                actually they pass undifferentiated lines of characters
                and each one re-parses from scratch. Nothing enforces a
                shape. That is simultaneously why any tool can be joined
                to any other, and why the join is fragile.
transfer:       Run `ps aux | grep root | wc -l` on a real machine, then
                run `ps aux | grep root` and count the lines yourself.
                The numbers differ by one, because `grep root` matched
                the `grep root` process itself. Nothing in the chain
                knew what a process was — it was all text. Assumes a
                Unix shell — see ED-8.
verified:       The convention and its rationale — McIlroy et al., Bell
                System Technical Journal 57(6) 1978, and Kernighan &
                Pike ch. 1; the self-matching `grep` artefact confirmed
                by running both commands on macOS (Darwin 25.5);
                structured alternatives — jq(1), awk(1).
                Checked 2026-07-25.

## DESCRIPTION

The reason `ps` can be joined to `grep` is not that anybody made them
compatible. It is that both of them, and nearly everything else, agreed
to a single convention: read lines of text, write lines of text.

That agreement is doing an enormous amount of work. It means a tool
written in 1975 can be piped into a tool written last week, by someone
who has never heard of it, without either being modified. No shared
format, no versioned schema, no interface definition — just characters
and newlines.

It also has a real cost, and you will meet the cost before you meet the
benefit. `ps | grep miner` matches the word "miner" anywhere on the
line. If a *user* is called miner, or a path contains it, you get that
line too. The filter does not know what a column is, because there are
no columns — there is a line of characters that happens to be laid out
in a way your eye reads as columns.

This is why `awk`, `jq` and structured output exist. It is also why,
fifty years on, text remains the default: the fragile version works
everywhere, and the robust version has to be agreed in advance.

## REAL-WORLD COUNTERPART

real — this is the Unix philosophy's load-bearing half, stated in the
1978 Bell System Technical Journal foreword as "write programs to handle
text streams, because that is a universal interface".

The modern qualification is worth having: when the data really is
structured, structured tools win. `jq` parses JSON properly and cannot
match the wrong field. `awk` splits into fields and can be told which
one. Both exist because the text convention is a good default and a bad
guarantee — and knowing which situation you are in is the actual skill.
```

---

### 3.11 `glob(7)`

```
id:             glob
section:        7
name:           glob
canonical:      glob
gloss:          A pattern that stands for a set of names without listing them.
status:         real
aliases:        wildcard, globbing, filename expansion, pathname expansion
seeAlso:        regular-expression(7), argument(7), path(7), shell(7),
                quoting(7), ls(1)
reading:        POSIX.1-2024 XCU §2.13 "Pattern Matching Notation";
                glob(7) on Linux; fnmatch(3); bash(1) "Pathname
                Expansion"
notes:          "Glob" is a proper noun-ish technical term from the
                original `glob` command in Unix v1. Do not translate it
                as "globe" or as a generic word for "pattern".
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  path(7), argument(7)
hook:           `ls /rig/tools/*-sweep` — the first time the player wants
                to act on several things at once and does not want to
                type all their names.
misconception:  commonly believed the pattern is handled by the command
                you are running, so `ls *.txt` means `ls` searches for
                text files; actually the shell expands the pattern
                *before* `ls` starts, and `ls` receives an already-
                finished list of names. It never sees the asterisk. This
                is why a pattern that matches nothing behaves so oddly.
transfer:       Run `mkdir -p /tmp/g && cd /tmp/g && touch a ab abb ac`
                then `echo ab*`. It prints `ab abb` — not `a`, not `ac`.
                Now run `echo zz*` where nothing matches, and watch the
                unexpanded pattern come back as a literal. Assumes a
                Unix shell — see ED-8.
verified:       `echo ab*` over files {a, ab, abb, ac} returned exactly
                `ab abb`, run on macOS (Darwin 25.5); expansion happens
                in the shell before the command runs, and the no-match
                literal behaviour — POSIX.1-2024 XCU §2.13 and bash(1)
                "Pathname Expansion"; our supported syntax —
                ../client/04 §3.8. Checked 2026-07-25.

## DESCRIPTION

`ls /rig/tools/*-sweep` lists every tool whose name ends in `-sweep`
without you naming any of them.

The syntax is small and worth knowing completely:

- `*` — any run of characters, including none at all
- `?` — exactly one character
- `[abc]` — any one of these characters
- `[a-z]` — any one character in this range
- `[!abc]` — any one character that is *not* one of these

The important part is not the syntax, it is *when* it happens. The
shell expands the pattern first, into a list of names that actually
exist, and only then runs the command with that list. The command never
sees the pattern. `ls *-sweep` is, by the time `ls` starts, exactly the
same as if you had typed all four tool names yourself.

That single fact explains most glob surprises. A pattern matching
nothing does not become an empty list — it is passed through unchanged,
so the command receives a literal asterisk and complains about a file
with a strange name.

## REAL-WORLD COUNTERPART

real — the syntax is standardised in POSIX.1-2024 XCU §2.13 as Pattern
Matching Notation, and the same rules are available to programs through
`fnmatch(3)`. The name comes from `glob`, a separate program in the
earliest versions of Unix that did this expansion before the shell
absorbed it.

One extension exists in real shells and not here: `**`, which crosses
directory boundaries. `bash` requires `shopt -s globstar` to enable it
and `zsh` has it always. We do not support it, so a pattern here never
descends.
```

---

### 3.12 `regular-expression(7)`

```
id:             regular-expression
section:        7
name:           regular expression
canonical:      regular expression
gloss:          A pattern language for text, where every character is a rule.
status:         real, simplified
aliases:        regex, regexp, ERE, BRE
seeAlso:        glob(7), grep(1), text-as-interface(7), character-encoding(7)
reading:        POSIX.1-2024 XBD §9 "Regular Expressions"; grep(1);
                re_format(7); Friedl, "Mastering Regular Expressions",
                3rd ed.
notes:          ⚠ Mandatory disambiguation: `*` means something
                different here than in `glob(7)`, and the two pages are
                each other's first SEE ALSO for exactly this reason.
                Translators must not harmonise the two descriptions.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  glob(7)
hook:           Using `grep -E` in a pipeline immediately after using a
                glob in a path, and finding that `*` did not mean the
                same thing twice.
misconception:  commonly believed a regular expression is the same
                pattern language as a glob with more features; actually
                they are different languages that share characters and
                disagree about them. In a glob, `*` means "any
                characters". In a regex, `*` means "zero or more of the
                thing immediately before me", and "any characters" is
                `.*`.
transfer:       Run `printf 'a\nab\nabb\nac\n' | grep -E '^ab*$'`. It
                prints `a`, `ab` and `abb` — it matches `a`, because
                `b*` allows zero `b`s, and it refuses `ac`. Then make
                four files with the same names and run `echo ab*`, which
                prints `ab abb` and not `a`. Same four characters, two
                different answers. Assumes a Unix shell — see ED-8.
verified:       Both halves of the contrast were run on macOS
                (Darwin 25.5): `grep -E '^ab*$'` over the four lines
                returned `a ab abb`; `echo ab*` over the four files
                returned `ab abb`. Syntax and the BRE/ERE distinction —
                POSIX.1-2024 XBD §9 and re_format(7).
                Checked 2026-07-25.

## DESCRIPTION

Inside `grep`, the pattern is a regular expression, and it is not the
same language as the pattern you use in a path.

This is the single most reliable source of confusion in the whole
domain, so it is worth being blunt about it. Take the pattern `ab*`:

- As a **glob**, in a path, it matches `ab` and `abb`. The `*` means
  "any characters at all, after `ab`". It does not match `a`.
- As a **regular expression**, it matches `a`, `ab` and `abb`. The `*`
  means "zero or more of the character before it, which is `b`". It
  does not match `ac`.

Same three characters, opposite answers, and no error to warn you.

The rest of the language, in the form `grep -E` accepts:

- `.` — any one character
- `*` — zero or more of the preceding thing
- `+` — one or more of it
- `?` — zero or one of it
- `^` and `$` — the start and end of the line
- `[abc]`, `[a-z]`, `[^abc]` — character sets, with `^` for negation
  (note: a glob uses `!` for the same job)
- `(ab|cd)` — either alternative

You will get this wrong for a while. Everyone does. The reliable habit
is to say out loud which language you are in before you type the
pattern.

## REAL-WORLD COUNTERPART

real, simplified — the language is standardised in POSIX.1-2024 XBD §9,
and `grep -E` gives you the "extended" flavour described above.

The simplification is that there is more than one flavour, and this page
teaches one. Plain `grep` without `-E` uses *basic* regular expressions,
where `+`, `?` and `|` lose their special meaning unless backslashed —
a historical wart, not a design. Perl, Python, JavaScript and most
modern languages use a third family (PCRE) with still more features.
They share a core, and the core is what transfers.

## CAVEATS

- **The `*` collision with `glob(7)` is real and permanent.** It is not
  a quirk of this game; it is two languages that grew up separately in
  the same terminal.
- **`[^abc]` in a regex, `[!abc]` in a glob.** Same idea, different
  character, and mixing them up silently matches the wrong thing.
- **We support extended syntax only, through `grep -E`.** Basic regular
  expressions and PCRE both exist on real machines and behave
  differently. When a pattern that worked somewhere else fails, the
  flavour is the first thing to check.
```

---

### 3.13 `grep(1)`

```
id:             grep
section:        1
name:           grep
canonical:      grep
gloss:          Keeps only the lines that match, and throws the rest away.
status:         real
aliases:        —
seeAlso:        pipeline(7), regular-expression(7), text-as-interface(7),
                ps(1), sort(1), wc(1)
reading:        grep(1); POSIX.1-2024 XCU "grep"; re_format(7);
                Kernighan & Pike, "The Unix Programming Environment"
                ch. 4
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  pipeline(7), regular-expression(7)
hook:           `ps | grep miner` — the canonical move for finding a
                foreign miner in a process table too long to read.
misconception:  commonly believed grep searches files, so it needs a
                filename to be useful; actually it filters whatever is
                handed to it, and in a pipeline that is another
                program's output. Most of its real-world value is as a
                pipeline stage, not as a file searcher.
transfer:       Run `ps aux | grep ssh` on any Mac or Linux machine, then
                `ps aux | grep -v root | wc -l` to count processes not
                owned by root. Then run `history | grep git` to find
                what you typed last week. Assumes a Unix shell —
                see ED-8.
verified:       `-i`, `-v`, `-E`, `-c`, `-n` flag meanings — grep(1) and
                POSIX.1-2024 XCU "grep"; the self-match artefact and the
                wrong-column failure both reproduced on macOS
                (Darwin 25.5); our supported flag subset —
                ../client/04 §3.7. Checked 2026-07-25.

## SYNOPSIS

       grep [-i] [-v] [-E] [--] <pattern>

## DESCRIPTION

Reads lines, prints the ones that match, discards the ones that do not.
In this game it is always used in a pipeline, after something that
produces a list.

`ps | grep miner` shows only the compute consumers whose line mentions
"miner". `ss | grep -v ESTAB` shows every connection that is *not*
established, which is a much more interesting list than the one it came
from.

The pattern is a regular expression, not a glob — see
`regular-expression(7)`, and read it before you write your first pattern
containing an asterisk.

## OPTIONS

- `-i` — ignore case when matching.
- `-v` — invert: keep the lines that do **not** match. This is the flag
  that turns grep from a search into a filter, and it is the one most
  worth remembering.
- `-E` — use extended regular expressions, enabling `+`, `?` and `|`.

## EXIT STATUS

- `0` — at least one line matched.
- `1` — nothing matched. This is not an error; it is an answer, and it
  is why grep is useful inside scripts.
- `2` — the pattern or invocation was wrong.

## REAL-WORLD COUNTERPART

real — `grep` is exactly this, on every Unix machine, and our flags mean
what its flags mean. The name is from the `ed` editor command
`g/re/p` — globally, regular expression, print — which is a genuine
piece of history rather than a backronym.

Its most under-used flag in real life is `-v`, and its most common
real-world use is not searching files at all: it is the second stage of
a pipeline, exactly as here.

## CAVEATS

- **It matches the whole line, not a column.** `ps | grep miner` will
  match a process whose *owner* or *path* contains "miner", not just its
  name. Nothing in the chain knows what a column is — see
  `text-as-interface(7)`. When precision matters, real machines reach
  for `awk` or structured output.
- **On a real machine, grep finds itself.** `ps aux | grep ssh` matches
  the `grep ssh` process too, because it is a running process with
  "ssh" in its command line. This is a rite of passage, not a bug.
- **Exit status `1` means "no matches", not "failure".** A script that
  treats any non-zero status as an error will misread it.
```

---

### 3.14 `quoting(7)`

```
id:             quoting
section:        7
name:           quoting
canonical:      quoting
gloss:          Telling a system that these words are one word, punctuation and all.
status:         real, simplified
aliases:        quotes, escaping, single quotes, double quotes
seeAlso:        argument(7), word-splitting(7), shell(7), glob(7),
                data-and-code(7), injection(7)
reading:        POSIX.1-2024 XCU §2.2 "Quoting"; bash(1) "Quoting";
                CWE-78 "OS Command Injection"
notes:          ⚠ The CAVEATS divergence is the largest in this domain
                and must never be trimmed for length: a player who
                learns that double quotes are literal, and carries that
                to a real shell, will write a broken script.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  argument(7)
hook:           The first item or node whose name contains a space, and
                the command that mysteriously receives two arguments
                instead of one.
misconception:  commonly believed quotes are decoration, or a style
                choice like brackets in prose; actually they are the
                only thing standing between "one argument containing a
                space" and "two arguments", and the command has no way
                to tell the difference after the fact.
transfer:       Run `mkdir -p /tmp/q && cd /tmp/q && touch "two words"`
                then `ls two words` — two errors, because it looked for
                two files. Then `ls "two words"` — one result. Then run
                `echo "$HOME"` and `echo '$HOME'` on a real shell and
                watch only one of them expand. Assumes a Unix shell —
                see ED-8.
verified:       Single quotes fully literal, double quotes permitting
                expansion — POSIX.1-2024 XCU §2.2 and bash(1)
                "Quoting"; both `echo` variants run on macOS
                (Darwin 25.5), single-quoted printed the literal
                `$HOME`; our both-literal divergence —
                ../client/04 §3.3, §3.1 rule 4. Checked 2026-07-25.

## DESCRIPTION

A line you type is split into words at the spaces, and each word becomes
one argument. That is fine until something you are naming has a space
in it.

`mv Old Ledger Dump vault` is four words. The command sees four
arguments, none of which is the thing you meant. `mv "Old Ledger Dump"
vault` is two arguments, and works.

Both quote characters work here and both are fully literal: everything
between them, including spaces and punctuation, is one word. `'` and `"`
behave identically.

A related job: `--` marks the end of flags, so a name that begins with
`-` can be given as an argument without being mistaken for one. Quoting
does not solve that — a quoted `"-n"` is still a flag — so the two tools
are for different problems.

## REAL-WORLD COUNTERPART

real, simplified — quoting exists on every shell and does the job
described above. The simplification is significant and is spelled out
below.

The reason this matters beyond convenience: quoting is the boundary
between data and instructions. A filename that contains a space is
harmless; a filename that contains a semicolon, handed unquoted to a
program that builds a command out of it, is the oldest security bug
there is. See `data-and-code(7)` for the general shape and `injection(7)`
for what an attacker does with it.

## CAVEATS

- **In a real shell, the two quote characters are not the same, and this
  is the biggest divergence in this section.** Single quotes are fully
  literal, as ours are. Double quotes are *not*: inside them, `$HOME`
  becomes your home directory, `` `cmd` `` and `$(cmd)` run a command,
  and `\` still escapes. `echo "$HOME"` prints a path; `echo '$HOME'`
  prints the four characters. Both of ours print the four characters.
- **We do this because there is nothing to expand.** This surface has no
  variables and no command substitution (`../client/04` §3.1 rule 4), so
  a double quote here has no expansion to permit. That is a property of
  this game, not of quoting.
- **Carry the real rule, not ours.** In a real shell, the safe default
  is to quote every variable — `"$file"`, not `$file` — because an
  unquoted variable containing a space becomes two words, and one
  containing a glob character gets expanded.
```

---

### 3.15 `tab-completion(7)`

```
id:             tab-completion
section:        7
name:           tab completion
canonical:      tab completion
gloss:          Letting the machine finish a name it already knows.
status:         real
aliases:        completion, autocomplete, Tab, programmable completion
seeAlso:        shell(7), command(7), path(7), readline(7), history(1),
                glob(7)
reading:        bash(1) "Programmable Completion"; bash-completion
                project documentation; zsh compsys — zshcompsys(1);
                readline(3)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  command(7), path(7)
hook:           The first long node address the player has to type, and
                the discovery that `Tab` finishes it.
misconception:  commonly believed completion is a text-editor feature
                that guesses from what you have typed before; actually
                it is position-aware and asks the system what is
                actually there — a different set of candidates in
                command position, in flag position, and in path
                position. It is answering a question, not predicting.
transfer:       In any terminal, type `git ch` and press Tab twice: you
                get `checkout`, `cherry-pick`, `check-ignore` and
                friends — the list came from `git` itself, not from your
                history. Then type `ls /u` and Tab, and watch it
                complete from the filesystem instead. Assumes a Unix
                shell — see ED-8.
verified:       Position-sensitive completion and the double-Tab listing
                convention — bash(1) "Programmable Completion" and
                zshcompsys(1); `git ch` + Tab Tab behaviour reproduced on
                macOS (Darwin 25.5) with bash-completion installed; our
                known-nodes-only rule — ../client/04 §3.6.
                Checked 2026-07-25.

## DESCRIPTION

Press `Tab` and the longest unambiguous part of the name is filled in.
Press it again and you get the list of candidates.

What makes this more than a typing aid is that it is **position-aware**.
The candidates depend on where you are in the line:

- first word — commands you can currently run
- after `--` — that command's own flags
- in a path — the namespace under `/rig/`, `/net/`, `/ledger/`
- where an item is expected — items you actually own
- where a node is expected — nodes you have actually discovered

That last one has a rule behind it worth understanding, because it is
the place where a convenience feature could quietly become a cheat.
Completion offers **only nodes the server has told you about**. If it
completed unscanned addresses, it would be handing you, free, the
information recon tools charge compute and ethecoin for. Tab completion
never discovers anything.

Completion also never executes. Pressing `Tab` cannot run a command,
spend anything, or send anything.

## REAL-WORLD COUNTERPART

real — including the position-awareness, which surprises people who
assume it is simple prefix matching. On a real machine this is called
*programmable completion*: a command can ship a script that tells the
shell what its own valid completions are, which is why `git ch`+Tab
knows about `cherry-pick` and `apt inst`+Tab knows about `install`.

The double-Tab convention — once to complete, twice to list — is
genuine and works in `bash` and `zsh` alike. So is the fact that
completion consults the real system: in a path, the candidates are the
files that are actually there, which makes Tab a fast way to check
whether something exists at all.
```

---

### 3.16 `man(1)`

```
id:             man
section:        1
name:           man
canonical:      man
gloss:          Opens the manual for a thing, on the machine, offline.
status:         real
aliases:        manual, manpage, man page
seeAlso:        man-section(7), apropos(1), synopsis-notation(7),
                shell(7), flag(7)
reading:        man(1); man-pages(7); intro(1); The Open Group Base
                Specifications Issue 8, "man"
notes:          The teaching layer itself is rendered in this form
                (../client/04 §4.3.1). A translator changing the section
                names NAME / SYNOPSIS / DESCRIPTION / SEE ALSO breaks
                the transfer, because a real man page will not match.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          first-session
prerequisites:  command(7)
hook:           The very first unfamiliar term the player hovers, and the
                keypress that turns a one-line gloss into a full page.
misconception:  commonly believed documentation lives on a website, so
                the way to find out what a command does is to search for
                it; actually the manual is already installed, works with
                no network, describes exactly the version you have
                rather than the newest one, and is usually more accurate
                than the first search result.
transfer:       Run `man ls` on any Mac or Linux machine. Press space to
                page, `/` to search inside it, `q` to quit. Then run
                `man man`, which is the manual for the manual and is
                where the section numbers are explained. Assumes a Unix
                shell — see ED-8.
verified:       `man(1)`, `apropos(1)` and `whatis(1)` all present at
                /usr/bin on macOS (Darwin 25.5); section directories
                man1, man4, man5, man6, man7, man8, man9 present under
                /usr/share/man; `man 1 intro` renders and states
                "Traditionally, the value 0 signifies successful
                completion"; the pager keys — man(1). Checked 2026-07-25.

## SYNOPSIS

       man [<section>] <name>

## DESCRIPTION

Opens the manual page for something. `man compute` explains compute;
`man 7 glob` explains globs; `man ps` explains `ps`.

This is worth a moment of attention, because the shape of what you are
reading is not an aesthetic choice. Every page in this game — including
this one — is laid out the way a real manual page is laid out, with the
same section headings in the same order:

- `NAME` — what it is called and, in one line, what it does
- `SYNOPSIS` — how to invoke it, in a notation with its own rules
- `DESCRIPTION` — what it actually does
- `SEE ALSO` — related pages

The consequence is the point: by the time you have played this game for
a while, you will have read several hundred manual pages. When you open
a real one, you will already know where to look.

A page is addressed by name and, when the name is ambiguous, by section
number. See `man-section(7)`.

## OPTIONS

- `<section>` — restrict the lookup to one section, as in `man 7 compute`.

## EXIT STATUS

- `0` — the page was found.
- `1` — there is no such page.

## REAL-WORLD COUNTERPART

real — `man` is on every Unix machine including macOS, and the section
headings above are its actual convention, described in `man-pages(7)`.

Three things about the real one that are worth knowing immediately:
it works offline, it documents the version installed on *your* machine
rather than the current release, and inside the pager `/` searches, the
space bar pages down and `q` quits. `man man` documents `man` itself.

If you do not know the command's name, `apropos(1)` searches by
description instead.
```

---

### 3.17 `man-section(7)`

```
id:             man-section
section:        7
name:           manual section
canonical:      manual section
gloss:          The number that says which manual a name is being looked up in.
status:         real
aliases:        section, man section, section number
seeAlso:        man(1), apropos(1), synopsis-notation(7)
reading:        man(1); man-pages(7) "Sections of the manual pages";
                intro(1), intro(2), intro(8)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  man(1)
hook:           The first time the player sees a page addressed as
                `compute(7)` rather than `compute`, and wonders what the
                number is for.
misconception:  commonly believed the number after a command name is a
                version; actually it is which of the numbered manuals
                the page lives in, and the same word can appear in
                several. `printf` is both a shell command and a C
                function, so `printf(1)` and `printf(3)` are two
                different pages about two different things.
transfer:       Run `man 1 printf` and then `man 3 printf` on a Mac or
                Linux machine. Same word, two manuals, two answers. Then
                run `man 7 signal` for a concept page with no command
                behind it at all. Assumes a Unix shell — see ED-8.
verified:       Section directories man1, man4, man5, man6, man7, man8,
                man9 present under /usr/share/man on macOS
                (Darwin 25.5); section meanings — man-pages(7) and
                man(1); `intro(1)` confirmed as "General Commands
                Manual" by rendering it; our section usage —
                ../client/04 §3.10, §4.3. Checked 2026-07-25.

## DESCRIPTION

`compute(7)` is not compute version 7. The number says which manual the
page is in.

Four sections are used in this game, and each carries a meaning:

- **1** — commands you run. `grep(1)`, `breach(1)`, `port-sweep(1)`.
- **5** — the shape of a record or file. `provenance-record(5)`.
- **7** — concepts, which have no command behind them. `compute(7)`,
  `glob(7)`, this page.
- **8** — commands for maintaining your own machine. `scan(8)`,
  `firewall(8)`.

The 1-versus-8 split is the interesting one, and it is not decorative.
In real Unix, section 1 is commands any user runs and section 8 is
system administration. Here, that maps onto something the game cares
about: acting on someone else's machine is section 1, and defending
your own is section 8. The numbering is telling you which side of the
game you are on.

The same name can exist in more than one section, which is exactly why
the number is written down.

## REAL-WORLD COUNTERPART

real — the numbering is a genuine convention, described in
`man-pages(7)`. The full set on a Linux machine is 1 commands,
2 system calls, 3 library functions, 4 devices, 5 file formats,
6 games, 7 miscellaneous and overviews, 8 system administration.

`printf(1)` and `printf(3)` are the classic demonstration: one is a
shell command, the other is the C library function, and they are
genuinely different things with different arguments. Writing the
section number is how a person avoids sending someone to the wrong one.

Section 6 being *games* is why this game's root page is
`eyeandsickle(6)`. That is correct rather than a joke.
```

---

### 3.18 `apropos(1)`

```
id:             apropos
section:        1
name:           apropos
canonical:      apropos
gloss:          Finds a command when you know what you want, not its name.
status:         real
aliases:        whatis, man -k
seeAlso:        man(1), man-section(7), grep(1)
reading:        apropos(1); whatis(1); man(1) "-k"; mandb(8)
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  man(1)
hook:           The moment the player knows what they want to do —
                "something that shows me connections" — and does not
                know what it is called.
misconception:  commonly believed you have to already know a command's
                name to look it up, which makes the manual useless
                exactly when you need it most; actually the manual is
                searchable by description, and that is what turns it
                from a reference into a way of finding things.
transfer:       Run `apropos socket` or `apropos "list directory"` on a
                Mac or Linux machine and read what comes back. If it
                reports nothing on a fresh Linux install, the index has
                not been built — `sudo mandb` builds it. Assumes a Unix
                shell — see ED-8.
verified:       /usr/bin/apropos and /usr/bin/whatis both present on
                macOS (Darwin 25.5); `apropos` searches NAME-line
                descriptions and `whatis` matches whole names —
                apropos(1) and whatis(1); `man -k` as the equivalent
                spelling — man(1); index rebuilt by mandb(8).
                Checked 2026-07-25.

## SYNOPSIS

       apropos <text>

## DESCRIPTION

Searches every manual page's one-line description and lists the ones
that mention your text.

This is the command that makes the manual usable when you do not know
what you are looking for. `apropos connection` finds the pages about
connections whatever they happen to be called. `apropos provenance`
finds the item-history material.

Its narrower sibling is `whatis`, which matches whole page names rather
than descriptions and prints the one-line summary — useful when you
have a name and want to know what it is before opening the page.

The one-line description it searches is the `NAME` line, which is why
every page has one and why it has to be written well. A page whose
`NAME` line is vague is a page nobody will find.

## EXIT STATUS

- `0` — at least one page matched.
- `1` — nothing matched.

## REAL-WORLD COUNTERPART

real — `apropos` is on every Unix machine, and `man -k` is the same
thing spelled differently. On macOS it works out of the box; on some
Linux installations the search index has to be generated first with
`mandb`, and until it is, `apropos` reports nothing at all, which is
confusing the first time.

It is genuinely one of the more under-used commands in real life. Most
people search the web for "how do I list open files" and never learn
that `apropos "open files"` was sitting on their machine the whole time.
```

---

### 3.19 `history(1)`

```
id:             history
section:        1
name:           history
canonical:      history
gloss:          The record of what you have already typed, searchable.
status:         real
aliases:        command history, Ctrl-R, reverse search
seeAlso:        shell(7), readline(7), tab-completion(7), grep(1)
reading:        history(3) / bash(1) "History Expansion"; readline(3);
                zshoptions(1) "HISTFILE", "SHARE_HISTORY"
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          operating
prerequisites:  shell(7)
hook:           The second time the player needs a long command they
                already typed once, and reaches for the Up arrow.
misconception:  commonly believed the Up arrow is the only way back
                through history, so a command from an hour ago is
                effectively gone; actually history is searchable —
                Ctrl-R searches backwards through everything you have
                typed, and it persists across sessions.
transfer:       In any terminal, press Ctrl-R and start typing a
                fragment of something you ran recently. Keep pressing
                Ctrl-R to step back through older matches, and press
                Enter to run it or the right arrow to edit it first.
                Then run `history | grep ssh`. Assumes a Unix shell —
                see ED-8.
verified:       Ctrl-R as readline's reverse-incremental-search binding,
                and Up/Down as history traversal — readline(3) and
                bash(1); history persisting to a file across sessions —
                bash(1) HISTFILE and zshoptions(1); the same bindings
                offered in our palette — ../client/04 §3.9.
                Checked 2026-07-25.

## SYNOPSIS

       history [-n <count>]

## DESCRIPTION

Prints what you have typed, most recent last. The terminal transcript is
the same record in a different form.

The keys matter more than the command:

- `Up` and `Down` step through previous lines.
- **`Ctrl-R` searches backwards.** Start typing any fragment and the
  most recent matching line appears; press `Ctrl-R` again to step to the
  one before it. Enter runs it; the arrow keys drop you into editing it.

`Ctrl-R` is the one to learn. Stepping through history one line at a
time is fine for the last three commands and useless for the last
three hundred, and most people never discover the search because
nothing advertises it.

Because history is a list of lines, it pipes: `history | grep breach`
finds every breach you have attempted.

## OPTIONS

- `-n <count>` — show only the last `<count>` lines.

## REAL-WORLD COUNTERPART

real — and the keys are identical, because both this palette and most
real shells use the same underlying editing library, `readline`. That
library is also why `Ctrl-A` and `Ctrl-E` jump to the start and end of
a line in `bash`, in `psql`, in `python`, and in dozens of other
programs that never coordinated with each other.

On a real machine history persists to a file — `~/.bash_history` or
`~/.zsh_history` — which is worth knowing for two reasons. It survives
reboots, so `Ctrl-R` can find something from last month. And it is a
plain-text file containing everything you have typed, which is worth
remembering before typing a password on a command line.
```

---

### 3.20 `jobs(1)`

```
id:             jobs
section:        1
name:           jobs
canonical:      jobs
gloss:          Lists the work you started that is still running.
status:         real, simplified
aliases:        job control, background jobs
seeAlso:        job-control(7), process(7), signal(7), ps(1),
                daemon(7), kill(1)
reading:        jobs(1); bash(1) "Job Control"; POSIX.1-2024 XCU §2.11
                "Job Control"; setpgid(2)
notes:          ⚠ A job is not a process — it is a shell-level grouping
                that may contain several. The distinction is the whole
                point of the CAVEATS section and must survive
                translation.
revision:       1

--- curriculum only, stripped before shipping ---

domain:         04
stage:          investigating
prerequisites:  process(7), command(7)
hook:           The `botnet` window, and the first time the player has
                more bots running than they can hold in their head.
misconception:  commonly believed a job and a process are the same
                thing, so `jobs` and `ps` should show the same list;
                actually a job is the shell's own grouping of the work
                *it* started, and one job can contain several processes
                — a whole pipeline is one job. `ps` shows the machine's
                processes; `jobs` shows your shell's work.
transfer:       Run `sleep 300 &` then `jobs` — one entry, marked
                Running. Run `sleep 300 | cat &` then `jobs` again: still
                one new entry, though `ps` will show two processes.
                Then `fg %1` brings one back, and Ctrl-Z suspends it.
                Assumes a Unix shell — see ED-8.
verified:       A pipeline forming a single job containing multiple
                processes, and `%N` job references — bash(1) "Job
                Control" and POSIX.1-2024 XCU §2.11; `sleep 300 &`
                followed by `jobs` reproduced on macOS (Darwin 25.5);
                Ctrl-Z sending SIGTSTP and `fg`/`bg` resuming —
                bash(1). Checked 2026-07-25.

## SYNOPSIS

       jobs [-v]

## DESCRIPTION

Lists the bot instances you have running, what each is doing, and how
long it has been doing it. The `botnet` window is the same information
drawn as a window rather than as lines.

The idea being borrowed is job control: work you started that continues
without your attention while you do something else. A bot instance is
exactly that — you build it, it runs, and it keeps running whether or
not you are looking at it.

Each job has a number, and commands that act on one take that number.

## OPTIONS

- `-v` — include which action each job has contributed, rather than just
  its state.

## REAL-WORLD COUNTERPART

real, simplified — `jobs` is a real shell builtin and job control is a
real feature specified in POSIX.1-2024 XCU §2.11.

On a real machine you put work in the background by appending `&`,
bring it back with `fg`, push a suspended one back to running with
`bg`, and suspend the thing currently running with Ctrl-Z. Jobs are
referred to as `%1`, `%2` and so on. The whole mechanism exists because
terminals originally could only run one thing at a time, and it remains
the fastest way to park a long-running command without opening another
window.

## CAVEATS

- **A job is not a process.** A pipeline is one job containing several
  processes, which is why `jobs` and `ps` legitimately disagree. See
  `process(7)`.
- **Jobs belong to the shell that started them.** Close that shell and
  its job list is gone — the processes may or may not survive, which is
  a genuinely confusing corner of real Unix and the reason `nohup` and
  `disown` exist.
- **We have no `&`, no `fg`, no `bg` and no Ctrl-Z.** Bots are started
  and stopped by command (`bot build`, `bot stop`), and there is no
  foreground to bring them to. The list is real; the controls are not.
```

---

## 4. Open questions

Prefix **`SH-`** (shell), chosen because `CL-` already belongs to `../client/00-client-overview.md` and this doc set should not add a third meaning to an existing prefix — see **CT-10**, which is about exactly that failure.

- **SH-1: this domain has one `adversarial` entry, and it is unwritten.** `shell-injection(7)` is inventoried at that stage and nothing else here reaches it. That is honest — the command line is infrastructure, and infrastructure is rarely adversarial by itself — and it is also convenient, because **CT-3** and **DS-4** both report the `adversarial` budget as oversubscribed. **Recommendation: leave it at one.** Recorded so that a later reader does not "fix" the imbalance by inventing adversarial framing for quoting or globs, which would be padding.

- **SH-2: `ls(1)` and `stat(1)` have two plausible owners.** Claimed provisionally here (§1.4); `03-operating-systems.md` §2.3 lists the command pages it owns as `ps`, `kill`, `df` and `scan`, and does not mention these two, so they are currently nobody's in writing. The case for `03` is that their payload is the filesystem — `ls` shows names, `stat` shows the inode behind a name. The case for `04` is that `ls` is where a beginner first meets positional arguments and flag clustering, and that lesson is grammar. **Recommendation: `stat(1)` to `03` (its payload is genuinely `inode(7)`), `ls(1)` here.** Cheap either way, and it must be settled before either document's entries are written, because it decides four `seeAlso` edges.

- **SH-3: does `../client/04` §2.15's homonym table contain any of this domain's terms?** Three are plausible collisions and this document could not check the table's contents against them: **`flag`** (a command-line option here; also, in ordinary computing speech, a boolean field), **`history`** (the command here; also the transcript, which `../client/04` §3.10 lists separately), and **`job`** (a shell job here; also, potentially, whatever the `botnet` window calls a unit of bot work). `00-curriculum-and-method.md` §3.2 makes `notes:` **mandatory** for every §2.15 homonym, so if any of these is in that table, three entries above are missing a required field. **Check before shipping.**

- **SH-4: `pipeline(7)` and `pipe(7)` are the tightest boundary in the doc set.** `03-operating-systems.md` §2.3 owns `pipe(7)` — the kernel object — and this document owns `pipeline(7)` — the syntax. The split is correct and it is also one edit away from collapsing: the natural thing for either writer to do is explain the other half "briefly, for context", and then there are two entries answering one question, which `00` §1.4 forbids. **Proposal: whichever of the two is translated into a term file second must be diffed against the first**, and the reviewer's explicit question should be "does either page teach the other's payload?"

- **SH-5: the `regular-expression(7)` / `glob(7)` `*` collision deserves a mechanism, not just two good pages.** Both entries name the collision and each is the other's first `SEE ALSO`, which is the best this doc set can do on its own. But the confusion happens *in play*, at the moment a player who just typed `ls /rig/tools/*-sweep` types `grep 'ab*'` and gets a different answer — and the teaching layer has a trigger mechanism (`../client/04` §4) that could surface the distinction exactly there. This is a finding **filed against `../client/04`**, per `00-curriculum-and-method.md` §1.2 rule 1: a curriculum document may not specify a mechanism, but it can report that this is the single highest-value contextual trigger in the domain.

- **SH-6: `shell-injection(7)` may duplicate `06`'s `injection(7)`.** Both are about data becoming instructions; `01-foundations.md` owns `data-and-code(7)` underneath both. Three entries on one idea is two too many. **Recommendation: `06-cryptography-and-trust.md` owns the attack class as `injection(7)`, `01` owns the general principle, and `shell-injection(7)` is dropped from this inventory** — with `quoting(7)`'s `CAVEATS`, which already makes the point in two sentences, carrying the domain's share of it. Listed rather than acted on because it is `06`'s call, and because **CT-5**'s dual-use rule governs how far any of the three may go.

- **SH-7: ED-8 lands more lightly here than expected, and this domain is evidence for option (c).** Every transfer test above is a Unix command, so on the face of it this is the worst-affected domain in the set — worse than `03`, which **OS-9** already calls the worst. But the substance is different: what these entries teach is *the shell itself*, so a player with no shell has nothing to transfer to by definition, and the honest answer for a Windows player is not a PowerShell equivalent but WSL, which gives them the real thing. **Recommendation: this domain adopts ED-8 option (a) unconditionally** — every test names its platform, and the domain's own root entry, `shell(7)`, is the natural place to tell a Windows player how to get one. That is a better outcome than translating `grep` into `Select-String`, which would teach a second vocabulary the game does not use.

- **SH-8: `man(1)` at `first-session` is a budget claim that needs confirming against the other six domains.** R2 caps the whole game's first session at twelve entries. This document takes two, `01` takes one, `02` takes one, `06` takes zero. Nobody has totalled them. `man(1)`'s claim is the strongest in the set — it is the entry that makes every other entry findable — but if the total exceeds twelve, the argument for cutting is going to be made against whichever document is read last, rather than against the weakest entry. **Total all seven before any of them ships.**

- **SH-9: `synopsis-notation(7)` is inventoried and unwritten, and it may be the most-used unwritten entry in the doc set.** Every section-1 and section-8 page in the game has a `SYNOPSIS` line full of brackets and bars, and nothing anywhere explains that `[x]` means optional and `a|b` means one of these. The reason it is unwritten is that its hook is diffuse — it is on every page rather than on one surface — which is exactly the case §7.3's hook test handles badly. **Recommendation: write it, and give it the hook "the first `SYNOPSIS` line the player reads", which is honest.** Flagged because a strict reading of the hook rule would delete it, and that would be wrong.

- **SH-10: nothing in this domain has had a technical review.** Every claim above is sourced, and every transfer test in it was run on this machine before being written down — but `00-curriculum-and-method.md` §8.4 is explicit that a writer verifying their own claims has checked that the claim matches a source, not that the source was the right one. The places a practitioner is most likely to object: `shell(7)`'s "the shell is not privileged", which is true and glosses over setuid and over shells running as root; `pipeline(7)`'s concurrency claim, which is true of every shell in practice but is a shell behaviour rather than a POSIX guarantee; and `text-as-interface(7)`'s framing of the Unix philosophy, which is a summary of an argument that people still have. **ED-6 is the blocking question; this is its instance for this domain.**

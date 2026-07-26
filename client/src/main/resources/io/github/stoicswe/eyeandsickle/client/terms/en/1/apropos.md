---
id: apropos
section: 1
name: apropos
canonical: apropos
gloss: Finds a page when you know what you want, not what it is called.
status: real
aliases: whatis, search
seeAlso: man(1), grep(1)
reading: apropos(1) | whatis(1) | man(1) "-k" | mandb(8)
revision: 1
---

## SYNOPSIS

       apropos [--all] <text>

## DESCRIPTION

Searches the one-line description of every manual page and lists the ones
that mention your text.

This is the command that makes a manual usable when you do not know what you
are looking for. `apropos compute` finds the pages about capacity whatever
they happen to be called.

The line it searches is the NAME line, which is why every page has one and
why it has to be written well. A page whose NAME line is vague is a page
nobody will find.

## OPTIONS

       --all   search the whole page, not only the NAME line

## EXIT STATUS

       0    at least one page matched
       1    nothing matched

## REAL-WORLD COUNTERPART

real — `apropos` is on every Unix machine, and `man -k` is the same thing
spelled differently. `whatis` is its narrower sibling: it matches whole names
and prints the one-line summary.

On macOS it works out of the box. On some Linux installations the search
index has to be built first with `sudo mandb`, and until it is, `apropos`
reports nothing at all — which is confusing the first time.

It is genuinely one of the more under-used commands in real life. Most people
search the web for "how do I list open files" and never learn that
`apropos "open files"` was on their machine the whole time.

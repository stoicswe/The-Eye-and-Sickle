---
id: man
section: 1
name: man
canonical: man
gloss: Opens the manual for a thing, on this machine, offline.
status: real
aliases: manual, help
seeAlso: apropos(1), shell(7), ps(1), compute(7)
reading: man(1) | man-pages(7) | intro(1)
notes: The teaching layer itself is rendered in this form. Do not translate the section headings.
revision: 1
---

## SYNOPSIS

       man [<section>] <name>

## DESCRIPTION

Opens the manual page for something. `man compute` explains compute;
`man 7 glob` explains globs; `man ps` explains `ps`.

The shape of what you are reading is not decoration. Every page in this game
is laid out the way a real manual page is laid out, with the same headings in
the same order: NAME, SYNOPSIS, DESCRIPTION, SEE ALSO. By the time you have
played for a while you will have read several hundred of these, and when you
open a real one you will already know where to look.

A page is addressed by name and, when the name is ambiguous, by section
number. The number is not a version — it says which of the numbered manuals
the page is in. Section 1 is commands you run, 5 is record formats, 7 is
concepts, 8 is maintaining your own machine.

## OPTIONS

       <section>   restrict the lookup to one section, as in `man 7 compute`

## EXIT STATUS

       0    the page was found
       1    there is no such page

## REAL-WORLD COUNTERPART

real — `man` is on every Unix machine including macOS, and these headings are
its actual convention, described in `man-pages(7)`.

Three things worth knowing immediately: it works offline, it documents the
version installed on *your* machine rather than the newest release, and
inside the pager `/` searches, space pages down and `q` quits. `man man`
documents `man` itself.

The classic demonstration of why sections exist: `man 1 printf` and
`man 3 printf` are two different pages about two different things — a shell
command and a C library function.

If you do not know the command's name, see apropos(1).

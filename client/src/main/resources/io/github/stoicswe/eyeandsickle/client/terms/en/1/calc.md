---
id: calc
section: 1
name: calc
canonical: calc
gloss: One value in hex, decimal, octal and binary at once, with its bits.
status: real, simplified
aliases: bc
seeAlso: man(1), apropos(1), shell(7), compute(7)
reading: bc(1) | printf(1) | xxd(1) | dc(1)
revision: 1
---

## SYNOPSIS

       calc [--bits=8|16|32|64] [--signed] <expression>

## DESCRIPTION

Evaluates an expression and prints the answer in **all four bases at once**,
with the register's width and byte order beside it.

    $ calc 0xff xor 0b1010
    hex   0000 00F5
    dec   245
    oct   00000365
    bin   0000 0000 0000 0000 0000 0000 1111 0101

    32 bits, unsigned   set bits 6   bytes BE 00 00 00 F5   LE F5 00 00 00

All four, rather than the one you asked in, on purpose: hex is not a different
number, it is a different way of writing the same bits. One hex digit is
exactly four bits and one octal digit is exactly three, which is why an address
or a colour is written in hex and a file mode is written in octal.

Literals carry their base — `0x` for hex, `0b` for binary, `0o` for octal, and
plain digits for decimal. Underscores inside a number are ignored, so
`0b1010_0110` is legal. Operators are the symbols or the words:

       + - * / %          arithmetic
       & | ^  ~           and, or, xor, not
       and or xor         the same three, spelled out
       << >>> >>          shift left, shift right, shift right keeping the sign
       lsh rsh asr        the same three, spelled out
       rol ror            rotate — bits leaving one end return at the other
       mod                remainder

`calc` with no arguments is a usage line. The **calc** tool window is the same
engine with keys on it, a grid of bits you can click, and every readout live —
`Shortcut+Shift+C`.

## OPTIONS

       --bits=N   register width: 8, 16, 32 or 64. Default 32
       --signed   read the decimal row as two's complement

## EXIT STATUS

       0    the expression was evaluated
       64   the expression could not be read, and the reason is printed

## REAL-WORLD COUNTERPART

real, simplified — every desktop operating system ships a calculator with a
programmer mode that does this, and every Unix ships `bc`. The behaviour worth
carrying away is not the tool, it is the model: **a value has a width**, and
every operation on it happens modulo that width. `calc 0xff + 1 --bits=8`
answers `0`, which is the same answer C, Java, Rust and the processor itself
would give you for a byte.

Try `printf '%x\n' 255` and `printf '%o\n' 493` on any Mac or Linux machine;
`echo 'obase=2; 245' | bc` for the binary. `xxd` on any file shows the same
bytes-and-characters pairing the tool window's bottom row does.

## CAVEATS

Three deliberate simplifications, each of which the real thing does differently.

**There is no operator precedence.** `2 + 3 * 4` is `20` here, not `14`. Every
desk calculator works this way and `bc` does not — `bc` is a language and
follows the usual rules. Anything you type into a program will use precedence;
this does not.

**A shift by the register width or more gives zero.** Real hardware disagrees:
x86 masks the shift count to the low 5 or 6 bits, and Java and C do the same,
so `1 << 64` on a 64-bit value is `1` and not `0`. That is genuinely surprising
and genuinely real. This tool answers the arithmetic instead, because a
calculator that reproduced the wrap would teach the wrap without ever saying
so.

**`>>` keeps the sign and `>>>` does not**, following Java. C decides the same
question by whether the type is signed, and assembly has two separate
instructions. There is no notation everybody agrees on; the two operations are
both here under names that do say which is which — `asr` and `rsh`.

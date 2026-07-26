---
id: storage-tiers
section: 7
name: storage tiers
canonical: Storage tiers
gloss: Three places to keep things, trading reachability against safety.
status: game
aliases: vault, storage, tiers
glossary: ../design/glossary.md
seeAlso: df(1), ls(1), ethecoin(7), heat(7)
revision: 1
---

## DESCRIPTION

Everything you own sits in one of three places, and the choice is a real one.

The **Encrypted Vault** is safe. Nothing reaches it, online or off. Its
capacity is limited and cannot be bought.

**Standard Storage** is exposed while you are online. This is where things
you are actually using live, and the exposure is the price of using them.

The **High-Hackable Zone** is always exposed, whether you are there or not.
Nothing should be here that you would mind losing — which makes it the right
place for a decoy, and the wrong place for anything else.

`mv <item> <tier>` moves things. The risk change is the point of the command.

## REAL-WORLD COUNTERPART

game — the three-tier split with these exact properties is this game's.

The underlying trade is entirely real and you make it constantly: data at
rest can be encrypted, and encrypted data is unreadable to whoever takes it
— but it is also unreadable to the machine using it, so anything in active
use is decrypted somewhere. A hardware security module, a locked password
vault, and a cold wallet are all the same bargain: the safest place for
something is the place it is hardest to use.

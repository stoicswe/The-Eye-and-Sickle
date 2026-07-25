# 03 — Servers & Federation

**Status:** Established (Tech Chat 1)
**Depends on:** `00-overview.md`, `02-identity-and-auth.md`
**Depended on by:** `04-item-provenance.md`, `05-validator-quorum.md`, `../design/13`

---

## 1. The self-host model

Home servers are **self-hostable, Minecraft-style, with allowlists** (`01` §2). Anyone can run one via Docker Compose; the operator controls who joins. This is a core requirement, not a nicety — it shapes everything about the trust model.

Consequence: **adversarial, untrusted servers exist by design.** A self-hosted server could try to mint items out of nothing, declare its player won every duel, or run dishonest validators. The federation layer's entire job is to make those attacks *not work* without requiring a central authority (Invariant I15).

## 2. The federation directory

**Opt-in, non-adversarial-data-only.** Servers that want cross-server play opt into a federation directory — a list of public servers. Key properties:

- **Opt-in:** a private/allowlisted server can ignore federation entirely and just be a single-player or friends game (`../design/13` §5).
- **Non-adversarial data only:** the directory and inter-server sync share the *minimum* needed to recognize identities (DIDs) and validate item provenance. Servers never share enough for one to grief another's internal state. A server's private game state stays private.

The directory is a **low-trust index, not an authority.** It says "these servers exist and claim to federate," nothing more. Trust in any specific outcome comes from the quorum (`05`) and provenance (`04`), never from the directory.

## 3. What crosses server boundaries — and what doesn't

| Interaction | Where resolved | Trust mechanism |
|---|---|---|
| Home-server-local PvP (raids, miner cracks) | The home server's Postgres | Server is authoritative for its own players |
| Player identity across servers | AT Proto DID (`02`) | Cryptographic identity |
| Cross-server item transfer | Provenance chain (`04`) | Signed per-item history |
| Cross-server duel outcome | Validator quorum (`05`) | Reputation-weighted majority signatures |
| "Is this item real?" | Provenance verification + non-recognition (§4) | Federation-wide consensus on legitimacy |

The design keeps the expensive trustless machinery (quorum) on the **rare, high-value, cross-server** path. Most play is local and cheap. (`../design/13` §3, open question D-1 defines the exact boundary.)

## 4. Anti-cheat: reputation registry + non-recognition

The enforcement model has two prongs:

1. **Reputation registry + item provenance signatures** (`04`, `05`) — the positive mechanism: legitimate items carry verifiable signed histories; legitimate outcomes carry quorum signatures.
2. **Federation-wide non-recognition for flagged servers** — the negative mechanism: a server caught minting fraudulent items or running dishonest validators (e.g. equivocating, `05`) has **its items refused by honest servers across the federation.**

Non-recognition is the endgame answer to "you can run any server you like": **you can, but the federation collectively decides whose items and outcomes are real.** A cheating server doesn't get banned by an authority (there isn't one) — it gets *ignored* by everyone honest, which makes its fraudulent items worthless outside its own walls.

> **[PROPOSAL]** The flagging mechanism itself needs specification: what evidence flags a server (equivocation proofs are cryptographic and automatic, `05`; other fraud may need detection), who propagates the flag (gossip across the directory?), and whether flags are reversible. Tech Chat 2 gives the cryptographic basis for the *provable* cases (equivocation → automatic); the softer cases are unspecified. Flag for the federation designer.

## 5. Why federation instead of a central server

Stated plainly (from Tech Chat 1's constraint): the game must have **no single point of trust for adversarial state.** A central server would be that single point — and would also undercut the self-host requirement. Federation with quorum consensus + provenance gets trustless cross-server play while preserving "anyone can run a server." The cost (latency, complexity) is accepted and confined to the cross-server path.

## 6. Fiction alignment

This isn't just plumbing — it *is* the setting (`../design/14` §2). The Sickle is a decentralized resistance with no headquarters; the federation of independent home servers is that decentralization made technical. "Your home server = your cell" falls out of the architecture for free.

## 7. Cross-references

- Item legitimacy proofs: `04-item-provenance.md`
- Duel adjudication + validator trust/slashing: `05-validator-quorum.md`
- Player-facing multiplayer built on this: `../design/13`
- The fiction this mirrors: `../design/14` §2

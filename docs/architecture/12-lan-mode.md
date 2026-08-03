# 12. LAN mode — the third way to play

> **Status: ⚠️ [PROPOSAL] — written 2026-08-02, partially built.**
>
> Not a new idea: `03-server-and-federation.md` §2 already states that *"a private/allowlisted server
> can ignore federation entirely and just be a single-player or friends game"*, and
> `../design/13-multiplayer-and-federation-play.md` §5 makes real-loss play opt-in. This document is
> the unbuilt half of that sentence, made specific.

---

## 0. The three modes, and the one sentence that separates them

| | **Solo** | **LAN** | **Federated** |
|---|---|---|---|
| Where state lives | a JSON file on the player's disk | the LAN server's Postgres | the home server's Postgres |
| Who else is in it | nobody | whoever is on the network | anyone the operator allows |
| Identity | a local handle | **a server-assigned UUID** | an AT Protocol DID |
| Identity is proven by | nothing | **nothing — it is a bearer token** | a signature over a resolved DID document |
| Losses are real | no | **yes, within the LAN** | yes |
| Can federate | **never** | **never** | yes |

**The sentence:** *state is quarantined by the strength of the identity that produced it.* Solo state
comes from an editable file and never leaves the machine. LAN state comes from an unproven UUID and
never leaves the LAN. Federated state comes from a cryptographically proven DID and may cross servers.

⚠ This is the same rule `CLAUDE.md` already states for solo — *"a solo character is local-only and can
never federate, which is how I14 survives a save file the player can edit"* — applied one rung up.

---

## 1. ⚠ THE QUARANTINE RULE — the load-bearing part of this document

**Nothing that originates on a LAN server may ever enter federated state, in either direction.**

Concretely, a LAN server must refuse to:

1. **Migrate a character out** (`09-player-state-portability.md`) — to a federated server or to any
   other server at all.
2. **Export item provenance** that a federated server would accept.
3. **Accept provenance** from a federated server, which would let LAN play consume real items.
4. **Advertise itself** in the federation directory, or answer peer exchange (`08`).
5. **Participate in, or request, a validator quorum** (`05`).

### Why each of those, specifically

A LAN identity is **a UUID with no proof behind it**. Anyone who learns it *is* that player, and
anyone can generate one. So:

- **Items** minted there have provenance chains rooted in an identity nobody can verify. A federated
  server importing one cannot distinguish "earned over forty hours" from "inserted with a SQL client
  ninety seconds ago". That is precisely the attack `04-item-provenance.md` exists to defeat.
- **Duel outcomes** had no quorum. One machine decided them, which is exactly the "single arbiter"
  shape **I15** forbids for cross-server outcomes.
- **Balances** came from an economy no other server watched.

⚠ **The direction that is easy to forget is inbound.** Refusing to *export* is the obvious half.
Refusing to *import* matters just as much: a player who could carry a federated item onto a LAN server
and lose it there has suffered a real loss adjudicated by a machine with no accountability — and if
they could carry it *back*, a LAN server becomes an item duplicator.

### ⚠ Mode is a property of the DATABASE, not a switch

A server may not be flipped from LAN to federated with its characters intact — that is precisely
"import all quarantined state at once". Changing mode requires a fresh database, and the server must
refuse to start if the mode it is configured for disagrees with the mode its data was created under.
An operator who wants both runs two servers.

---

## 2. Identity: username in, UUID out

1. A player submits a **username** to the LAN server.
2. The server assigns a **random UUIDv4** and returns it.
3. The client stores it, keyed by that server, and presents it on every later connection.
4. The UUID is what the server keys the character on. The username is a **display name** and may
   change; nothing is keyed on it.

That mirrors the federated model exactly — DID is the key, handle is a mutable display name — which is
deliberate: the two modes should differ in *what proves the identity*, not in the shape of it.

### ⚠ What a UUID identity is, said plainly

**It is a bearer token.** Whoever holds it is that player, and there is no second factor, no
signature, and no way for the server to tell a copy from the original. The threat model that makes
this acceptable is *the network is the trust boundary* — the people who can reach the server are
people you invited into your home or your LAN party.

Consequences that follow, and must be built rather than assumed:

- ⚠ **UUIDv4, from a CSPRNG, never sequential and never derived from the username.** A guessable
  identity is one anyone on the network can wear. `UUID.randomUUID()` is the right call; a counter is
  not.
- ⚠ **It is a credential and is stored like one** — the same store the AT Protocol refresh token uses,
  never `ClientProfile`. The stakes are lower than a Bluesky session; the handling is the same because
  the failure is the same shape.
- **The server shows the username, never the UUID**, except where an operator needs to allowlist or
  ban one.
- ⚠ **Usernames are not unique and must not be made unique.** Two players calling themselves `ghost`
  is a social problem with a social fix; making the username a key would quietly recreate the thing
  the UUID exists to be.

### ⚠ The interlock: LAN mode must refuse a public address

"The network is the trust boundary" is only true if the server is actually on a private network. A
LAN-mode server that binds a public interface is **an open server with no authentication at all**.

So: in LAN mode the server **refuses to start** unless every address it binds is loopback, link-local,
or RFC 1918 / unique-local. This is the single most important safety check in the mode, because every
other decision here rests on it, and because the failure is silent — a misconfigured server works
perfectly and is simply open to the internet.

---

## 3. What is switched off, and what stays on

### Off in LAN mode

| Subsystem | Why |
|---|---|
| AT Protocol sign-in (`10`) | there is no DID; the identity provider is swapped, not bypassed |
| DID / handle resolution | nothing to resolve, and no outbound HTTP is wanted on a LAN |
| Peer discovery, descriptor advertisement (`08`) | quarantine rule 4 |
| Validator quorum (`05`) | quarantine rule 5 |
| Provenance ingress **and** egress (`04`) | quarantine rules 2 and 3 |
| Character migration (`09`) | quarantine rule 1 |

⚠ **Switched off means the bean is absent, not that a flag is checked at each call site.** A flag
consulted in fifteen places is a flag somebody forgets in the sixteenth. The federation components
should not exist in a LAN-mode context at all, so a call that should not happen fails to wire rather
than failing to check.

### On in LAN mode — which is most of the game

Everything single-server: the economy, mining and the chain, breach and the puzzle, heat and
detection, items and gates, the vault and its tiers, the ledger, bots, and **PvP between players on
that LAN**. Losses there are real *within the LAN*.

⚠ **I14 is untouched.** It says game state lives in the server's Postgres and never in
player-controlled infrastructure — and it still does. I14 names AT Proto as identity-only; it does not
require that identity *be* AT Proto. A UUID assigned and held by the server is, if anything, less
player-controlled than a DID.

---

## 4. The invariants, one by one

| | Effect of LAN mode |
|---|---|
| **I1** compute never purchasable with ethecoin | none — server-side rule, unchanged |
| **I2** ethecoin never buys a ceiling | none |
| **I3** one unlock gate per item | none |
| **I4** self-mining immune, zero heat | none |
| **I5** offline income bounded | none |
| **I6** deployed miner spends the host's compute | none |
| **I7** proof-of-skill gates tier-gated | none |
| **I8** zero-days never reliably purchasable | none |
| **I9** defending your own rig is heat-free | none |
| **I10** bots assist, never substitute | none |
| **I11** bot loss destroys instances, not blueprints | none |
| **I12** vault capacity sub-linear, unpurchasable | none |
| **I13** salvage gated on engagement tier | none |
| **I14** game state never in player-controlled infrastructure | ✅ **holds** — state is still the server's Postgres. AT Proto is identity-only, not the only identity |
| **I15** no single arbiter for cross-server adversarial outcomes | ⚠ **vacuous, not violated** — see below |

### ⚠ I15 in LAN mode

A LAN server *is* a single arbiter. That does not violate I15, because I15 governs **cross-server**
adversarial outcomes and a LAN server has no cross-server outcomes to decide.

**The quarantine rule is what keeps that true.** The moment LAN state could cross a server boundary,
I15 would apply — and would be violated, because one machine decided everything. So §1 is not
belt-and-braces around a lesser mode; it is the entire reason LAN mode is compatible with the
architecture at all.

⚠ **Corollary worth stating: "just let LAN characters migrate if the operator allows it" is not a
setting.** It is I15 with an off switch.

---

## 5. What the player must be told, and where

Real-loss play is opt-in (`../design/13` §5), which means the mode has to be legible at a glance —
`SessionMode` already exists for exactly this reason and already carries an explanation string.

- **On the mode indicator:** that losses are real *on this network*, and that nothing here can be
  carried to a federated server.
- ⚠ **Before the first character is made**, not after. A player who spends forty hours and then learns
  their character cannot leave the LAN has been misled by omission — and this is the one fact about
  the mode with a consequence they cannot reverse.
- **On the join screen:** that identity here is a name and a code held by this server, with no
  password and no account, and that anyone who obtains the code can play as them.

---

## 6. Open questions

- **LAN-1: the allowlist default.** `03` §1 makes servers closed by default. On a LAN the network is
  already the boundary, so requiring an operator to paste UUIDs before anyone can join defeats the
  "friends game" case. Proposed: **open by default in LAN mode, gated on the §2 address interlock**,
  with the allowlist still available. Not yet decided.
- **LAN-2: what happens when the LAN server's address changes.** Identity is keyed on the server; a
  router handing out a new DHCP lease should not orphan characters. Probably wants the server's own
  identity to be a stable id it generates once, not its URL.
- **LAN-3: can a player with an AT Protocol account use it on a LAN server?** Currently no — LAN is
  UUID-only. Allowing it would mean one DID accumulating both federatable and quarantined state, which
  `09`'s portability model would then have to explain. Simpler to refuse; worth confirming.
- **LAN-4: discovery.** `11` §D proposes mDNS, which is far more valuable here than for federated play
  — a LAN server that has to be found by typing an IP is one most households will not find.
- ⚠ **LAN-6: how the client↔server link is secured on a LAN — the blocker for playing.**
  `07-transport-security.md` §3 makes TLS 1.3 **mandatory** and puts the DID channel *inside* it. On a
  LAN neither half is free:
  - **TLS** needs a certificate, and a household has no CA. A self-signed certificate pinned on first
    use is the practical route, and trust-on-first-use on a private network is a defensible model —
    but it is a decision, not a default, and it must be stated to the player.
  - **The channel** authenticates DIDs rather than hostnames, which is exactly what a server whose
    address is a DHCP lease needs — and it is the part `CLAUDE.md`'s **T-1** says is hand-rolled and
    unreviewed. T-1's wording is "do not let it guard a live federation"; a LAN test is not a
    federation, which is an argument for using it here *first*, not for skipping it.
  - ⚠ **A LAN client has no key of its own.** The channel is mutually DID-authenticated; a LAN identity
    is a UUID with no keypair (§2). So either the client generates a transport keypair at join and the
    server records it — which would make the LAN identity meaningfully stronger than a bare bearer
    token — or the handshake runs server-authenticated only. The first is better and is probably worth
    the extra field.
  ⚠ **Nothing here justifies plaintext.** The absence of a certificate is an argument for the channel,
  not against encryption; that reasoning was written into the client once and removed.

- **LAN-5: is "real loss" the right default on a LAN?** Between friends, permanent loss of a
  forty-hour character may be a worse social outcome than a softer rule. `../design/13` §5's consent
  model may want a LAN-specific answer.

## 7. Cross-references

- The sentence this document implements: [`03-server-and-federation.md`](03-server-and-federation.md) §2
- Why real-loss play is opt-in: [`../design/13-multiplayer-and-federation-play.md`](../design/13-multiplayer-and-federation-play.md) §5
- What quarantine protects: [`04-item-provenance.md`](04-item-provenance.md), [`05-validator-quorum.md`](05-validator-quorum.md)
- Migration, which LAN characters are refused: [`09-player-state-portability.md`](09-player-state-portability.md)
- The identity this mode replaces: [`10-oauth-and-did-resolution.md`](10-oauth-and-did-resolution.md)

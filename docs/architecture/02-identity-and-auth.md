# 02 — Identity & Authentication

**Status:** Established (Tech Chat 1)
**Depends on:** `00-overview.md`
**Depended on by:** `04-item-provenance.md` (DIDs sign/hold items), `05-validator-quorum.md`, `../design/12`

---

## 1. Decision

**AT Protocol OAuth, authentication-only.** Players sign in with their existing **Bluesky / AT Protocol** handle; the game resolves their **DID** (Decentralized Identifier), and that DID becomes the **portable player ID** every cross-server system keys off. No game data is ever written to a player's PDS.

## 2. Why AT Proto identity fits

The design (from the earlier mechanics work) needed a **portable player ID, decoupled from any single server, that proves "this is the same person" across independently-hosted instances.** That is precisely what AT Protocol's identity layer already is:

- **DIDs are stable identifiers**, explicitly designed to survive handle changes and to work across independently-run infrastructure — the exact property required.
- **OAuth** is AT Proto's standard auth flow, usable in **authentication-only mode** (the AT Proto docs distinguish this from full read/write authorization). Players are **not** granting the game write access to their Bluesky account — just proving identity.
- **Federation is native** to the protocol, so identity portability isn't something bolted on.

Concretely: instead of building and operating a central identity service, the player signs in with their AT Proto handle, the game resolves their DID, and that DID is the player ID the quorum / reputation / provenance systems (`04`, `05`) use. Real infrastructure the team doesn't have to build.

## 3. The hard boundary — identity only, never game state

> **Do not extend AT Proto to game-data storage** (Invariant I14).

AT Proto's repository model *would* technically let the game write custom item records into a player's own PDS via Lexicons. **This is explicitly rejected.** Reason (Tech Chat 1): a player's vault contents living in infrastructure *they control* is the same self-hosted-cheating problem the whole quorum/provenance system solves, relocated one layer down to the player's own repo.

The rule, stated for implementers:

- ✅ Use AT Proto to **authenticate** and to obtain a **DID**.
- ✅ Store the DID in the home server's Postgres as the player's ID; hang all game state off it there.
- ❌ **Never** write items, balances, rig state, or any adversarial game data into a player's PDS.
- ❌ **Never** trust the client or the player's PDS as authoritative for anything a cheater would want to forge.

Identity is safe to decentralize to the player. Adversarial state is not. That asymmetry is the whole point.

## 4. Accepted trade-off, and the offline question

**Accepted (Tech Chat 1):** requiring an AT Proto identity is a **soft requirement to play online** — real onboarding friction for an audience that may not already be on Bluesky. The user explicitly accepted this: *"online play requiring a Bluesky account is fine for this project for authentication of the player. It won't be used for game data storage."*

> **[PROPOSAL] Offline / single-player identity (open — see `../design/15`, not resolved in source):** single-player against a private/allowlisted server (`03`) arguably shouldn't require an internet round-trip to AT Proto just to start. Options to weigh at implementation time:
> 1. **Local identity for solo play** — a server-local player ID for private servers; AT Proto DID required only to join a *federated* server. Cleanest UX; means a solo character can't later be carried into federation without a DID-binding step.
> 2. **DID required always** — simpler model, one identity everywhere, at the cost of an online dependency even for solo play.
> Recommend option 1 (matches the per-server multiplayer opt-in proposed in `../design/13` §5: no federation → no DID needed). Flagged for a decision.

## 5. Implementation notes

- **Key type:** ⚠ **Corrected 2026-08-02 — this bullet used to be wrong, and its error was load-bearing.** It read: *"AT Proto DIDs use Ed25519 keys — which is also what the provenance layer signs with (`04`). One crypto stack for both identity and provenance; no second key system. This was a stated reason to lean on AT Proto (Tech Chat 2)."* **Both halves are false.** The [atproto DID spec](https://atproto.com/specs/did) permits exactly two curves for `verificationMethod` — **P-256 (secp256r1)** and **secp256k1 (K-256)** — and **Ed25519 is not among them**. Provenance signs with Ed25519 (`crypto/Ed25519Signatures`), so identity and provenance share a curve *not at all*, and the "no second key system" argument that partly justified choosing AT Proto does not survive the spec. The real inventory is **three** curves — Ed25519 (provenance), ES256/P-256 (DPoP, mandatory), ES256K/secp256k1 (service-auth JWTs) — and ES256K is not in the default JCA providers. See [`10-oauth-and-did-resolution.md`](10-oauth-and-did-resolution.md) §5.1. The decision to use AT Proto is unaffected; only this justification for it was.
- **DID resolution:** the home server resolves the DID at sign-in and caches the identity → DID mapping in Postgres. Handle changes don't break the mapping because the DID is stable.
- **Session scope:** authn-only OAuth session; the game holds proof-of-identity, not account authorization. Nothing the game does touches the player's social account.

## 6. Cross-references

- How the DID is used to sign/own items: `04-item-provenance.md`
- How validator identities (also DIDs) sign duel outcomes: `05-validator-quorum.md`
- The player-facing identity/social systems (burner handles, informants): `../design/12`
- Offline-identity open question: `../design/15` A-/offline note

# 07 — Transport Security

**Status:** ⚠️ **[PROPOSAL]** — neither Tech Chat 1 nor 2 addressed transport security at all. This doc fills that gap. The *goals* (§1) should outlive any specific mechanism; the *construction* (§4) is a first pass and is the part most likely to change.
**Depends on:** `02-identity-and-auth.md` (DIDs), `03-server-and-federation.md`, `04-item-provenance.md` (shares the Ed25519 key stack)
**Depended on by:** implementation — `protocol/src/main/java/.../protocol/channel/`

---

## 1. What we are actually defending

The requirement as stated: *data must be safe in transit from tampering*, between clients and servers and between servers.

Four properties, in priority order:

1. **Integrity** — nothing in flight can be altered without detection. The headline requirement.
2. **Authenticity** — each end knows *which DID* is at the other end, and every message is bound to it.
3. **Replay resistance** — a captured-and-resent message is refused. A "collect yield" frame replayed a thousand times is a thousand collections, every byte of it perfectly authentic.
4. **Confidentiality** — nobody in the middle can read the traffic. Genuinely last: an attacker who can *alter* an item transfer is a far worse problem than one who can merely *watch* one.

## 2. The honest framing: what "end-to-end" can and cannot mean here

**Between two servers, end-to-end encryption is real and worth having.** Federation messages pass through infrastructure neither peer controls — the federation directory, reverse proxies, whatever a self-hoster has in front of their stack. The directory is explicitly a *low-trust index, not an authority* (`03` §2). Sealing server-to-server messages to the peer's key means none of that infrastructure can read or alter them.

**Between a client and its home server, strict end-to-end encryption is a category error, and pretending otherwise would be worse than useless.** The server *is* the other end. It must read game state to be authoritative — that is Invariant I14, and it is the entire reason the architecture refuses to put game state in a player's PDS. There is no third party to hide the plaintext from, and encrypting data *from the server* would break the game rather than secure it.

So what the client↔server link gets is an **authenticated, encrypted, replay-proof session** — which delivers every one of the four properties in §1 against everyone except the endpoint that is supposed to read the data. That is the real requirement, honestly labelled.

## 3. Why not "just use TLS"

TLS 1.3 is **mandatory** and is not being replaced. This layer runs *inside* it. Three things TLS does not do for this game:

- **TLS authenticates hostnames; this game authenticates DIDs.** A certificate says "you reached `sickle.example.org`". It says nothing about *which operator* is behind it, and a home server's address is whatever its operator has this month. Every other trust decision in the system — provenance, validator reputation, the allowlist — keys off the DID (`02`). Transport identity that keys off something else is a seam where those decisions can be confused.
- **TLS terminates at the proxy.** Self-hosters run Caddy, nginx, Cloudflare. Past that terminator the traffic is plaintext and unattributable. A compromised or merely misconfigured proxy can forge player intent, and nothing downstream can tell.
- **No CA fits this trust model.** Federation has *no central authority* by construction (Invariant I15), and self-hosted servers are adversarial by design (`03` §1). Anchoring transport identity in the CA system would reintroduce exactly the single point of trust the rest of the architecture spent so much effort removing.

**Defence in depth, with different anchors.** TLS protects against the network; this protects against everything between the TLS terminator and the application, and binds every byte to a DID.

## 4. The construction

A three-message handshake following the **Noise framework's `IK` pattern**, then AEAD-protected frames.

### 4.1 Keys

- **Identity:** the existing **Ed25519 DID key** (`02` §5), unchanged. Same key stack as provenance — still one crypto stack, not two.
- **Transport:** a **separate X25519 key pair**, bound to the DID by an Ed25519-signed **transport key attestation**.

> **Why not convert the Ed25519 key to X25519?** It is mathematically possible and it is a known footgun: each algorithm's security argument assumes the key is used for that algorithm alone. A separate attested key also lets the transport key rotate weekly without touching the long-lived identity every provenance chain in the game is signed against.

The attestation is a signed statement — *DID `X` owns X25519 key `K`, valid from `T1` to `T2`* — with every field length-prefixed before signing, so no two distinct field tuples can produce the same signing input.

### 4.2 Handshake

```
initiator                                              responder
   |-- 1. ephemeral pubkey + transport attestation ------->|
   |<- 2. ephemeral pubkey + attestation + confirmation ---|
   |-- 3. confirmation ----------------------------------->|
   |                  channel established                  |
```

Three Diffie-Hellman operations feed key derivation, and each buys a specific property:

| DH | Buys |
|---|---|
| ephemeral × ephemeral | **Forward secrecy.** Seizing a server's disk next year does not decrypt this year's recorded traffic. |
| initiator ephemeral × responder static | **Responder authentication.** Only the holder of that transport key can complete it. |
| initiator static × responder ephemeral | **Initiator authentication**, symmetrically. |

Because both static keys participate, a machine-in-the-middle holding neither cannot make the two sides agree on a key — they derive different keys and confirmation fails.

**The transcript hash is the KDF salt.** A running SHA-256 over the protocol label, the caller's prologue, and every handshake byte is used as the HKDF salt. Any tampering anywhere in the handshake changes it, so the two sides derive different keys and the session dies loudly instead of silently continuing in a downgraded state.

`HKDF-SHA256(salt = transcript, ikm = dh1‖dh2‖dh3, info = <direction label>)` → one AES-256 key **per direction**, so a frame cannot be reflected back at its sender and accepted.

### 4.3 Frames

`AES-256-GCM`. Each frame is `version ‖ direction ‖ sequence` followed by ciphertext and a 128-bit tag. The header is the AEAD **associated data**, so it is authenticated but not encrypted — meaning the sequence number cannot be altered in flight either.

**Nonces are counters, never random.** GCM's one catastrophic failure is nonce reuse — it leaks the authentication subkey and lets an attacker forge arbitrary messages from then on. A fresh key per session plus a strictly increasing per-direction counter has *no* collision risk, whereas random 96-bit nonces have a birthday bound a long-lived connection can actually reach.

Sequence numbers must increase by exactly one. Equal is a replay, lower is a reorder, a gap is a withheld frame; on a reliable ordered transport all three are attacks. A frame that fails its tag does **not** advance the counter, so injecting junk cannot desynchronise a session — otherwise one forged packet would be a cheap denial of service.

### 4.4 Algorithms

| Purpose | Algorithm | Why |
|---|---|---|
| Identity signatures | Ed25519 | already the DID key type (`02` §5) |
| Key agreement | X25519 | same curve family, in `java.base` since JDK 11 |
| Key derivation | HKDF-SHA256 | RFC 5869; `javax.crypto.KDF` since JDK 25 |
| Authenticated encryption | AES-256-GCM | AEAD, hardware-accelerated everywhere |

**Zero third-party crypto dependencies** — all of the above is in `java.base`. That is not incidental: the `protocol` module is shared with the desktop client, where every added dependency is weight in the shipped artifact, and crypto libraries are the last place you want an unnecessary supply-chain edge.

## 5. Explicit non-goals

- **No identity hiding.** Both DIDs are visible to a network observer. Noise's `IK` can encrypt the initiator's static key; this does not, because the complexity buys little — a client's DID is already known to the server it is dialling, and federating servers are publicly listed anyway.
- **No metadata protection.** Traffic timing and volume are observable. Relay chains are an in-fiction mechanic (`../design/08`), not a claim about the real transport.
- **This is not TLS's replacement.** Run inside TLS 1.3.
- **⚠ It does not make the client trustworthy.** This is the most important line in the document. An authenticated channel proves *who* said something and that it arrived unaltered. It says nothing about whether the contents are *true*. A cheating client can hold a flawless channel and send flawlessly authenticated lies about how much ethecoin it mined. **Invariant I14 is completely untouched by any of this: the server still validates everything.** Encryption is not authority, and any future change that treats "it came over the secure channel" as "therefore it is true" is a bug.

## 5a. ⚠ AMENDED 2026-08-02 — the construction is now RFC 9180 HPKE, not hand-rolled Noise

§4 describes a hand-rolled Noise-IK-shaped handshake. On explicit direction the channel moved to
**RFC 9180 HPKE via BouncyCastle** (`protocol/channel/HpkeChannel`), for the reason **T-1** always
gave: reviewed patterns and unreviewed code is a bad bet for the one layer that cannot fail loudly.

- **Mode `auth`, X25519 + HKDF-SHA256 + AES-256-GCM.** Suite pinned, never negotiated — a suite the
  peer can choose is one the peer can choose badly, and there is no legacy to be compatible with.
- ⚠ **Authentication is structural.** `mode_auth` folds the sender's static key into the key schedule,
  so a wrong sender does not raise a mismatch for somebody to check — its frames simply do not open.
- ⚠ **Replay and reorder are rejected by the construction**, via the context's sequence number, rather
  than by a nonce cache that has to be maintained and pruned.
- ⚠ **HPKE contexts are ONE-DIRECTIONAL.** The reverse direction is derived from the exporter secret
  per RFC 9180 §9.8. Reusing one context both ways would repeat sequence numbers under one key — the
  catastrophic AEAD failure — and is the mistake this note exists to prevent.

**§3 is unchanged**: TLS 1.3 stays mandatory and this runs inside it. **§4 is superseded** for new
work; the Noise implementation is still in the tree, still unreviewed, and was never called from
outside `protocol`.

⚠ **T-1 is narrowed, not closed.** The primitive is now a standard from a widely-reviewed library, so
what remains to review is *this repository's use of it* — key distribution, session lifetime, and the
framing above it — which is a much smaller and more tractable question than a bespoke handshake.

## 6. Open questions

- **TS-1: Should this be replaced by a reviewed Noise library?** This is a hand-rolled implementation of a well-understood pattern — safer than inventing a protocol, still not the same as audited code. **Get a cryptographer to review it, or swap in a real Noise implementation, before it protects a live federation.** Recorded as the highest-priority item here.
- **TS-2: Rekeying.** Sessions currently have a hard frame cap and must reconnect. Long-lived federation links may want in-session rekeying instead.
- **TS-3: Transport key rotation and revocation.** Attestations have a validity window, which bounds damage but does not revoke. Does a compromised transport key need a revocation path, or is a short window enough?
- **TS-4: Post-quantum.** X25519 alone is not PQ-safe; harvest-now-decrypt-later applies to any recorded traffic. A hybrid X25519+ML-KEM handshake is the standard answer when it matters. Probably not urgent for a game, but the decision should be conscious.
- **TS-5: Does client↔server need this at all, or is TLS plus per-message signing enough?** The full channel is more machinery than a REST API strictly needs. The argument for it is the untrusted TLS terminator; if that threat is judged out of scope for client links, signed requests inside TLS would be simpler.

## 7. Cross-references

- The DIDs this authenticates: `02-identity-and-auth.md`
- Why untrusted servers exist at all: `03-server-and-federation.md` §1
- The other Ed25519 consumer, and why one key stack: `04-item-provenance.md` §5
- The invariant this must never be read as weakening: `../design/00-vision-and-pillars.md` §4 (I14)
- Open-question log: `../design/15-open-questions.md` A-6

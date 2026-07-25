# 04 — Item Provenance

**Status:** Established (Tech Chat 2)
**Depends on:** `02-identity-and-auth.md` (DIDs), `03-server-and-federation.md`
**Depended on by:** `05-validator-quorum.md` (duel outcomes are provenance events), `06-data-model.md`

The cryptographic record that proves an item's history is legitimate, so a cheating server can't mint or rewrite items and have honest servers accept them. This is the positive half of the anti-cheat model (`03` §4).

---

## 1. Structure — detached JWS over canonicalized JSON

Each provenance record is a **detached JWS** over a **canonicalized JSON payload.**

- **Detached** = the signature does not embed the payload; payload and signature travel separately. This keeps the payload human-readable/debuggable while remaining tamper-evident.
- **Canonicalized** = the JSON is put in a deterministic byte form (see §4) before signing, so the same logical payload always produces the same signature input.

## 2. The payload (the part that gets canonicalized + signed)

```json
{
  "recordVersion": 1,
  "itemId": "uuid-v4-of-the-item",
  "itemType": "hacking_tool_tier2",
  "itemAttrs": { "power": 42, "durability": 0.87 },
  "eventType": "duel_grant | server_grant | trade | initial_mint",
  "holderDid": "did:plc:xxxxxxxx",           // who receives/owns after this event
  "issuerDid": "did:plc:yyyyyyyy",           // home server's DID, OR quorum aggregate ID for duel outcomes
  "prevRecordHash": "sha256-of-previous-record-in-chain-or-null-if-genesis",
  "timestamp": "2026-07-23T18:04:00Z",
  "nonce": "random-128-bit-value"
}
```

Field notes:
- `holderDid` / `issuerDid` are **DIDs** (`02`) — the same identity primitive as players. `issuerDid` is the home server's DID for normal events, or a synthetic quorum identifier for duel outcomes (§3).
- `prevRecordHash` chains this record to the previous one in the item's history (§5).
- `nonce` + `timestamp` prevent **replay** of an old valid record as if it were a new event.
- `itemAttrs` carries the game-relevant stats, so the provenance record *is* the authoritative item definition, not just a receipt.

## 3. The envelope (what actually travels over the wire)

```json
{
  "payload": { ...above... },
  "payloadCanonicalization": "JCS-RFC8785",
  "signature": {
    "alg": "EdDSA",
    "kid": "did:plc:yyyyyyyy#key1",
    "sig": "base64url-signature-bytes"
  }
}
```

### 3.1 Duel outcomes — multi-signature variant

For **duel outcomes**, `issuerDid` is **not** a single DID — it's a synthetic identifier for the quorum round (e.g. `duel:<duelId>`), and the envelope carries an **array of signatures** instead of one:

```json
"signatures": [
  { "kid": "did:plc:validator1#key1", "sig": "..." },
  { "kid": "did:plc:validator2#key1", "sig": "..." },
  { "kid": "did:plc:validator3#key1", "sig": "..." }
]
```

This is the join point with the validator quorum (`05`): a duel's item grant is a provenance event signed by the sampled validator committee rather than by one server.

## 4. Canonicalization choice — JCS (RFC 8785)

`payloadCanonicalization: "JCS-RFC8785"` — JSON Canonicalization Scheme.

Rationale (Tech Chat 2): JCS is the pragmatic choice for JSON — deterministic byte output so signatures verify reliably, while keeping the payload as debuggable/loggable JSON. The alternative is switching the whole envelope to **COSE/CBOR (RFC 9052)**, which sidesteps canonicalization entirely (CBOR has a defined deterministic encoding) — **worth considering only if payload size over the wire actually matters** for the client. Otherwise JWS/JSON is simpler to debug and log. (Tracked as open A-3 in `../design/15`; JWS/JSON is the decision unless wire size forces it.)

## 5. Signature algorithm — EdDSA (Ed25519)

`alg: "EdDSA"`, keys are Ed25519.

Rationale (Tech Chat 2): smaller keys/signatures and faster verification than RSA, **and it's the DID key type AT Protocol already uses** (`02` §5) — so the game runs **one crypto stack** for both player identity and item provenance, not two. `kid` is a DID fragment (`did:...#key1`) identifying which key signed.

## 6. Chain model — per-item (decided)

**Decision (Tech Chat 2): `prevRecordHash` chains per-item** — one chain per item's full history — **not** per-holder.

- **Per-item** (chosen): simpler to verify and audit in isolation; the standard choice given items move between servers and players independently.
- Per-holder (rejected): one chain per player interleaving all their items — cheaper to store, but verifying any single item means walking a chain full of unrelated events.

### 6.1 Player-facing history (a requirement, not just a nicety)

The user explicitly wanted players to **view an item's chain of events.** Because the chain is per-item, "show the history of this item" is just walking `prevRecordHash` back to genesis and rendering each record's `eventType`, `issuerDid`, and `timestamp` — **no extra schema needed.**

Two additions make that lookup fast instead of a full walk every time:

1. **Index records by `itemId`** in local storage — each server keeps a queryable copy of the chains for items currently held on it.
2. **Store `chainDepth` in the payload** (position in the chain, genesis = 0) so a client can request "records N through N+20" instead of always walking from the tip.

> **[PROPOSAL]** `chainDepth` is an *addition* proposed in Tech Chat 2 on top of the §2 payload; when implementing, add `chainDepth` to the payload schema (and thus it becomes part of what's signed). Noted here because the §2 block above is the original schema and this field extends it.

### 6.2 Offline verifiability

Signatures stay **verifiable offline**: a player's client can re-verify the whole displayed chain against the DID public keys **without trusting the server's UI** to have checked it. The client independently confirms the history it's shown.

## 7. Verification algorithm

A verifier checks, for a duel-outcome (multi-sig) record:

1. **Each signature resolves to a validator that was actually sampled for that duel** (cross-reference the quorum's sampling record, `05`).
2. **The summed reputation-weight of valid signatures clears the `2f+1`-of-`3f+1` threshold** (`05` §BFT).
3. **`prevRecordHash` correctly chains back** — walking the chain to genesis must never hit a break or a record signed by an unauthorized issuer.

For a single-issuer record (mint/server_grant/trade), step 1–2 collapse to "the single signature resolves to the authorized issuer DID for that event type," and step 3 is unchanged.

A chain that fails any check is **not recognized** — which, federation-wide, is how a cheating server's fabricated items become worthless (`03` §4).

## 8. Implementation checklist

- [x] Ed25519 signing/verification (reuse the AT Proto key stack, `02` §5). — `protocol` `crypto/Ed25519Signatures`
- [x] JCS (RFC 8785) canonicalizer for the payload. — `protocol` `crypto/JsonCanonicalization`, tested against the RFC's published vectors
- [x] Detached-JWS envelope encode/decode (single-sig and multi-sig variants). — `protocol` `provenance/ProvenanceJson`
- [ ] Per-item chain storage in Postgres, indexed by `itemId`, with `chainDepth` for range queries (`06`). — **server**, not started; blocked on the A-4 data-access decision in `../design/15`
- [x] Chain-walk verifier implementing §7, runnable client-side (offline) and server-side. — `protocol` `provenance/ProvenanceChainVerifier`; does no I/O, reads no clock, and takes key resolution / issuer authority / duel committees as caller-supplied interfaces
- [x] Replay protection via `nonce` + `timestamp` validation. — same verifier; policy is P-7 in `../design/15`
- [ ] Item-history UI walking the chain (§6.1). — **client**, not started

> The verifier makes several calls this document leaves open — the `prevRecordHash` format, how a duel's unverifiable signatures are treated, whether the quorum threshold binds on validator count as well as weight. All are tagged `[PROPOSAL]` in the code and listed as **P-1 … P-7** in `../design/15` §2. The first three are one-way doors.

## 9. Cross-references

- Who signs duel outcomes and how the committee is chosen: `05-validator-quorum.md`
- The DIDs used as `holderDid`/`issuerDid`/`kid`: `02-identity-and-auth.md`
- Where chains are stored: `06-data-model.md`
- Non-recognition of unverifiable items: `03-server-and-federation.md` §4
- Envelope-format open question: `../design/15` A-3

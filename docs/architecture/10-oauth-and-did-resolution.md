# 10. AT Proto OAuth and DID resolution

> **Status: §1 is DECIDED (2026-08-02) — Option C. Everything else is [PROPOSAL] and unbuilt.**
>
> This document was opened 2026-07-28 to hold the sign-in work in one place. On **2026-08-02** its
> claims were checked against the atproto specifications, the blocking decision in §1 was made, and
> **three factual errors elsewhere in the repo were found and corrected** (§5). Every "unverified"
> marker the first draft carried has been resolved.
>
> `02-identity-and-auth.md` remains the Established source of truth for *what* AT Proto identity is
> for. This document owns *how it gets built*.
>
> **Sources, all read 2026-08-02.** The atproto specs are authoritative and are cited per-claim below:
> [OAuth](https://atproto.com/specs/oauth) · [DID](https://atproto.com/specs/did) ·
> [Handle](https://atproto.com/specs/handle) · [XRPC / inter-service auth](https://atproto.com/specs/xrpc) ·
> [did:plc](https://web.plc.directory/spec/v0.1/did-plc) ·
> [granular scopes discussion](https://github.com/bluesky-social/atproto/discussions/4118).
> Bluesky's [OAuth client guide](https://docs.bsky.app/docs/advanced-guides/oauth-client) is a
> secondary source and was the first draft's only one.

---

## 0. The one-paragraph summary

The server-side shape already exists and is tested: `AtProtoIdentityProvider` → `SignInService` →
`AccountSession(did, handle, characters)` → `PlayerRepository`, with a `handle` column on `players`
and `ResolvedIdentity` already carrying a handle *"so the player's display handle can be refreshed on
every sign-in — kept current without ever becoming the thing the mapping is keyed on"*. What is
missing is (a) a real provider behind that interface, (b) any network client at all, and (c) any
transport between the desktop client and the server (**CL-8**). The decision that used to block all
of it is made: **§1, Option C.**

---

## 1. ✅ DECIDED — the desktop client is the OAuth client; the server verifies independently

**Option C.** The desktop client runs the OAuth flow as a **public client**; the home server does
**not** take the client's word for who it is, and verifies the DID over its own path.

### Why not Option A (home server as confidential client)

AT Proto requires a client's `client_id` to **"exactly match the full URL used to fetch the client
metadata JSON itself"** ([OAuth spec §client metadata](https://atproto.com/specs/oauth)). There is no
shared registration and no dynamic registration to fall back on (§3.1). So a server-as-client design
means **every self-hosted home server needs its own public HTTPS domain, its own certificate and its
own keypair** before anyone can sign in to it. The project's pitch is a spare box and a
`docker compose up`; a mandatory domain-and-certificate step is a tax on exactly the thing that is
supposed to be easy. Rejected on that ground alone — its security properties are otherwise the best
of the three.

### Why not Option B (client flows, server trusts the result)

One metadata document, self-hosters need nothing, simplest to build — and the server would be
accepting a client's assertion of *"I am this DID"* with nothing behind it. That is a forgeable
sign-in in a game whose social layer (`../design/12-identity-and-social.md`) runs on informants,
dossiers and an evidence threshold. Rejected as a shipping design; it is however the honest
description of **stage 3a** below, and is why stage 3b is not optional.

### What Option C actually is

1. The client resolves the player's handle → DID → PDS → authorization server, and runs
   PAR + PKCE + DPoP against it (§3). Scope is **`atproto` and nothing else** (§3.6).
2. The client asks the player's own PDS for a short-lived **service-auth JWT**
   (`com.atproto.server.getServiceAuth`) with `aud` set to **the home server's DID**.
3. The client presents that JWT to the home server. The server verifies the signature against the
   **`#atproto` verification method in the player's DID document**, which it resolves itself (§4.2).

⚠ **Step 3 is the whole point: the server's trust comes from a signature it checked against a
document it fetched, not from a field in a request body.** No token, no refresh token and no DPoP
key ever reaches the server — the JWT is a 60-second bearer assertion scoped to one audience.

✅ **It reuses W-1.** The DID-document resolver the server needs here is the same one
`04-item-provenance.md` needs for provenance keys. Build it once.

⚠ **Its one dependency is not fully shipped.** Obtaining a service-auth JWT requires the granular
`rpc:` scope (`rpc:com.atproto.server.getServiceAuth?aud=did:web:<server>`). Bluesky reports granular
permissions **"implemented on the `bsky.social` hosting service, and rolling out to the self-hosted
PDS distribution"**, while advising against shipping on them until permission sets are complete and
the final spec is published ([discussion 4118](https://github.com/bluesky-social/atproto/discussions/4118),
read 2026-08-02). This is why §7 stages the verification leg last and why stage 3a exists.

⚠ **Consequence for I14 to state plainly:** tokens and a DPoP keypair now live on the player's
machine. I14 governs *game state*, not credentials, so this does not violate it — but it does mean
§4.4's storage rule is load-bearing rather than advisory, and `ClientProfile` must never see any of
it.

⚠ **Public clients are capped at a two-week session lifetime** ([OAuth spec](https://atproto.com/specs/oauth)).
A player who does not open the game for a fortnight signs in again. That is acceptable and should be
said in the UI rather than discovered.

---

## 2. Bugs that exist today, independent of OAuth

Carried unchanged from the first draft; **not re-verified on 2026-08-02.**

- **Online mode displays the solo character's handle.**
  `EyeAndSickleClient.connectOnline` constructs
  `new RemoteGameSession(URI.create(address), profile.settings().soloHandle)`, and
  `RemoteGameSession.handle()` returns that constructor argument forever. Connecting to a server
  shows whatever the player named their *offline* character, and since 2026-07-28 it also appears in
  the command-strip prompt (`handle@hostname.local:~$`).
  **Honest fix today:** show the DID, or `not signed in`, until sign-in exists.

- **There is no transport.** `RemoteGameSession` refuses every intent with `EX_UNAVAILABLE`
  (**CL-8**). Nothing can carry an `AccountSession` to the client, so a resolved handle currently has
  nowhere to arrive.

---

## 3. Protocol requirements — verified

All mandatory. None optional for any client type. Every claim here was checked against
<https://atproto.com/specs/oauth> on 2026-08-02.

### 3.1 ⚠ There is no dynamic client registration

AT Proto uses **client-ID-metadata-document**, not RFC 7591. Authorization servers advertise
`client_id_metadata_document_supported: true`, and the `client_id` *is* the metadata URL — the server
fetches it. There is no registration request, no client secret issuance and no registration endpoint.

⚠ **`AtProtoIdentityProvider`'s javadoc said otherwise** and was corrected on 2026-08-02 (§5.3).

### 3.2 Client metadata document

Served over HTTPS at the URL that is its own `client_id`.

| Field | Requirement for this project |
|---|---|
| `client_id` | Must exactly equal the URL the document is fetched from |
| `application_type` | **`native`** (default is `web`) |
| `scope` | **`atproto`** — must declare every scope that may be requested |
| `grant_types` | `authorization_code`, `refresh_token` |
| `response_types` | `code` |
| `redirect_uris` | At least one; see §3.3 |
| `dpop_bound_access_tokens` | Must be `true` |
| `token_endpoint_auth_method` | Omitted / `none` — we are a public client |
| `client_name`, `client_uri`, `logo_uri`, `tos_uri`, `policy_uri` | Recommended; this is what the player sees on the consent screen |

`jwks` / `jwks_uri` and `private_key_jwt` are **confidential-client only** and do not apply under
Option C.

### 3.3 ✅ Redirect URIs, and the localhost development exemption

This is the finding that most reduces the cost of the work.

- **Development:** `client_id` may be the literal string **`http://localhost`** (no port). The
  authorization server synthesises virtual metadata with `token_endpoint_auth_method: none`,
  `application_type: native`, and default redirect URIs `http://127.0.0.1/` and `http://[::1]/`.
  ⚠ **Port numbers are ignored in redirect matching; paths must match exactly.**
- **Release:** one HTTPS metadata document hosted once by the project, for every player, with a
  loopback redirect (`http://127.0.0.1/…`) or a reverse-domain custom scheme
  (`io.github.stoicswe.eyeandsickle:/callback` — the scheme must be reverse-domain-ordered relative
  to the `client_id` hostname, then a single colon, a single slash, then the path).

✅ **The entire client-side flow can therefore be built and tested against real Bluesky accounts with
no domain, no certificate and no hosting**, and the only thing that changes at release is a constant.

### 3.4 Discovery chain

Handle → DID → DID document → PDS → auth server. Every hop is a network fetch and every one is
attacker-influenced (§4.3).

1. Resolve the handle to a DID (§4.1).
2. Resolve the DID to its document (§4.2).
3. Read the PDS from the document's `service` array.
4. `GET <pds>/.well-known/oauth-protected-resource` → `authorization_servers[0]`. Must be an HTTPS
   origin with no credentials, path, query or fragment.
5. `GET <as>/.well-known/oauth-authorization-server` → `issuer` (must match the fetch origin),
   `authorization_endpoint`, `token_endpoint`, `pushed_authorization_request_endpoint`.

A conforming server declares `require_pushed_authorization_requests: true`,
`code_challenge_methods_supported` containing `S256`, `dpop_signing_alg_values_supported` containing
`ES256`, and `scopes_supported` containing `atproto`.

### 3.5 PAR, PKCE, DPoP

- **PAR** is required for all client types. POST form-encoded parameters to the PAR endpoint, receive
  a `request_uri`, then redirect with **only** `request_uri` and `client_id` in the query.
- **PKCE** `S256` only; `plain` is forbidden; a new random challenge on **every** request. Servers
  must reject `code_challenge` reuse for at least 24 hours.
- **DPoP** — **ES256 (NIST P-256)** mandatory. A **new keypair per auth session**, never per client
  and never exported or moved between devices. A DPoP proof on every token request and every PDS
  request, each with a unique random `jti`.
  ⚠ **Nonces are mandatory and stateful.** The server sends `DPoP-Nonce` and rotates it on a
  ≤5-minute lifetime; the client tracks nonces **per account-session and per server**, and **must
  reject any response that omits the header when DPoP was used in the request.** This is the part
  that makes a naive HTTP client insufficient.

### 3.6 ✅ Scope — `atproto` alone is correct, and that resolves §3 of `02`

The bare **`atproto`** scope *"grants no PDS resource access by itself; primarily used for
authentication"*. It is exactly and only what this game wants, and it satisfies
`02-identity-and-auth.md` §3's requirement to **never request write scope** without qualification.

- ⚠ **`transition:generic` must not be requested.** It is App-Password-equivalent breadth — write any
  record type, upload blobs, read/write preferences, proxy most endpoints. Requesting it would
  violate `02` §3 outright.
- The **one** additional scope this design will eventually need is
  `rpc:com.atproto.server.getServiceAuth?aud=did:web:<home-server>` for §1 step 2 — narrow by
  construction, since `rpc:` scopes are bound by both `lxm` (the method) and `aud` (the audience),
  and only one of the two may be a wildcard.

### 3.7 Token lifetimes and refresh

- Access tokens: **≤30 minutes** (15 if individual revocation is impossible; 5 recommended).
- **Public-client sessions: 2 weeks maximum.**
- Refresh tokens are **single-use** — each refresh returns a replacement.
- ⚠ Therefore a **single-flight lock around refresh is mandatory, not a nice-to-have**: two
  concurrent refreshes race and can invalidate each other, ending the session.

---

## 4. Security requirements

### 4.1 ⚠ Bidirectional handle verification — the one that must not be skipped

A DID document's `alsoKnownAs` is written by the DID controller. It is a **self-asserted claim**, and
anyone can put `at://<a-rival's-handle>` in their own document. The handle spec is explicit:
handles *"should not be trusted or considered valid until the DID is also resolved and the current
DID document is confirmed to link back to the handle"*.

**The verified procedure:**

1. **Handle → DID**, by either of two methods:
   - **DNS:** `TXT` record at **`_atproto.<handle>`**, value **`did=did:plc:…`**.
     ⚠ Any TXT value not starting with `did=` must be **ignored, not treated as a failure** — other
     records legitimately live on that name.
   - **HTTPS:** `GET https://<handle>/.well-known/atproto-did` → 2xx, `Content-Type: text/plain`,
     body is the **bare DID with no prefix or wrapper**.
   - ⚠ **On conflict, DNS wins.** The spec states the DNS TXT result should be preferred.
2. **DID → document** (§4.2), and read the claimed handle from `alsoKnownAs`.
3. **Confirm the two agree.** If not, the handle is invalid and must never be drawn as verified.
   (Bluesky's own clients render this case as `handle.invalid`.)

**Why this matters more here than in a social app.** `../design/12-identity-and-social.md` has
informants, compiled dossiers, an evidence threshold and a mass-vote override, and
`../design/01-core-resources.md` §2.2 makes the public ledger *"a gameplay feature… it gives
investigators — player and NPC — something to work with"*. A forged display name on any of those
surfaces is not cosmetic; it is an attack on the mechanic.

**The rule to encode:** the DID is what everything is keyed on; the handle is a **cache with a
verified flag**; an unverified handle is never drawn as if it were verified. Handles are also
re-claimable after release, which is the second reason to refresh on every sign-in and never key
anything on one.

### 4.2 DID document resolution — verified endpoints

- **`did:plc`** → `GET https://plc.directory/<did>`. Audit log at `https://plc.directory/<did>/log/audit`;
  current state at `https://plc.directory/<did>/data`. Rate limits exist but are undocumented and
  described as generous — ⚠ **cache with a TTL anyway**, because this is a single point of failure
  for every sign-in on every home server.
- **`did:web`** → `GET https://<hostname>/.well-known/did.json`.
  ⚠ **Hostname-level only — path-based `did:web` is excluded by the atproto spec** and must be
  rejected rather than resolved.

The document carries `alsoKnownAs` (handle claims, `at://` prefixed), `verificationMethod` (signing
keys) and `service` (PDS location).

### 4.3 SSRF

This is the **first outbound HTTP the server makes**, and every URL in §3.4 and §4.1 is derived from
a user-supplied handle or from a document the user controls. A hardened HTTP client with timeouts,
response size limits, a redirect limit, and an address-range denylist (loopback, link-local, RFC 1918,
IPv6 ULA, and the cloud metadata addresses) is **required, not advisable**. One implementation, one
place, so the rules are written once — see §6.

### 4.4 Token storage

*"Access and refresh tokens should never be copied or shared across end devices."* Under Option C
they live on the player's machine, so this is now a client-side obligation.

⚠ **They must never reach `ClientProfile`**, whose own comment already says: *"No credentials and no
tokens are ever written here — the profile is a plain unencrypted JSON file in a conventional
location."* Storage needs its own encrypted-at-rest location, and the DPoP private key must never be
serialised anywhere the profile is.

### 4.5 Verify the `sub`, and verify the scope

*"It is **critical** for the client to verify that the `sub` DID matches the account expected."* Two
further checks the first draft did not carry:

- The returned **`scope` must contain `atproto`**, or the session is rejected.
- If a flow began from a server hostname rather than an account identifier, resolve `sub`'s DID
  document, read its declared PDS, and confirm that PDS binds to the authorization server the session
  used — and that the AS `issuer` matches the `iss` in the OAuth response.

---

## 5. ⚠ Errors found elsewhere in the repo — all three corrected 2026-08-02

### 5.1 `02-identity-and-auth.md` §5 named the wrong curve — twice

It said: *"AT Proto DIDs use Ed25519 keys — which is also what the provenance layer signs with (`04`).
One crypto stack for both identity and provenance; no second key system."*

**Both halves are wrong.** The [DID spec](https://atproto.com/specs/did) permits exactly two curves
for `verificationMethod`: **P-256 (secp256r1)** and **secp256k1 (K-256)**. **Ed25519 is not among
them.** So atproto shares a curve with the provenance layer (`crypto/Ed25519Signatures`) — the thing
the sentence was asserting — **not at all**, and the "no second key system" argument that partly
justified choosing AT Proto does not survive contact with the spec.

⚠ **The real inventory, once §1's design is built, is three curves:**

| Curve | Used for | Where |
|---|---|---|
| **Ed25519** | Item provenance, secure channel | `protocol` / `server` `crypto/`, existing |
| **ES256 / P-256** | DPoP, mandatory for OAuth | client, new |
| **ES256K / secp256k1** | Verifying service-auth JWTs | server, new |

⚠ **ES256K is the one with a tooling cost, and it is worse than "add a dependency" — it is
RUNTIME-DEPENDENT.** Measured 2026-08-02 on two JDK **26** builds on one machine:

| Step | OpenJDK 26 (SunEC) | Semeru 26 (OpenJ9) |
|---|---|---|
| `AlgorithmParameters.init(secp256k1)` | ✅ OK | ✅ OK |
| `KeyFactory.generatePublic` | ✅ OK | ✅ OK |
| `Signature.initVerify` | ✅ **OK — the trap** | ✅ OK |
| `Signature.verify` | ❌ *Curve not supported* | ✅ OK |
| `KeyPairGenerator` | ❌ *Curve not supported* | ✅ OK |

So **three API layers report success on a JVM that cannot verify the curve**, `initVerify` included.
Any cheap availability probe returns true in exactly the case that matters, and the real failure lands
on the request path when a federated signature arrives.

- ⚠ **The same jar therefore works on one operator's JVM and not another's.** This is not a build
  decision that can be made once.
- **Confirmed against a real account:** `did:plc:ewvi7nxzyoun6zhxrhs64oiz` (the atproto docs' own
  example) publishes a **secp256k1** `#atproto` key. Most `did:plc` accounts do.
- ✅ **RESOLVED 2026-08-02 — the server declares BouncyCastle**, for this one reason. Server only: the
  client needs P-256 and Ed25519, which the platform has everywhere, so the five platform jars and the
  jpackage image are untouched.
- ⚠ **Registering BouncyCastle is NOT sufficient, and this is the trap inside the trap.** Measured with
  BC registered on OpenJDK 26: `Signature.getInstance("SHA256withECDSA")` is **still answered by
  SunEC**, which accepts the key and refuses at `verify()` — the JCA never falls through, because
  nothing reported a problem in time. **The provider must be named explicitly.**
- ⚠ **And the algorithm name differs by provider.** JOSE needs raw `R‖S`. SunEC calls that
  `SHA256withECDSAinP1363Format`; **BouncyCastle does not have that name at all** and calls it
  `SHA256WITHPLAIN-ECDSA`. Neither works on the other. So `MultibaseKey` caches a *(provider,
  algorithm)* pair per curve, probed with a real sign-and-verify round trip.
- `MultibaseKey.secp256k1Available()` probes `verify()`, and `MultibaseKey.decode` **refuses an
  unusable curve up front** with a message naming the cause, rather than returning a key that fails
  three layers away.

Both curves must be supported, selected from **the key's curve** and never from the JWT's own `alg`
header — a verifier that reads `alg` from the token lets an attacker nominate the algorithm their own
signature is checked with. ⚠ The `kid` must be checked to be `#atproto` rather than whatever key the
token names.

§5 of `02` was corrected in place on 2026-08-02 and now points here.

### 5.2 `deploy/` is silent on the public-URL requirement

⚠ **This is now moot and should stay moot.** §1's Option A was the branch that would have required
every self-hoster to have a domain and a certificate; Option C does not. Nothing needs adding to
`deploy/` — but if §1 is ever revisited, this is the cost that decides it.

### 5.3 `AtProtoIdentityProvider`'s javadoc claimed dynamic client registration

It said *"AT Proto uses dynamic client registration, pushed authorization requests, and DPoP-bound
tokens"*. PAR and DPoP are right; **there is no dynamic client registration** (§3.1). Corrected
2026-08-02.

### 5.4 `../design/15-open-questions.md` W-6 understates the work

Unchanged from the first draft and still true: W-6 reads as "a production provider is missing", when
the provider is one of six pieces, and W-1 shares its network client with all of them. The blocking
half of that note — the undecided §1 — is now closed and the note has been amended.

---

## 6. What lands where in the code

Nothing here needs the `GameSession` port or any view to change.

| Piece | Where | Status |
|---|---|---|
| SSRF guard | `protocol/identity/SsrfGuard` | ✅ **Built.** §4.3, pure and fully tested without a socket |
| Hardened HTTP client | `protocol/identity/HardenedHttpClient` | ✅ **Built.** §4.3, **pinned-address** — see below |
| HTTP/1.1 response reader | `protocol/identity/HttpResponseReader` | ✅ **Built.** Ours because the client drives a socket |
| DID document + resolver | `protocol/identity/DidDocument`, `DidResolver` | ✅ **Built.** §4.2, TTL-cached, `did:plc` + `did:web` |
| Bidirectional handle resolution | `protocol/identity/HandleResolver` | ✅ **Built.** §4.1 |
| Multibase key decoding | `protocol/identity/MultibaseKey` | ✅ **Built.** Ed25519 + P-256 + secp256k1; **W-1**'s decode half |
| DID→key resolution | `server/identity/AtprotoDidPublicKeyResolver` | ✅ **Built.** **Closes W-1** |
| Server seam + no-op default | `server/identity/VerifiedHandleDirectory` | ✅ **Built.** Matches how `DidPublicKeyResolver` is shaped |
| Handle refresh on sign-in | `SignInService` | ✅ **Built.** Verified handles only; see below |
| OAuth client (PAR, PKCE, DPoP, refresh) | `client/oauth/OauthClient`, `Jose`, `DpopKey`, `Pkce` | ✅ **Built.** JOSE is hand-written over JDK primitives — see below |
| Discovery chain | `client/oauth/OauthDiscovery`, `AuthServer` | ✅ **Built.** §3.4, with the issuer-anchoring check |
| Loopback callback | `client/oauth/LoopbackReceiver` | ✅ **Built.** §3.3, ephemeral port, bound to `127.0.0.1` |
| Credential storage | `client/oauth/TokenStore` + keychain/encrypted-file | ✅ **Built.** §4.4 — see below |
| Service-auth JWT verifier | `server/identity/ServiceAuthVerifier`, `ServiceAuthReplayGuard` | ✅ **Built.** §1 step 3, ES256 **and** ES256K |
| Real provider | `server/identity/ServiceAuthIdentityProvider` | ✅ **Built — W-6 closed.** Verifies a signature rather than exchanging a code. `DevAtProtoIdentityProvider` stays as the disabled-by-default fallback |
| Token + DPoP key storage | `client/oauth/`, **not** `ClientProfile` | ✅ **Built.** §4.4 |

### JOSE was hand-written, reversing §6's original advice

This table used to say "use a reviewed JOSE library (e.g. nimbus-jose-jwt)". Decided otherwise on
2026-08-02, on explicit direction: nimbus would be the client's **first third-party runtime
dependency**, carried into five platform jars and the jpackage image, and the client's austerity
(JavaFX, `protocol`, `solo`, an allowlisted `spring-context`) is deliberate.

⚠ **T-1 is not really in play** — its warning is about designing a *handshake*, and this is token
formatting over JDK crypto. But it is still our code signing our tokens, so the one genuinely
dangerous part is called out:

⚠ **`SHA256withECDSAinP1363Format`, never `SHA256withECDSA`.** The latter emits ASN.1 **DER**; JOSE
requires raw **R‖S**, each left-padded to 32 bytes. A hand-rolled DER→raw conversion that forgets to
pad a short `r` works about **255 times in 256** — it ships, and then rejects one login in a few
hundred with no pattern. The P1363 algorithm emits the raw form directly, so the conversion does not
exist. Same class of trap in `Jose.publicJwk`: `BigInteger.toByteArray()` returns 31, 32 or 33 bytes
depending on the value, and a JWK coordinate must always be 32.

### Credential storage: OS keychain, with an honest fallback

⚠ **No desktop store protects against an attacker already running as your user** — an unlocked
keychain answers any process the user runs. What a store can bound is backup/sync leakage, a copied
profile directory, other users of the machine, and casual reading, and those are the exposures that
actually happen.

- **`KeychainTokenStore`** — macOS `security`, Windows DPAPI (`CurrentUser`), Linux `secret-tool`.
  ⚠ **This is the first time the client has ever spawned a subprocess**, which `CLAUDE.md` records as
  a property it did not have. Bounded to that one class: never a shell, always an argument list, and
  ⚠ **the secret goes in on STDIN, never argv** — process arguments are world-readable on Linux and
  visible to `ps` everywhere, so a token on a command line would be worse than the file fallback.
- **`EncryptedFileTokenStore`** — AES-256-GCM, fresh IV per write, `rw-------`. ⚠ **The key sits
  beside the data**, so against a local attacker it is obfuscation, not protection.
  `isPlatformSecured()` is false so the interface can say which mode is in force.
- ⚠ **Neither ever touches `ClientProfile`**, whose comment promises it holds no credentials. The
  DPoP private key counts as one.

⚠ **Two macOS findings, both from probing the real keychain rather than from review.**
`security add-generic-password -w` with no value reads from stdin — and **prompts twice**, so a single
write fails the retype, stores nothing, prints "passwords don't match" and **still exits 0**. It fails
as a success. And a missing item is reported as a non-zero exit (44), not as empty output, so reading
after a sign-out threw where it should have reported absence. Both are why
`KeychainTokenStore.available()` **round-trips a canary** instead of checking that a binary exists.

### ⚠ The placement changed: this landed in `protocol`, not in `server` and `client`

The first version of this table said "one implementation per module, so the SSRF rules are written
once each" — which is two SSRF denylists, i.e. **one denylist that is wrong**. It went into
`protocol/identity` instead, because both sides genuinely need it: `04-item-provenance.md` §6.2
requires the provenance verifier to run *client-side and offline*, and that verifier's missing half is
exactly DID→key resolution (**W-1**).

⚠ **This amended `protocol`'s charter**, which said the module holds "exactly two things", and it is
now three. The argument is in `protocol/identity/package-info.java`; the new thing is that this module
now does **network I/O**, which it never did before. `ArchitectureRulesTest` confines that to
`identity` and refuses it everywhere else, so it cannot become a precedent the wire types follow.

### Three findings from building it

1. ⚠ **The charter's name check caught `DidDocument.Service`** — a DID document's own word for an
   endpoint, and a rule that refuses any type here ending in `Service`. Renamed to `ServiceEndpoint`
   rather than carving the first exception into a deliberately blunt rule.
2. ⚠ **`SignInService` needed three handle states, not two.** "No resolver wired" and "checked,
   nothing verified" must not collapse into one `null` — otherwise a server whose DNS breaks silently
   falls back to displaying unverified handles, which is the exact outcome §4.1 exists to prevent.
   Hence `VerifiedHandleDirectory.canVerify()`.
3. ⚠ **Handle verification ships OFF, and not because it is unfinished.** There is no real identity
   provider yet, so verifying the handle of an identity nobody authenticated is a check that looks
   like security and is not. `eyeandsickle.identity.handle-resolution.enabled` turns it on; it belongs
   on at stage 5.

⚠ **`SignInCredentials` needs one more field** — the service-auth JWT — and its javadoc's claim that
a production provider *"completes a code-exchange and reads `authorizationCode`/`state`/`redirectUri`"*
becomes wrong the moment Option C is built. Those three fields belong to the client now.

### ✅ DNS rebinding — closed 2026-08-02

Previously recorded here as an accepted residual. It is fixed, and the fix is why
`HardenedHttpClient` no longer uses `java.net.http`.

The hole: `SsrfGuard` judges an **already-resolved** address, and `java.net.http` resolves the name
**again** when it connects. An attacker running authoritative DNS with a one-second TTL answers the
first lookup with a public address and the second with loopback; every check passes.

The fix: resolve once, check every address, connect a plain `Socket` to **the address**, then layer
TLS with `SSLSocketFactory.createSocket(Socket, String host, int, boolean)` — the four-argument form
that takes the hostname *separately*, so SNI and certificate verification use the real name while the
connection stays pinned. There is no second lookup left to poison.

- ⚠ **`setEndpointIdentificationAlgorithm("HTTPS")` is load-bearing.** A raw `SSLSocket` validates the
  certificate *chain* but **not** that the certificate is for the host you asked for. Without it the
  pinning is worse than useless — any host with a valid certificate for anything could answer.
- ⚠ **The cost is a hand-written HTTP/1.1 reader.** It is split into `HttpResponseReader` precisely so
  it can be tested against a `ByteArrayInputStream`: chunked bodies, oversize bodies that lie about
  `Content-Length`, header floods, endless header lines, unrequested `Content-Encoding`, short chunks.
  This is the riskiest code in the package.
- **Verified live** against `plc.directory` and a real bidirectional resolution of `bsky.app`.

### `java.naming` — measured, and now loud rather than silent

⚠ `TxtLookup.system()` needs the `java.naming` module; without it, DNS lookups fail and handle
resolution degrades to the HTTPS method alone — a **smaller** set of resolvable handles, and DNS is
the method the spec says wins on conflict.

✅ **Measured 2026-08-02: it is present.** jpackage's non-modular mode links **51** JDK modules and
`java.naming` is among them, so the shipped image is fine. ⚠ That holds only while nothing adds
`--add-modules` to trim the ~135 MB image, which this repo already wants to do.
`TxtLookup.systemAvailable()` now exists and the server **logs a warning at startup** if the provider
is missing, so the degradation is stated rather than discovered.

**Library position.** `io.github.kikin81.atproto` (atproto-kotlin) is the only JVM OAuth
implementation found on Maven Central as of 2026-08-02 — MIT, JDK 17, with a PAR + PKCE + DPoP
module. It is new and single-maintainer. ⚠ **Undecided:** whether to take it or to drive
nimbus-jose-jwt directly. The flow itself is stock OAuth 2.0 with three extensions, so the second
option is defensible and avoids depending on unreviewed code for the one thing T-1 says this repo
already has too much of.

---

## 7. Order of work

Stages 1 and 2 were worth doing **whatever happened to §1**, and both are done.

1. ✅ **DONE (2026-08-02) — the wrong-handle bug** (§2). `RemoteGameSession` no longer takes a handle
   in its constructor; it reports `not signed in` until something signs in, then a *verified* handle,
   falling back to the DID. It never borrows the solo character's name again.
2. ✅ **DONE (2026-08-02) — the shared floor.** `protocol/identity`: SSRF guard, hardened HTTP client,
   DID document + resolver with TTL cache, bidirectional handle resolution, plus the server seam and
   the sign-in handle refresh. Every regression test was verified against the unfixed code first.
   ✅ **W-1 is CLOSED (2026-08-02).** `MultibaseKey` decodes base58btc + multicodec into an Ed25519,
   P-256 or secp256k1 key, and `AtprotoDidPublicKeyResolver` supplies `DidPublicKeyResolver`. ⚠ Behind
   `eyeandsickle.identity.resolution.enabled`, off by default — and on stock OpenJDK secp256k1 keys are
   refused with an explanatory message rather than returned broken (§5.1).
3. ✅ **DONE (2026-08-02) — the client-side OAuth flow.** `client/oauth/`: discovery, PKCE, DPoP with
   per-origin nonce tracking, PAR, code exchange, single-flight refresh, loopback callback, and
   credential storage. ⚠ **Not yet wired to a screen** — there is no sign-in UI and nothing calls
   `OauthClient` from the deck; that and `RemoteGameSession.identify()` are what remain of this stage.
   Against `client_id = http://localhost` (§3.3). Real Bluesky
   accounts, no hosting, no certificate. Ends with the client holding a verified DID.
4. **The transport** (**CL-8**), carrying an `AccountSession` back to the client — or before 3, if a
   client that can sign in but not play is the wrong shape to debug.
5. **3a — server accepts the client's DID** with the trust gap of Option B, **behind the same
   disabled-by-default flag `DevAtProtoIdentityProvider` already uses.** ⚠ This is not a shipping
   state and the flag is what says so.
6. ✅ **DONE (2026-08-02) — the service-auth JWT leg** (§1 steps 2–3). `ServiceAuthVerifier` +
   `ServiceAuthReplayGuard` + `ServiceAuthIdentityProvider`, behind
   `eyeandsickle.identity.resolution.enabled`. **W-6 is closed.**
   ✅ **The client half landed the same day** — `ServiceAuth` mints the token from the player's PDS,
   `HomeServerSignIn` presents it, and `SignInController` / `ServerIdentityController` receive it. The
   loop is closed end to end in code.
7. **The release metadata document** — one HTTPS URL, hosted once by the project (§3.3).

### §7 — what remains, stated exactly

The loop is complete in code and has **not** been run against a live PDS end to end. What is known to
be outstanding:

- ⚠ **The `rpc:` scope is still rolling out.** The client requests
  `atproto rpc:com.atproto.server.getServiceAuth?aud=*` and **falls back to `atproto` alone** when an
  older authorization server refuses the whole request — such a player signs in but cannot join a home
  server, which `Identity.canJoinHomeServer()` reports up front rather than failing at the join.
  ⚠ `lxm` is pinned and only `aud` is wildcard: the spec permits one, and the method is fixed forever
  while the home server is not chosen until after sign-in.
- ⚠ **The home server names its own audience.** A token is bound to an `aud`, so the client must learn
  the server's DID before minting — and today it asks the server (`GET /api/server`). A hostile server
  cannot *use* a token minted for someone else (`aud` is checked against the receiving server's own
  DID), but it can induce a client to mint one for a third party and relay it there. Closing this needs
  the DID to arrive from somewhere the server does not control — `08`'s signed descriptor, or a
  discovery list (`11`). Recorded in both `HomeServerSignIn` and `ServerIdentityController`.
- **Replay state is in memory** — see `ServiceAuthReplayGuard`: not restart-durable, per-process, both
  fine for a ≤5-minute token on a single-process home server and both wrong behind a load balancer.
- **`RemoteGameSession` still has no game transport** (**CL-8**). Sign-in works; playing does not.

---

## 8. Cross-references

- What AT Proto identity is *for*, and why it was chosen: [`02-identity-and-auth.md`](02-identity-and-auth.md)
- DID→key resolution for provenance (**W-1**, shares the resolver): [`04-item-provenance.md`](04-item-provenance.md)
- The hand-rolled-crypto warning (**T-1**): [`07-transport-security.md`](07-transport-security.md) §6
- The stubs register (**W-1**, **W-6**) and **CL-8**: [`../design/15-open-questions.md`](../design/15-open-questions.md)
- Why a forged handle is a mechanic-level problem, not a cosmetic one: [`../design/12-identity-and-social.md`](../design/12-identity-and-social.md)

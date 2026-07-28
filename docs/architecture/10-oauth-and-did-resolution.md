# 10. AT Proto OAuth and DID resolution — the pending work

> **Status: ⚠️ [PROPOSAL] — nothing here is built, and one item is a decision nobody has made yet.**
>
> This is a working document, written 2026-07-28 to hold the sign-in work in one place while it is
> thought over. It is **not** a decision record. `02-identity-and-auth.md` is the Established doc and
> stays the source of truth for *what* AT Proto identity is for; this collects *what it would take to
> actually ship it*, what it costs, and the three places the existing documents turn out to be wrong
> or silent.
>
> **Sources.** Bluesky's OAuth client guide (<https://docs.bsky.app/docs/advanced-guides/oauth-client>),
> read 2026-07-28. Direct quotations below are from it. Anything marked *unverified* has **not** been
> checked against the atproto specs and must be before it is relied on.

---

## 0. The one-paragraph summary

The server-side shape already exists and is tested: `AtProtoIdentityProvider` → `SignInService` →
`AccountSession(did, handle, characters)` → `PlayerRepository`, with a `handle` column on `players`
and `ResolvedIdentity` already carrying a handle *"so the player's display handle can be refreshed on
every sign-in — kept current without ever becoming the thing the mapping is keyed on"*. What is
missing is (a) a real provider behind that interface, (b) any network client at all, (c) any
transport between the desktop client and the server, and (d) **a decision about who the OAuth client
is**, which everything else depends on.

---

## 1. ⚠ The blocking decision: who is the OAuth client?

Everything below branches on this, so it is first.

AT Proto requires that a client publish a metadata document on the public web, and that its
`client_id` **"exactly match the full URL used to fetch the client metadata JSON itself"**.

### Option A — the home server is a confidential client

What `02-identity-and-auth.md` §5 currently implies: *"the home server resolves the DID at sign-in and
caches the identity → DID mapping in Postgres."*

- ✅ Tokens never touch the player's machine. Cleanest against **I14**.
- ✅ `private_key_jwt` client authentication is available, which is the stronger option.
- ⚠️ **Every self-hosted home server needs a publicly reachable HTTPS URL**, because that URL *is*
  the `client_id`. A game whose selling point is that anybody can run a server from a spare box now
  requires each of them to have a domain and a certificate. `deploy/` says nothing about this today.
- ⚠️ Each server is a *separate* OAuth client with its own metadata and its own keys. There is no
  single "The Eye and Sickle" client registration.

### Option B — the desktop client is a public client

- ✅ One metadata document, hosted once by the project, for every player.
- ✅ Self-hosters need nothing public.
- ⚠️ Tokens and the DPoP keypair live on the player's machine. Needs checking against **I14** —
  I14 is about *game state*, not credentials, so this is probably fine, but it has not been argued.
- ⚠️ The server then has to trust something the client hands it, which means the client's assertion
  of "I am this DID" needs its own verification path. This is the direction in which sign-in becomes
  forgeable if it is done casually.

### Option C — both

The desktop client does the OAuth dance and the server independently verifies the resulting
identity. More moving parts; possibly the honest answer for a federated game.

> **Nothing below can be sized until this is picked.** Recommendation is not offered here on purpose —
> it is a product decision about how hard self-hosting is allowed to be.

---

## 2. Bugs that exist today, independent of OAuth

These are real now and do not need any of the above.

- **Online mode displays the solo character's handle.**
  `EyeAndSickleClient.connectOnline` constructs
  `new RemoteGameSession(URI.create(address), profile.settings().soloHandle)`, and
  `RemoteGameSession.handle()` returns that constructor argument forever. Connecting to a server
  shows whatever the player named their *offline* character. Since 2026-07-28 it also appears in the
  command-strip prompt (`handle@hostname.local:~$`), so it is now more visible than it was.
  **Honest fix today:** show the DID, or `not signed in`, until sign-in exists.

- **There is no transport.** `RemoteGameSession` refuses every intent with `EX_UNAVAILABLE`
  (**CL-8**). Nothing can carry an `AccountSession` to the client, so a resolved handle currently has
  nowhere to arrive.

---

## 3. Protocol requirements

All of these are **mandatory** per the client guide. None is optional for any client type.

### 3.1 Client metadata document

Served over HTTPS at the URL that is its own `client_id`.

| Field | Requirement |
|---|---|
| `client_id` | Must exactly equal the URL the document is fetched from |
| `scope` | *"The `atproto` scope is required"* |
| `grant_types` | `authorization_code`, `refresh_token` |
| `response_types` | `code` |
| `redirect_uris` | Fully-qualified callback URL |
| `dpop_bound_access_tokens` | *"must be `true` (DPoP is mandatory)"* |
| `token_endpoint_auth_method` | `private_key_jwt` for a confidential client |
| `jwks` / `jwks_uri` | Public keys, confidential clients only |

`transition:generic` is mentioned as an additional scope. **Unverified:** which scope this game
actually needs. We want authentication only and no account authorization — §3 of
`02-identity-and-auth.md` is explicit that we must *"never request write scope"* — so the minimum
viable scope set needs establishing rather than copying from an example.

### 3.2 PAR — Pushed Authorization Requests

*"Pushed Authorization Requests (PAR) are required for all client types."* Parameters are POSTed to
the PAR endpoint; the response is a `request_uri` used in the authorization redirect.

### 3.3 DPoP

- Mandatory. Binds tokens to a specific client device or server.
- **ES256 (NIST P-256).** *"the cryptographic algorithm/curve which must be supported"*.
- **A new keypair per auth session** — not per client, per *session*.
- A DPoP header on **every** token request and every PDS request.
- Keypairs *"should never be exported or moved between devices"*.

### 3.4 PKCE

`S256` method. A random verifier at session start, its derived challenge on the authorization
request.

### 3.5 Token refresh

*"Long-lived clients will need to manage access token lifetimes and periodic refresh token
requests"*, and must handle **concurrent** requests without duplicating refresh attempts. That is a
single-flight lock around refresh, not a nice-to-have — duplicate refreshes can invalidate each
other.

---

## 4. Security requirements

The ones with real consequences for *this* game specifically.

### 4.1 ⚠ Bidirectional handle verification — the one that must not be skipped

The guide: *"it is critical (mandatory) to bidirectionally verify the handle by checking that the DID
document claims the handle."*

A DID document's `alsoKnownAs` is written by the DID controller. It is a **self-asserted claim**.
Anyone can put `at://<a-rival's-handle>` in their own document. To trust a handle as a display name:

1. Resolve the DID → document (`did:plc` via the PLC directory; `did:web` via
   `/.well-known/did.json`). **Unverified:** exact endpoints.
2. Read the claimed handle from `alsoKnownAs`.
3. Resolve **that handle independently** — DNS `TXT` at `_atproto.<handle>`, or
   `GET https://<handle>/.well-known/atproto-did`. **Unverified:** exact record format.
4. Confirm it returns **the same DID**. If not, the handle is invalid and must not be displayed as
   though it were verified. (Bluesky's own clients render this case as `handle.invalid`.)

**Why this matters more here than in a social app.** `../design/12-identity-and-social.md` has
informants, compiled dossiers, an evidence threshold and a mass-vote override, and
`../design/01-core-resources.md` §2.2 makes the public ledger *"a gameplay feature… it gives
investigators — player and NPC — something to work with"*. A forged display name on any of those
surfaces is not cosmetic; it is an attack on the mechanic.

**The rule to encode:** the DID is what everything is keyed on; the handle is a **cache with a
verified flag**; an unverified handle is never drawn as if it were verified. Handles are also
re-claimable after release, which is the second reason to refresh on every sign-in and never key
anything on one.

### 4.2 Verify the `sub`

*"It is **critical** for the client to verify that the `sub` DID matches the account expected."*

### 4.3 SSRF

This would be the **first outbound HTTP the server makes**, and the URLs are derived from
user-supplied handles and from DID documents. A hardened HTTP client with timeouts, resource limits,
redirect limits and an address-range denylist is required, not advisable. The guide calls for
*"a hardened HTTP client"* and explicit SSRF prevention.

### 4.4 Token storage

*"Access and refresh tokens should never be copied or shared across end devices."* ⚠ In particular
they must never reach `ClientProfile`, whose own comment already says: *"No credentials and no tokens
are ever written here — the profile is a plain unencrypted JSON file in a conventional location."*

---

## 5. ⚠ Three places the existing documents are wrong or silent

Found while reading the guide against our own docs. Each needs fixing regardless of which option §1
picks.

1. **`02-identity-and-auth.md` §5 claims one crypto stack.** It says: *"AT Proto DIDs use Ed25519
   keys — which is also what the provenance layer signs with (`04`). One crypto stack for both
   identity and provenance; no second key system."*
   DPoP mandates **ES256 / P-256**, so there **is** a second curve whatever the provenance layer
   uses. That sentence is wrong as written.
   ⚠ **Unverified and worth checking separately:** whether atproto signing keys are Ed25519 at all.
   The claim was inherited from a technology chat and has not been checked against the spec. If it is
   also wrong, §5 has two errors, and the "no second key system" argument that partly justified
   choosing AT Proto needs restating.

2. **`deploy/` is silent on the public-URL requirement.** If §1 lands on Option A, every self-hoster
   needs a domain and a certificate, and the Docker Compose story has to say so.

3. **`../design/15-open-questions.md` W-6 understates the work.** It reads as "a production provider
   is missing". The provider is one of six things (metadata document, PAR, DPoP, PKCE, refresh,
   resolution), and W-1 is a prerequisite for none of them individually but shares the network client
   with all of them.

---

## 6. What lands where in the code

Nothing here needs the port or any view to change.

| Piece | Where | Notes |
|---|---|---|
| Real provider | `server/identity/AtProtoIdentityProvider` | The interface already exists and is the documented seam. `DevAtProtoIdentityProvider` stays as the disabled-by-default fallback |
| Handle resolver | new, `server/identity/` | Bidirectional (§4.1). Its own interface + a `@ConditionalOnMissingBean` no-op default, matching how `DidPublicKeyResolver` is already shaped |
| DID document resolver | new, shared | **W-1 needs the same one** for provenance keys. Build once, with a TTL cache |
| Hardened HTTP client | new, shared | §4.3. One place, so the SSRF rules are written once |
| DPoP / `private_key_jwt` | new | ⚠ Use a reviewed JOSE library (e.g. nimbus-jose-jwt). `07-transport-security.md` §6 **T-1** already flags that this repo has one hand-rolled crypto protocol awaiting review; a second would be worse |
| Handle refresh on sign-in | `SignInService` | Already shaped for it — `ResolvedIdentity` carries the handle and `PlayerRepository` has the column |

---

## 7. Suggested order

1. **Fix the wrong-handle bug** (§2). Small, no network, no decision needed.
2. **Add the `HandleResolver` seam** with a no-op default and the bidirectional contract written into
   its comment. Makes §4.1 reviewable before any network code exists.
3. **Make the §1 decision.** Everything after this is sized by it.
4. **Hardened HTTP client + DID document resolver**, with the TTL cache. Closes half of **W-1** at
   the same time.
5. **Bidirectional handle resolution** on top of it.
6. **The OAuth flow itself** — metadata document, PAR, PKCE, DPoP, refresh.
7. **The transport** (**CL-8**), or before step 6 if we want a client that can actually sign in.

---

## 8. Cross-references

- What AT Proto identity is *for*, and why it was chosen: [`02-identity-and-auth.md`](02-identity-and-auth.md)
- DID→key resolution for provenance (**W-1**, shares the network client): [`04-item-provenance.md`](04-item-provenance.md)
- The hand-rolled-crypto warning (**T-1**): [`07-transport-security.md`](07-transport-security.md) §6
- The stubs register (**W-1**, **W-6**) and **CL-8**: [`../design/15-open-questions.md`](../design/15-open-questions.md)
- Why a forged handle is a mechanic-level problem, not a cosmetic one: [`../design/12-identity-and-social.md`](../design/12-identity-and-social.md)

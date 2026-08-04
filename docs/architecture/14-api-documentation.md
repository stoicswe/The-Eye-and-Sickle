# 14. API documentation — the OpenAPI spec

> **Status: Established (2026-08-04).** The spec is generated, checked on every `-Pit` run, and
> served only when an operator asks for it. What is **not** settled is the HTTP security policy it
> documents — see §4, which records a live defect found while building this.

---

## 1. What exists

[springdoc-openapi](https://springdoc.org) 3.1.0 generates an **OpenAPI 3.1** document from the
server's controllers and wire types. Metadata — title, version, tags, security schemes — comes from
`server/web/OpenApiConfiguration`.

| | |
|---|---|
| Spec | `GET /v3/api-docs` |
| Console | `GET /swagger-ui.html` |
| Build output | `server/target/openapi.json` |

⚠ **springdoc 3.x, not 2.x.** 2.x is built against Spring Boot 3; 3.1.0 is built against Boot 4.1.0,
which is what this project uses. A 2.x on Boot 4 resolves cleanly and fails at runtime, which is the
worst place to discover it.

## 2. Both surfaces ship OFF

`application.yml` defaults `springdoc.api-docs.enabled` and `springdoc.swagger-ui.enabled` to
`false`. An operator turns them on for their own machine:

```
EYEANDSICKLE_API_DOCS=true      # the spec
EYEANDSICKLE_SWAGGER_UI=true    # the console
```

⚠ **This follows the server's existing posture rather than inventing one.** A home server is closed
by default — the allowlist starts empty (`03` §1) and LAN mode refuses to bind a public address
(`12` §2). Shipping an interactive request builder that answers to whoever can reach the port would
sit badly beside that.

⚠ **Two switches, not one, and the split is the point.** The spec is a static document naming paths
and shapes; the console issues live requests. An operator who wants to hand a client developer the
schema should not have to expose a request builder to do it.

## 3. The spec is checked, not merely produced

`OpenApiSpecIT` boots the context with docs enabled and asserts the document matches the API **in
both directions**:

- every path Spring maps appears in the spec — a controller added without the spec growing leaves the
  document confidently wrong, and nothing else would say so;
- every path the spec names is really mapped — a spec promising an endpoint the server does not serve
  sends a client developer to build against nothing.

⚠ **Mapped paths are read from `RequestMappingHandlerMapping`, never a hand-kept list.** A list
somebody has to remember to update documents the API as it was when they last remembered.

⚠ **Verified by hiding a controller**: `@Hidden` on `LanJoinController` fails the check with
`/api/lan/join is mapped but absent from the spec`. A coverage test that passes both ways reports
drift as fine.

⚠ **The test enables docs for itself**, which is what lets the shipped default stay closed *and* the
spec stay checked. A suite that could only verify the docs by shipping them enabled would have made
that a false choice.

⚠ **`@ActiveProfiles("federation")`, not `registry.add("spring.profiles.active", …)`.** Profiles
resolve while the `Environment` is being prepared, *before* `@DynamicPropertySource` contributes
anything, so the registry form is accepted, ignored, and reported only as `No active profile set` in
a log nobody reads in a passing test. The federation controllers would then be absent and the
coverage check would pass over a spec missing an entire tag. ⚠ **`ServerContextLoadsIT` has the same
bug** and has never actually exercised the federation profile it claims to.

## 4. ⚠ What this uncovered: the REST API answers 401 to everything

`spring-boot-starter-security` has been on the classpath since the identity slice landed, and **no
`SecurityFilterChain` bean existed anywhere**. Boot's default therefore applied —
`anyRequest().authenticated()` — so **every endpoint on this server returns 401**: sign-in, LAN join,
the session transport, the whole federation surface.

⚠ **Nothing noticed because no test in the server module has ever made an HTTP request to one of its
own controllers.** Every integration test is repository- or service-level; the only `RestClient` in
the test tree is an *outbound* peer transport. The 401 surfaced the first time anything fetched a URL
from this server, which was `OpenApiSpecIT`.

`server/web/ApiDocsSecurityConfiguration` carves out the documentation paths and **preserves the
existing behaviour for everything else**, deliberately. Deciding which endpoints are public, which
need an allowlisted account and which need a verified service-auth JWT is the application's
authentication model, and settling it as a side effect of adding documentation — in the permissive
direction — would open a server meant to be closed.

⚠ **Note the shape of the real answer.** This server does not authenticate with HTTP Basic or a form
login at all: `identity/ServiceAuthVerifier` verifies an AT Protocol service-auth JWT against a DID
document the server resolves itself (`10` §1). Boot's default is not a weak version of that — it is
an unrelated mechanism that happens to be refusing everybody. The policy belongs with **CL-8**
(`../design/15`), where the transport's identity leg lives.

## 5. Open

- **API-1 — the security policy above.** Blocking for any real client, and not a documentation task.
- **API-2 — per-endpoint prose.** Tags and schemas are generated; `@Operation` descriptions and the
  meaning of each refusal code are not written yet. The controllers' javadoc already says most of it,
  and moving it into annotations is mechanical.
- **API-3 — publishing.** The spec is a build artifact. Whether a release should attach it (like the
  client jars) or a committed copy should make API changes visible in review is undecided.

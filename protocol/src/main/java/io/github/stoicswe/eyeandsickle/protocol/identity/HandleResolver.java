package io.github.stoicswe.eyeandsickle.protocol.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Resolves handles to DIDs and back, <strong>bidirectionally</strong>.
 *
 * <h2>The check this type exists to make impossible to forget</h2>
 *
 * A DID document's {@code alsoKnownAs} is written by the DID's controller, so a handle claim in it is
 * a claim about something the controller does not own. Anyone may write {@code at://a-rivals.handle}
 * into their own document. The handle spec is explicit that handles "should not be trusted or
 * considered valid until the DID is also resolved and the current DID document is confirmed to link
 * back to the handle".
 *
 * <p><strong>Why this matters more here than in a social app.</strong>
 * {@code docs/design/12-identity-and-social.md} has informants, compiled dossiers, an evidence
 * threshold and a mass-vote override, and {@code docs/design/01-core-resources.md} §2.2 makes the
 * public ledger a gameplay feature that gives investigators "something to work with". A forged
 * display name on any of those surfaces is not cosmetic — it is an attack on the mechanic. So this
 * class has <strong>no method that returns an unverified handle</strong>. The one-way lookups are
 * private on purpose; a caller cannot reach for the cheap answer because there is no cheap answer to
 * reach for.
 *
 * <h2>The two methods, and which wins</h2>
 *
 * <ol>
 *   <li><strong>DNS:</strong> {@code TXT} at {@code _atproto.<handle>}, value {@code did=did:plc:…}.
 *       ⚠ Values not starting with {@code did=} are <em>ignored, not failed</em> — other records
 *       legitimately share that name.
 *   <li><strong>HTTPS:</strong> {@code GET https://<handle>/.well-known/atproto-did}, returning the
 *       bare DID as {@code text/plain}.
 * </ol>
 *
 * ⚠ <strong>On conflict the DNS answer wins</strong>, per the spec. This class therefore tries DNS
 * first and only falls through to HTTPS when DNS says nothing at all — which is the same outcome and
 * one fewer request.
 */
public final class HandleResolver {

    private final HttpFetcher http;
    private final DidResolver dids;
    private final TxtLookup txt;

    public HandleResolver() {
        this(new HardenedHttpClient(), new DidResolver(), TxtLookup.system());
    }

    public HandleResolver(HttpFetcher http, DidResolver dids, TxtLookup txt) {
        this.http = Objects.requireNonNull(http, "http");
        this.dids = Objects.requireNonNull(dids, "dids");
        this.txt = Objects.requireNonNull(txt, "txt");
    }

    /**
     * A handle and the DID it was confirmed to belong to, in both directions.
     *
     * <p>There is no {@code verified} flag: an instance of this type <em>is</em> the verification.
     * A boolean would be a field somebody can read past.
     *
     * @param handle the normalised handle
     * @param did the DID it resolves to, whose document claims it back
     */
    public record VerifiedHandle(String handle, String did) {}

    /**
     * Resolves a handle the player typed, and confirms the resulting DID claims it back.
     *
     * @param rawHandle the handle as typed
     * @return the verified pairing
     * @throws IdentityResolutionException {@code NOT_FOUND} if neither method resolves the handle;
     *     {@code INVALID} if the DID document does not claim the handle back — which is what an
     *     impersonation attempt looks like from here, not a missing record
     */
    public VerifiedHandle resolve(String rawHandle) {
        String handle = normalise(rawHandle);
        String did = lookup(handle);
        if (did == null) {
            throw new IdentityResolutionException(Kind.NOT_FOUND, "no atproto identity for handle '" + handle + "'");
        }
        DidDocument document = dids.resolve(did);
        if (!document.claimsHandle(handle)) {
            throw new IdentityResolutionException(
                    Kind.INVALID,
                    "handle '" + handle + "' resolves to " + did + ", but that DID document does not claim it"
                            + " (claims: " + document.claimedHandles() + ")");
        }
        return new VerifiedHandle(handle, did);
    }

    /**
     * Finds the verified handle for a DID that is already authenticated.
     *
     * <p>This is the sign-in direction: OAuth produced a {@code sub}, and the player wants a name on
     * screen rather than {@code did:plc:ewvi7nxzyoun6zhxrhs64oiz}. Every claim in the document is
     * checked by resolving it independently and comparing back.
     *
     * <p>⚠ Returns {@code null} rather than throwing when nothing verifies. An account whose handle
     * has lapsed is <em>not</em> a failed sign-in — Bluesky's own clients render this as
     * {@code handle.invalid} — and refusing to sign someone in because their DNS is briefly wrong
     * would be a far worse failure than showing them their DID.
     *
     * @param did an authenticated DID
     * @return the verified handle, or null if none of its claims check out
     */
    public String verifiedHandleFor(String did) {
        DidDocument document = dids.resolve(Objects.requireNonNull(did, "did"));
        for (String claimed : document.claimedHandles()) {
            try {
                if (did.equals(lookup(claimed))) {
                    return claimed;
                }
            } catch (IdentityResolutionException unresolvable) {
                // One bad claim must not hide a good one further down the list. A document may
                // legitimately carry a stale handle alongside a current one.
                continue;
            }
        }
        return null;
    }

    /**
     * One-way lookup: handle to whatever DID it names. DNS first, then HTTPS.
     *
     * <p>Private, and that is the design. Everything this class exposes has been checked in both
     * directions; there is deliberately no way to obtain the unverified answer.
     *
     * @return the DID, or null if neither method answers
     */
    private String lookup(String handle) {
        List<String> records = txt.txt("_atproto." + handle);
        for (String record : records) {
            if (record != null && record.startsWith("did=")) {
                String did = record.substring("did=".length()).trim();
                if (!did.isEmpty()) {
                    // DNS wins on conflict, so the first valid did= answer ends the search — the
                    // HTTPS method is never consulted to second-guess it.
                    return did;
                }
            }
        }
        return overHttps(handle);
    }

    private String overHttps(String handle) {
        HttpFetcher.Response response;
        try {
            response = http.get(URI.create("https://" + handle + "/.well-known/atproto-did"), "text/plain");
        } catch (IdentityResolutionException unreachable) {
            if (unreachable.kind() == Kind.NOT_FOUND || unreachable.kind() == Kind.REFUSED_BY_POLICY) {
                // No such host, or a host we will not talk to. Either way this method has no answer;
                // that is not the same as the handle being invalid.
                return null;
            }
            throw unreachable;
        }
        if (response.status() == 404 || response.status() == 410) {
            return null;
        }
        if (!response.isSuccess()) {
            throw new IdentityResolutionException(
                    Kind.UNAVAILABLE, "resolving handle '" + handle + "' returned HTTP " + response.status());
        }
        String did = response.body().trim();
        if (!did.startsWith("did:")) {
            // The endpoint returns a BARE DID with no prefix or wrapper. Anything else is a web
            // server answering /.well-known/* with a catch-all page, which is common and must not be
            // mistaken for an identity.
            return null;
        }
        return did;
    }

    /**
     * Normalises a handle for comparison.
     *
     * <p>Handles are case-insensitive, and a trailing dot is a legal fully-qualified name that the
     * DID document will not carry. Normalising once, here, is what stops a document claiming
     * {@code Alice.Example} from reading as impersonation of {@code alice.example}.
     *
     * @param rawHandle the handle as typed
     * @return the normalised handle
     * @throws IdentityResolutionException if it is not shaped like a handle at all
     */
    public static String normalise(String rawHandle) {
        if (rawHandle == null) {
            throw new IdentityResolutionException(Kind.INVALID, "no handle");
        }
        String handle = rawHandle.trim().toLowerCase(Locale.ROOT);
        if (handle.startsWith("@")) {
            // Players type the social-media form; it is not part of the handle.
            handle = handle.substring(1);
        }
        while (handle.endsWith(".")) {
            handle = handle.substring(0, handle.length() - 1);
        }
        if (handle.isEmpty() || !handle.contains(".")) {
            throw new IdentityResolutionException(
                    Kind.INVALID, "a handle is a domain name and must contain a dot: '" + rawHandle + "'");
        }
        if (handle.contains("/") || handle.contains(":") || handle.contains(" ") || handle.contains("@")) {
            throw new IdentityResolutionException(Kind.INVALID, "not a valid handle: '" + rawHandle + "'");
        }
        return handle;
    }
}

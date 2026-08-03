package io.github.stoicswe.eyeandsickle.protocol.identity;

import io.github.stoicswe.eyeandsickle.protocol.identity.IdentityResolutionException.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A resolved DID document — the three things atproto puts in one
 * (<a href="https://atproto.com/specs/did">DID spec</a>, read 2026-08-02).
 *
 * <h2>⚠ Everything in here is SELF-ASSERTED</h2>
 *
 * A DID document is written by whoever controls the DID. That is the entire security model, and it
 * cuts both ways: {@link #verificationMethods()} is trustworthy because controlling the DID is what
 * being that identity <em>means</em>, but {@link #alsoKnownAs()} is a claim about something the
 * controller does <strong>not</strong> own — a handle in the DNS. Anyone may write
 * {@code at://a-rivals.handle} into their own document.
 *
 * <p>So {@link #claimsHandle} is named for what it does. It answers "does this document claim that
 * handle", never "is that handle theirs". The second question needs the handle resolved
 * independently and compared back, which is {@link HandleResolver}'s job and is the one step
 * {@code docs/architecture/10-oauth-and-did-resolution.md} §4.1 says must not be skipped.
 *
 * @param id the DID this document describes
 * @param alsoKnownAs handle claims, as {@code at://} URIs, verbatim and unverified
 * @param verificationMethods signing keys
 * @param services declared endpoints, including the PDS
 */
public record DidDocument(
        String id,
        List<String> alsoKnownAs,
        List<VerificationMethod> verificationMethods,
        List<ServiceEndpoint> services) {

    /**
     * The fragment atproto puts the account's signing key under.
     *
     * <p>⚠ A verifier must look for <em>this</em> key and not simply take the first method in the
     * list, or the token names the key that verifies it and a document with two keys can pick
     * whichever one it has a signature for ({@code 10} §5.1).
     */
    public static final String ATPROTO_KEY_FRAGMENT = "#atproto";

    /** The service id atproto puts the personal data server under. */
    public static final String ATPROTO_PDS_FRAGMENT = "#atproto_pds";

    private static final JsonMapper MAPPER = JsonMapper.builder().build();

    public DidDocument {
        Objects.requireNonNull(id, "id");
        alsoKnownAs = List.copyOf(alsoKnownAs);
        verificationMethods = List.copyOf(verificationMethods);
        services = List.copyOf(services);
    }

    /**
     * A signing key.
     *
     * @param id the full method id, e.g. {@code did:plc:abc#atproto}
     * @param type the method type — modern documents use {@code Multikey}
     * @param controller the DID that controls this key
     * @param publicKeyMultibase the multibase-encoded key
     */
    public record VerificationMethod(String id, String type, String controller, String publicKeyMultibase) {}

    /**
     * A declared endpoint.
     *
     * <p>⚠ Named {@code ServiceEndpoint} and not {@code Service}, which is what the DID document
     * calls it: {@code ArchitectureRulesTest} refuses any type here whose name ends in {@code Service},
     * to stop a {@code GateEvaluationService} arriving in a hurry on a Friday. That rule is a blunt
     * name check on purpose, and renaming one record is a much smaller cost than carving the first
     * exception into it.
     *
     * @param id the fragment, e.g. {@code #atproto_pds}
     * @param type the service type
     * @param serviceEndpoint the URL
     */
    public record ServiceEndpoint(String id, String type, String serviceEndpoint) {}

    /**
     * Parses a DID document and confirms it describes the DID that was asked for.
     *
     * <p>⚠ The {@code expectedDid} check is not a formality. Without it a directory — or anything
     * that can answer for one — may return a document for a <em>different</em> identity, and every
     * key and endpoint in it would be adopted for the DID the caller meant. It is the same class of
     * check as verifying the OAuth {@code sub} ({@code 10} §4.5).
     *
     * @param json the document
     * @param expectedDid the DID that was resolved
     * @return the parsed document
     * @throws IdentityResolutionException if the JSON is malformed or describes a different DID
     */
    public static DidDocument parse(String json, String expectedDid) {
        JsonNode root;
        try {
            root = MAPPER.readTree(json);
        } catch (JacksonException malformed) {
            throw new IdentityResolutionException(
                    Kind.INVALID, "DID document for " + expectedDid + " is not valid JSON", malformed);
        }
        if (root == null || !root.isObject()) {
            throw new IdentityResolutionException(
                    Kind.INVALID, "DID document for " + expectedDid + " is not an object");
        }
        String id = text(root, "id");
        if (id == null) {
            throw new IdentityResolutionException(Kind.INVALID, "DID document for " + expectedDid + " has no 'id'");
        }
        if (!id.equals(expectedDid)) {
            throw new IdentityResolutionException(
                    Kind.INVALID, "DID document claims to be '" + id + "' but was resolved for '" + expectedDid + "'");
        }

        List<String> akas = new ArrayList<>();
        JsonNode akaNode = root.get("alsoKnownAs");
        if (akaNode != null && akaNode.isArray()) {
            akaNode.forEach(node -> {
                if (node.isString()) {
                    akas.add(node.stringValue());
                }
            });
        }

        List<VerificationMethod> methods = new ArrayList<>();
        JsonNode methodNode = root.get("verificationMethod");
        if (methodNode != null && methodNode.isArray()) {
            methodNode.forEach(node -> {
                if (node.isObject()) {
                    methods.add(new VerificationMethod(
                            text(node, "id"),
                            text(node, "type"),
                            text(node, "controller"),
                            text(node, "publicKeyMultibase")));
                }
            });
        }

        List<ServiceEndpoint> services = new ArrayList<>();
        JsonNode serviceNode = root.get("service");
        if (serviceNode != null && serviceNode.isArray()) {
            serviceNode.forEach(node -> {
                if (node.isObject()) {
                    services.add(
                            new ServiceEndpoint(text(node, "id"), text(node, "type"), text(node, "serviceEndpoint")));
                }
            });
        }
        return new DidDocument(id, akas, methods, services);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value != null && value.isString() ? value.stringValue() : null;
    }

    /**
     * The handles this document claims, with the {@code at://} prefix stripped and lowercased.
     *
     * <p>⚠ Unverified by construction — see the class note. Lowercased because handles are
     * case-insensitive and a comparison that is not would fail on a document that spelled one with a
     * capital, which reads to the player as impersonation rather than as a formatting difference.
     *
     * @return the claimed handles, possibly empty
     */
    public List<String> claimedHandles() {
        return alsoKnownAs.stream()
                .filter(aka -> aka != null && aka.startsWith("at://"))
                .map(aka -> aka.substring("at://".length()).toLowerCase(Locale.ROOT))
                .toList();
    }

    /**
     * @param handle the handle to look for
     * @return whether this document claims that handle — <strong>not</strong> whether it is theirs
     */
    public boolean claimsHandle(String handle) {
        return handle != null && claimedHandles().contains(handle.toLowerCase(Locale.ROOT));
    }

    /**
     * The account's atproto signing key.
     *
     * @return the {@code #atproto} verification method, or null if the document declares none
     */
    public VerificationMethod atprotoSigningKey() {
        return verificationMethods.stream()
                .filter(method -> method.id() != null && method.id().endsWith(ATPROTO_KEY_FRAGMENT))
                .findFirst()
                .orElse(null);
    }

    /**
     * The account's personal data server.
     *
     * @return the {@code #atproto_pds} service endpoint, or null if the document declares none
     */
    public String pdsEndpoint() {
        return services.stream()
                .filter(service -> service.id() != null && service.id().endsWith(ATPROTO_PDS_FRAGMENT))
                .map(ServiceEndpoint::serviceEndpoint)
                .filter(Objects::nonNull)
                .findFirst()
                .orElse(null);
    }
}

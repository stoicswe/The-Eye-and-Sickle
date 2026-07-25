package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A Decentralized Identifier — the portable, stable player (and server) identity
 * ({@code docs/architecture/02-identity-and-auth.md} §1).
 *
 * <h2>Why a value object and not a bare {@code String}</h2>
 *
 * A DID flows through the allowlist, the player record, provenance holders and the ledger, and at each
 * of those a raw string is one typo away from a lookup that silently matches nothing. Wrapping it makes
 * "this is a DID, shaped like one" a fact established once, at the edge, instead of an assumption made
 * everywhere. Every constructor validates the same shape the database enforces, so a value that would
 * be rejected by the {@code is_did} CHECK cannot be constructed here and reach an INSERT.
 *
 * <h2>Shape, not authentication</h2>
 *
 * This checks the <em>syntax</em> of a DID. It does <strong>not</strong> prove the DID resolves to a
 * key or that the caller controls it — that is the job of the AT Proto OAuth handshake at sign-in
 * ({@link AtProtoIdentityProvider}), and no regex can stand in for it. A well-formed DID from an
 * unauthenticated source is still an unproven claim; treat it as one until the identity provider has
 * verified it.
 *
 * <h2>The pattern mirrors the schema deliberately</h2>
 *
 * The regex is the same one {@code db/migration/core/V2__core_schema.sql}'s {@code is_did(text)}
 * function applies, and the 512-character bound matches it too. Keeping them identical means a DID that
 * passes here always passes the database, so a shape mismatch surfaces as a validation error at the API
 * edge with a useful message rather than as a constraint violation three layers down. If the schema's
 * rule ever changes, this must change with it — {@code DidTest} pins the shared shape so the two cannot
 * drift silently.
 *
 * @param value the DID string, already validated to be well-shaped
 */
public record Did(String value) {

    /**
     * The DID shape. Identical to {@code is_did} in the core migration: a lowercase method segment and
     * a method-specific identifier. Not anchored to a fixed method (e.g. {@code did:plc}) because AT
     * Proto also uses {@code did:web}, and pinning the method here would reject a legitimate identity.
     */
    private static final Pattern SHAPE = Pattern.compile("^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$");

    /** Matches {@code length(value) <= 512} in the schema; a DoS bound, not a semantic one. */
    static final int MAX_LENGTH = 512;

    public Did {
        Objects.requireNonNull(value, "value");
        if (value.length() > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "DID is " + value.length() + " characters, over the " + MAX_LENGTH + "-character bound");
        }
        if (!SHAPE.matcher(value).matches()) {
            throw new IllegalArgumentException("Not a well-shaped DID: '" + value + "'. Expected did:<method>:<id> "
                    + "(docs/architecture/02-identity-and-auth.md §1).");
        }
    }

    /**
     * Parses a required DID.
     *
     * @param value the DID string
     * @return the value object
     * @throws IllegalArgumentException if the string is not a well-shaped DID
     */
    public static Did of(String value) {
        return new Did(value);
    }

    /**
     * Parses an optional DID.
     *
     * <p>Exists because {@code players.did} is nullable for local-only solo play
     * ({@code docs/architecture/02-identity-and-auth.md} §4), so a null read back from that column is
     * legitimate and must not be forced into an exception.
     *
     * @param value the DID string, or {@code null}
     * @return the value object, or {@code null} if the input was {@code null}
     * @throws IllegalArgumentException if a non-null string is not a well-shaped DID
     */
    public static Did ofNullable(String value) {
        return value == null ? null : new Did(value);
    }

    /**
     * @return the DID string, so a {@code Did} logs and concatenates as its plain value rather than as
     *     {@code Did[value=...]}
     */
    @Override
    public String toString() {
        return value;
    }
}

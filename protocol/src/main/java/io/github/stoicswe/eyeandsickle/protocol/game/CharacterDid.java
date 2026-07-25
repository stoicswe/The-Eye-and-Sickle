package io.github.stoicswe.eyeandsickle.protocol.game;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A character's derived, portable identity — the ownership/counterparty id that per-character game state
 * keys on ({@code docs/architecture/09-player-state-portability.md} §9, Q-item-keying, option 3).
 *
 * <h2>Why a character needs its own DID</h2>
 *
 * A DID is now an <em>account</em> that may hold several <em>characters</em> (09 §1). Balance and heat
 * already live on the character row, but items, the ledger and deployed miners key on the account DID and
 * would therefore be <em>shared</em> across an account's characters — the bug 09 §9 fixes. The resolution
 * is to give each character a stable sub-identity, used everywhere game state currently keyed on the raw
 * account DID, while the account DID keeps doing auth and the character directory.
 *
 * <h2>The format, exactly</h2>
 *
 * <pre>did:eyeandsickle:&lt;slot&gt;:&lt;accountDid&gt;</pre>
 *
 * e.g. account {@code did:plc:abcd1234} in slot {@code 2} yields {@code did:eyeandsickle:2:did:plc:abcd1234}.
 * The <strong>slot comes first</strong> so parsing is unambiguous even though the account DID itself
 * contains colons: after the {@code did:eyeandsickle:} prefix, the segment up to the first {@code ':'} is
 * the integer slot, and <em>everything</em> after that first colon is the account DID verbatim.
 *
 * <h2>Four properties this identity was shaped for</h2>
 *
 * <ul>
 *   <li><strong>It is DID-shaped.</strong> The whole string passes the server's {@code is_did()} check
 *       (method {@code eyeandsickle}; the id part {@code <slot>:<accountDid>} is drawn from the same
 *       character class {@code is_did} allows, which includes {@code ':'}). So the {@code *_did} columns
 *       accept it as-is and no schema or constraint change is needed.
 *   <li><strong>It is stable across migration.</strong> Migration preserves {@code (account, slot)}, so an
 *       item's holder character DID survives a home change even though the local character-row id is
 *       freshly minted at the new home. That is why the holder is this derived DID and not the row UUID.
 *   <li><strong>It is a game-internal method.</strong> {@code did:eyeandsickle} does not resolve over AT
 *       Proto and is <em>never</em> a signing key — provenance still verifies the <em>issuer's</em> Ed25519
 *       signature ({@code docs/architecture/04-item-provenance.md}); a character DID is only recorded as
 *       the holder/counterparty. So keying on it does not weaken provenance verification.
 *   <li><strong>It requires an account.</strong> A local, DID-less character (09 §1) has no account DID and
 *       is exempt from the federated economy entirely; it has no character DID. Callers holding a local
 *       character must exclude it rather than fabricate one — {@code of} refuses a blank/non-DID account.
 * </ul>
 *
 * <h2>Vocabulary, not rule (Invariant I14)</h2>
 *
 * This is a pure string format with one structural invariant (a slot is {@code >= 1}); it encodes no
 * threshold, price or gate and reads no clock or randomness, so it belongs in the game vocabulary package.
 * If this constant format changed, no player would <em>gain</em> anything — it is a naming scheme, not a
 * balance value. Provenance keeps its holder as a plain {@code String}; it is the <em>server</em> that
 * produces the character-DID string, so {@code ProvenancePayload} does not (and must not) depend on this.
 *
 * @param accountDid the account (AT Proto) DID this character belongs to; non-blank and DID-shaped
 * @param slot the character's save slot within the account; {@code 1} or greater
 */
public record CharacterDid(String accountDid, int slot) {

    /** The game-internal DID method. Not resolvable over AT Proto; never a signing key. */
    public static final String METHOD = "eyeandsickle";

    /** The literal prefix every character DID starts with: {@code did:eyeandsickle:}. */
    public static final String PREFIX = "did:" + METHOD + ":";

    /** The lowest slot number. A slot is a positive index; {@code 0} or negative is not a slot. */
    public static final int MIN_SLOT = 1;

    /**
     * Upper bound on the full character-DID string, matching {@code length(value) <= 512} in the schema's
     * {@code is_did}. A character DID is stored in a {@code *_did} column, so if the full value exceeded
     * this it would be rejected at INSERT; catching it here keeps the failure a construction error with a
     * clear message rather than a constraint violation layers down. A DoS bound, not a semantic one.
     */
    public static final int MAX_LENGTH = 512;

    /**
     * The shape an account DID must have. Identical to the server's {@code Did} pattern and the schema's
     * {@code is_did}: a lowercase method segment and a method-specific identifier. Kept here as a local
     * copy rather than importing the server type, because {@code protocol} must not depend on
     * {@code server}; {@code CharacterDidTest} pins the shape so the two cannot drift silently.
     */
    private static final Pattern ACCOUNT_DID_SHAPE = Pattern.compile("^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$");

    public CharacterDid {
        if (accountDid == null || accountDid.isBlank()) {
            throw new IllegalArgumentException("accountDid must be a non-blank DID, was: " + accountDid);
        }
        if (!ACCOUNT_DID_SHAPE.matcher(accountDid).matches()) {
            throw new IllegalArgumentException("accountDid is not a well-shaped DID: '" + accountDid
                    + "'. Expected did:<method>:<id> (docs/architecture/02-identity-and-auth.md §1).");
        }
        if (slot < MIN_SLOT) {
            throw new IllegalArgumentException("slot is a positive index (>= " + MIN_SLOT + "), was " + slot);
        }
        int length = PREFIX.length() + Integer.toString(slot).length() + 1 + accountDid.length();
        if (length > MAX_LENGTH) {
            throw new IllegalArgumentException(
                    "character DID is " + length + " characters, over the " + MAX_LENGTH + "-character bound");
        }
    }

    /**
     * Builds the character-DID <em>string</em> for an account and slot.
     *
     * <p>Returns the string (not the record) because that is the value the server stamps into the
     * {@code *_did} columns — {@code items.holder_did}, the ledger's {@code from_did}/{@code to_did}, a
     * future miner's {@code deployer_did} — where a plain {@code String} is stored. Callers that want the
     * structured form parse it back with {@link #parse(String)} or {@link #from(String)}, or construct the
     * record directly.
     *
     * @param accountDid the account (AT Proto) DID; non-blank and DID-shaped
     * @param slot the save slot within the account; {@code 1} or greater
     * @return the canonical {@code did:eyeandsickle:<slot>:<accountDid>} string
     * @throws IllegalArgumentException if {@code accountDid} is blank or not DID-shaped, or {@code slot < 1}
     */
    public static String of(String accountDid, int slot) {
        return new CharacterDid(accountDid, slot).value();
    }

    /**
     * Parses a character-DID string into its {@code (accountDid, slot)} parts, if it is one.
     *
     * <p>Round-trips with {@link #of}: {@code parse(of(account, slot))} reconstructs {@code (account, slot)}
     * for every valid pair, and {@code of(parse(v).accountDid(), parse(v).slot())} reconstructs {@code v}
     * for every canonical {@code v}. The slot must be canonical — no leading zeros, sign or whitespace — so
     * the round-trip is exact in both directions.
     *
     * @param value a candidate character-DID string, or {@code null}
     * @return the parsed identity, or empty if {@code value} is null or not a well-formed character DID
     */
    public static Optional<CharacterDid> parse(String value) {
        if (value == null || !value.startsWith(PREFIX)) {
            return Optional.empty();
        }
        String rest = value.substring(PREFIX.length());
        int separator = rest.indexOf(':');
        // Need at least one digit before the separator, and a non-empty account DID after it.
        if (separator <= 0 || separator == rest.length() - 1) {
            return Optional.empty();
        }
        String slotDigits = rest.substring(0, separator);
        String accountDid = rest.substring(separator + 1);
        if (!isCanonicalPositiveInt(slotDigits)) {
            return Optional.empty();
        }
        int slot;
        try {
            slot = Integer.parseInt(slotDigits);
        } catch (NumberFormatException overflow) {
            // A digit run too long for an int is not a slot we can hold.
            return Optional.empty();
        }
        try {
            return Optional.of(new CharacterDid(accountDid, slot));
        } catch (IllegalArgumentException malformed) {
            // The account DID was blank or not DID-shaped, or slot < 1 — not a valid character DID.
            return Optional.empty();
        }
    }

    /**
     * Parses a <em>required</em> character-DID string.
     *
     * @param value the character-DID string
     * @return the parsed identity
     * @throws IllegalArgumentException if {@code value} is not a well-formed character DID
     */
    public static CharacterDid from(String value) {
        return parse(value)
                .orElseThrow(() -> new IllegalArgumentException("Not a well-formed character DID: '" + value
                        + "'. Expected " + PREFIX + "<slot>:<accountDid> "
                        + "(docs/architecture/09-player-state-portability.md §9)."));
    }

    /**
     * @param value a candidate string, or {@code null}
     * @return whether {@code value} has the {@code did:eyeandsickle:<int>:<accountDid>} shape — i.e. is a
     *     character DID this type can parse
     */
    public static boolean isCharacterDid(String value) {
        return parse(value).isPresent();
    }

    /**
     * @return the full, canonical character-DID string — the value stored in {@code *_did} columns and the
     *     inverse of {@link #parse(String)}
     */
    public String value() {
        return PREFIX + slot + ":" + accountDid;
    }

    /**
     * @return the character DID as its canonical string, so it logs and concatenates as the plain value
     *     rather than as {@code CharacterDid[accountDid=..., slot=...]}
     */
    @Override
    public String toString() {
        return value();
    }

    /**
     * A canonical positive integer: one or more ASCII digits with no leading zero, no sign and no
     * whitespace. Rejecting non-canonical spellings ({@code 02}, {@code +2}, {@code " 2"}) is what makes
     * the {@link #parse(String)} round-trip exact — {@link #of} never emits them, so accepting them on the
     * way in would let two distinct strings claim the same slot.
     */
    private static boolean isCanonicalPositiveInt(String digits) {
        if (digits.isEmpty()) {
            return false;
        }
        if (digits.length() > 1 && digits.charAt(0) == '0') {
            return false;
        }
        for (int i = 0; i < digits.length(); i++) {
            char c = digits.charAt(i);
            if (c < '0' || c > '9') {
                return false;
            }
        }
        return true;
    }
}

package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * How many characters the federation currently recognizes for an account — the number the slot cap is
 * checked against ({@code docs/architecture/09-player-state-portability.md} §2).
 *
 * <h2>Why this is a narrow seam, and why it is not a local {@code count(*)}</h2>
 *
 * The cap is "at most {@code maxCharacters} <em>recognized</em> characters" (09 §2), and "recognized" is
 * a federation-wide question, not a single-server one. There is no global account table to answer it —
 * Invariant I15 forbids a single arbiter — so the real answer comes from the signed, gossiped character
 * directory (09 §4): every honest server sees the same recognized set, and a defecting server's excess
 * 4th character is simply not recognized by anyone else.
 *
 * <p>That directory belongs to the discovery slice, which is built separately. So the cap check depends
 * on this one-method seam rather than on the directory directly. The identity slice ships a
 * <strong>safe default</strong> ({@code @ConditionalOnMissingBean} in {@link IdentityConfiguration}) that
 * counts only this server's own active rows — correct for a single, non-federating server, and the honest
 * floor for a federating one until the directory-backed implementation replaces it. When the discovery
 * slice contributes a directory-aware bean, it steps this default aside without any change to
 * {@link CharacterService}.
 *
 * <p>Implementations must count <em>recognized, live</em> characters only: a migrated character is
 * recognized at its new home, not its old one, and a retired character is recognized nowhere (09 §6.1).
 * They count the account's occupancy of live slots, network-wide.
 */
@FunctionalInterface
public interface RecognizedCharacterCount {

    /**
     * @param accountDid the account whose characters to count
     * @return how many characters the federation currently recognizes for this account
     */
    int countRecognized(Did accountDid);
}

package io.github.stoicswe.eyeandsickle.server.audit;

import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.lan.LanIdentity;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What the operator of a home server sees happening on it.
 *
 * <h2>Who this is for</h2>
 *
 * Somebody running a server for their friends, or for strangers, who needs to answer three questions
 * without a debugger: <em>who is on my server</em>, <em>what are they doing</em>, and <em>what did I
 * just refuse and why</em>. It is the same need in LAN and federated mode, so this is not gated on
 * either.
 *
 * <h2>⚠ A LAN IDENTITY IS A CREDENTIAL. IT IS NEVER LOGGED.</h2>
 *
 * This is the rule most likely to be broken by someone adding a line here, and it is worth being
 * blunt about: a LAN identity is {@code did:easlan:<uuid>} and that UUID is a <strong>bearer
 * token</strong> ({@code docs/architecture/12-lan-mode.md} §2). Whoever holds it is that player.
 *
 * <p>Logging it in full would mean the operator's log file is <em>a list of passwords</em> — a file
 * they tail in a terminal, ship to a log aggregator, and paste into a bug report. So a LAN identity is
 * recorded as a short **fingerprint**: stable enough to correlate one player's actions across a
 * session, useless for impersonating them.
 *
 * <p>⚠ A federated DID is the opposite and <strong>is</strong> logged in full. It is a public
 * identifier that proves nothing on its own — possession of a DID is not possession of the account,
 * because the account is proven by a signature. Fingerprinting it would destroy the operator's ability
 * to correlate with the allowlist, the ledger and the directory, for no security gain at all.
 *
 * <p>{@link #actor(Did)} makes that decision once, so no call site has to remember it.
 *
 * <h2>⚠ Levels mean something, because this game teaches that they do</h2>
 *
 * {@code alert-fatigue(7)} is a page in the game's own manual, and {@code CLAUDE.md} already applies
 * its lesson to the rig log: <em>a log that cries wolf teaches its reader to stop looking</em>. So:
 *
 * <ul>
 *   <li><b>INFO</b> — things that happened and are normal. Joins, sign-ins, intents, characters.
 *   <li><b>WARN</b> — a refusal the operator may want to act on: an allowlist denial, a rejected
 *       token. ⚠ Not an error — the system worked exactly as designed.
 *   <li><b>ERROR</b> — the server could not do its job, or something happened that should be
 *       impossible. A replayed token is here, because it means somebody is trying.
 * </ul>
 *
 * <h2>Structured, because an operator greps</h2>
 *
 * Every line is {@code event=<name> key=value …} with a stable event name. "What did this player do"
 * should be one {@code grep}, not regex archaeology over prose.
 */
@Component
public class OperatorLog {

    private static final Logger log = LoggerFactory.getLogger("eyeandsickle.operator");

    /** ⚠ Bounded: the key is attacker-supplied. See {@link #name}. */
    static final int MAX_NAMES = 10_000;

    /** Identity → {@code username@rig}. */
    private final java.util.Map<String, String> names = new java.util.concurrent.ConcurrentHashMap<>();

    // ── identity ──────────────────────────────────────────────────────────────────────────────

    /**
     * How an actor is named in the log.
     *
     * <p>⚠ The whole credential/identifier decision lives here and nowhere else — see the class note.
     * A federated DID is public and logged whole; a LAN identity is a bearer token and is reduced to a
     * fingerprint.
     *
     * @param did the actor
     * @return a safe, stable label
     */
    public String actor(Did did) {
        if (did == null) {
            return "anonymous";
        }
        String known = names.get(did.value());
        if (known != null) {
            return known;
        }
        return anonymousLabel(did);
    }

    /**
     * The label before anyone has told us a name — and the fallback if nobody ever does.
     *
     * <p>Static because {@link #actor} needs it and tests assert on it directly.
     */
    static String anonymousLabel(Did did) {
        return LanIdentity.isLanIdentity(did) ? "lan:" + fingerprint(did.value()) : did.value();
    }

    /**
     * Records what to call somebody, so every later line reads {@code username@rig}.
     *
     * <h2>⚠ The username is NOT the credential — the UUID is</h2>
     *
     * Which is what makes this safe and worth doing. A LAN player's identity is
     * {@code did:easlan:<uuid>} and that UUID is a bearer token; their <em>username</em> is a display
     * name they chose and gave to the server, exactly like a federated handle. Logging the name is
     * free; logging the UUID would be logging a password.
     *
     * <p>⚠ And it is what an operator actually needs. A fingerprint answers "is this the same player
     * as the last line", which is useful; {@code ghost@nightjar} answers "who is this", which is the
     * question they are actually asking. The fingerprint stays as the fallback for an actor nobody
     * has named yet, so a line is never unattributable.
     *
     * <p>⚠ <strong>Bounded.</strong> The key is attacker-supplied — anyone who can reach a LAN server
     * can join repeatedly — so this cannot be an unbounded map keyed on "every identity that ever
     * appeared". At the cap it is cleared rather than grown; the cost is that some later lines fall
     * back to the fingerprint, which is a degraded log rather than a dead server.
     *
     * @param did the actor
     * @param username the display name the player chose
     * @param rigName the rig/character they are on, or null before one is selected
     */
    public void name(Did did, String username, String rigName) {
        if (did == null || username == null || username.isBlank()) {
            return;
        }
        if (names.size() >= MAX_NAMES) {
            names.clear();
        }
        String label = rigName == null || rigName.isBlank() ? bare(username) : bare(username) + "@" + bare(rigName);
        names.put(did.value(), label);
    }

    /** Like {@link #safe} but never quoted — this is a fragment inside a larger value. */
    private static String bare(String value) {
        String cleaned = value.replaceAll("[\\r\\n\\t \"=]", "_").trim();
        if (cleaned.isEmpty()) {
            return "-";
        }
        return cleaned.length() > 32 ? cleaned.substring(0, 32) : cleaned;
    }

    /**
     * A short, stable, non-reversing label for a secret.
     *
     * <p>⚠ Truncated to 12 hex characters deliberately. Long enough to distinguish the players on one
     * server without collision in practice; short enough that nobody mistakes it for something they
     * can present back to the server. ⚠ It is <em>not</em> a security boundary on its own — it is
     * SHA-256 of a UUID, and a determined reader with the UUID can confirm a match. What it prevents
     * is the log <em>being</em> the credential, which is the actual failure.
     */
    static String fingerprint(String secret) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 12);
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("SHA-256 is required on every JVM", impossible);
        }
    }

    /**
     * Strips anything that would break the {@code key=value} shape or forge a log line.
     *
     * <p>⚠ Log injection is real here: a <strong>username is player-supplied</strong> and a newline in
     * one lets a player write their own log lines — including a fabricated
     * {@code event=player.signin.denied} for somebody else. Truncated too, because a 64-character
     * username should not push the rest of the line off an operator's terminal.
     */
    static String safe(String value) {
        if (value == null) {
            return "-";
        }
        String cleaned = value.replaceAll("[\\r\\n\\t]", " ").trim();
        if (cleaned.isEmpty()) {
            return "-";
        }
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64) + "…";
        }
        return cleaned.contains(" ") ? "\"" + cleaned.replace("\"", "'") + "\"" : cleaned;
    }

    // ── lifecycle ─────────────────────────────────────────────────────────────────────────────

    /** Said once at startup, so every later line has a mode to be read against. */
    public void serverStarted(String mode, boolean federation, boolean secp256k1) {
        log.info(
                "event=server.started mode={} federation={} secp256k1={}",
                safe(mode),
                federation ? "on" : "off",
                secp256k1 ? "available" : "UNAVAILABLE");
    }

    // ── joining and signing in ────────────────────────────────────────────────────────────────

    /**
     * A LAN player asked for an identity.
     *
     * <p>⚠ The minted identity is <strong>never</strong> in this line — see the class note. The
     * fingerprint is what ties it to everything they do next.
     */
    public void lanJoined(String username, Did minted) {
        // Registered FIRST, so this line and every line after it read the same way.
        name(minted, username, null);
        log.info("event=player.joined mode=lan actor={}", actor(minted));
    }

    public void signedIn(Did did, String handle, int characterCount) {
        // A federated handle is the same kind of thing as a LAN username: a display name, not a proof.
        name(did, handle, null);
        log.info("event=player.signin.ok actor={} characters={}", actor(did), characterCount);
    }

    /**
     * A sign-in was refused because the DID is not allow-listed.
     *
     * <p>⚠ WARN, not ERROR: servers are closed by default ({@code docs/architecture/03} §1), so this
     * is the system working. It is above INFO because it is the one refusal an operator can fix, and
     * the player on the other end is stuck until they do.
     */
    public void signInDenied(Did did, String reason) {
        log.warn("event=player.signin.denied actor={} reason={}", actor(did), safe(reason));
    }

    /** A presented proof did not check out. WARN — could be a bug on their side or an attempt. */
    public void signInRefused(String reason) {
        log.warn("event=player.signin.refused reason={}", safe(reason));
    }

    // ── characters ────────────────────────────────────────────────────────────────────────────

    public void characterCreated(Did did, String characterId, String handle) {
        log.info(
                "event=character.created actor={} character={} handle={}", actor(did), safe(characterId), safe(handle));
    }

    /**
     * ⚠ Where {@code username@rig} becomes complete. Until a character is selected there is no rig,
     * so earlier lines carry the username alone — which is correct rather than incomplete.
     */
    public void characterSelected(Did did, String characterId, String rigName) {
        name(did, currentUsername(did), rigName);
        log.info("event=character.selected actor={} character={}", actor(did), safe(characterId));
    }

    /** The username already on file, so selecting a character does not erase it. */
    private String currentUsername(Did did) {
        String known = names.get(did.value());
        if (known == null) {
            return null;
        }
        int at = known.indexOf('@');
        return at < 0 ? known : known.substring(0, at);
    }

    // ── play ──────────────────────────────────────────────────────────────────────────────────

    /**
     * A player asked the server to do something, and it did.
     *
     * <p>⚠ The intent's <em>type and parameters</em>, never a snapshot of the resulting state. A log
     * line per state change would be a second, divergent copy of the game state in a text file — and
     * the ledger already exists for the part of that which matters ({@code design/01} §2.2).
     */
    public void intentApplied(String characterId, String intent, long revision) {
        log.info("event=intent.applied character={} intent={} revision={}", safe(characterId), safe(intent), revision);
    }

    /** ⚠ INFO, not WARN. A refused intent is usually a player asking for more than they have. */
    public void intentRefused(String characterId, String intent, String reason) {
        log.info(
                "event=intent.refused character={} intent={} reason={}", safe(characterId), safe(intent), safe(reason));
    }

    // ── security ──────────────────────────────────────────────────────────────────────────────

    /**
     * A service-auth token was presented twice.
     *
     * <p>⚠ ERROR, and the only routine event at that level. Replay is not a misconfiguration and not
     * a player mistake — the token is single-use by construction, so a second presentation means
     * somebody captured one and tried it. An operator should see this even if they see nothing else.
     */
    public void replayRejected(String detail) {
        log.error("event=security.replay.rejected detail={}", safe(detail));
    }

    /**
     * LAN state was asked to cross into federated state and was refused.
     *
     * <p>⚠ WARN: it is the quarantine working ({@code 12} §1), but an operator seeing it should know
     * somebody tried to migrate a character that can never migrate — usually confusion, occasionally
     * not.
     */
    public void quarantineRefused(Did did, String what) {
        log.warn("event=security.quarantine.refused actor={} attempted={}", actor(did), safe(what));
    }
}

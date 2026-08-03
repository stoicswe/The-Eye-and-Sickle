package io.github.stoicswe.eyeandsickle.server.lan;

import io.github.stoicswe.eyeandsickle.server.identity.Did;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where a player joins a LAN server: a username in, an identity out.
 *
 * <h2>⚠ This endpoint hands an identity to anyone who asks</h2>
 *
 * That is the design ({@code docs/architecture/12-lan-mode.md} §2) and it is only defensible because
 * {@link LanAddressInterlock} guarantees the server is on a private network. It exists <strong>only in
 * LAN mode</strong> — in federated mode the bean is absent, so there is no endpoint to forget to
 * guard.
 *
 * <p>⚠ Joining does <em>not</em> create a character. It creates an identity; characters are created
 * against it afterwards, cap-checked, exactly as in federated mode
 * ({@code docs/architecture/09-player-state-portability.md} §1). Minting a character here would give
 * anyone who can reach the port an unbounded way to fill the database.
 */
@RestController
@RequestMapping("/api/lan/join")
public class LanJoinController {

    private final io.github.stoicswe.eyeandsickle.server.audit.OperatorLog operatorLog;

    LanJoinController(io.github.stoicswe.eyeandsickle.server.audit.OperatorLog operatorLog) {
        this.operatorLog = operatorLog;
    }

    /**
     * @param username what to call this player on screen; ⚠ NOT unique and never made unique — two
     *     players called {@code ghost} is a social problem with a social fix, and making the name a
     *     key would recreate the thing the identity exists to be (12 §2)
     */
    public record JoinRequest(String username) {}

    /**
     * @param did the minted identity — {@code did:easlan:<uuid>}
     * @param username echoed back, trimmed
     */
    public record JoinResponse(String did, String username) {}

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public JoinResponse join(@RequestBody JoinRequest request) {
        String username = request == null || request.username() == null
                ? ""
                : request.username().trim();
        if (username.isEmpty()) {
            throw new IllegalArgumentException("a username is required");
        }
        if (username.length() > 64) {
            // A bound, not a rule about names. This lands in a display and in a database column.
            throw new IllegalArgumentException("that username is too long");
        }
        Did did = LanIdentity.mint();
        // ⚠ The minted identity is a bearer token; OperatorLog reduces it to a fingerprint rather
        // than writing a credential into the operator's log file.
        operatorLog.lanJoined(username, did);
        return new JoinResponse(did.value(), username);
    }
}

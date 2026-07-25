package io.github.stoicswe.eyeandsickle.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Entry point for a self-hosted home server.
 *
 * <p>A home server is the unit of deployment for this game ({@code
 * docs/architecture/03-server-and-federation.md}). Anyone can run one; the operator controls who
 * joins via an allowlist. There is no central game server and there is not meant to be — Invariant
 * I15 says no single arbiter decides cross-server adversarial outcomes, and a central server would be
 * exactly that.
 *
 * <h2>What this process is authoritative for</h2>
 *
 * All game state for its players, held in PostgreSQL: inventories, ethecoin balances, compute
 * allocations, rig configuration, the public ledger, deployed-miner records, and home-server-local
 * PvP resolution. The client renders this; it never decides it (Invariant I14).
 *
 * <p>AT Protocol is used to <em>authenticate</em> and to obtain a player's DID, and for nothing else.
 * No item, balance, or rig state is ever written to a player's PDS — that would put adversarial state
 * on infrastructure the player controls, which is the self-hosted-cheating problem relocated one
 * layer down.
 *
 * <h2>Federation is opt-in</h2>
 *
 * A private, allowlisted server can ignore federation entirely and simply be the single-player or
 * friends experience. When federation is enabled, this process additionally serves non-adversarial
 * directory data, acts as a validator when sampled, and verifies item provenance on cross-server
 * transfers.
 */
// @ConfigurationPropertiesScan registers every @ConfigurationProperties record across all slices in
// one place. The alternative — each slice's own @EnableConfigurationProperties — is what the slices
// were building toward, but several were interrupted before writing that config, leaving their
// properties records unbound (a record annotated only @ConfigurationProperties does not self-register;
// constructor binding needs one of these two mechanisms). Scanning once here is the idiomatic fix and
// is idempotent with the explicit registrations that do exist (persistence, federation).
@SpringBootApplication
@ConfigurationPropertiesScan
public class EyeAndSickleServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EyeAndSickleServerApplication.class, args);
    }
}

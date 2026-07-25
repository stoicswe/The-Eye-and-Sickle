package io.github.stoicswe.eyeandsickle.server.identity;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Copies the configured seed DIDs ({@code eyeandsickle.allowlist.dids}) into {@code allowlist_entries}
 * at startup, so the operator's initial list lands in the durable, runtime-editable table.
 *
 * <h2>Configuration seeds the table; the table is the source of truth</h2>
 *
 * The environment value is where an operator names the first players
 * ({@code docs/architecture/03-server-and-federation.md} §1, {@code deploy/.env.example}), but the
 * authoritative allowlist is the table — that is what lets an operator add and revoke without a restart.
 * Seeding is a one-way copy on boot: each configured DID is inserted only if absent
 * ({@link AllowlistRepository#insertIfAbsent}), so a DID an operator has since <em>revoked</em> in the
 * table is not silently re-admitted just because it is still in the config. The runtime table always
 * wins, which is the whole reason it exists.
 *
 * <h2>Fail loud on a malformed seed</h2>
 *
 * A typo in the one list that decides who may play should stop startup, not be silently dropped, so
 * {@link AllowlistProperties#parsedDids()} validates shape and this runner lets the failure propagate.
 */
@Component
public class AllowlistSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AllowlistSeeder.class);

    /** Attribution note on a seeded entry, distinguishing it from an operator's manual addition. */
    static final String SEED_NOTE = "seeded from configuration (eyeandsickle.allowlist.dids)";

    private final AllowlistRepository allowlist;
    private final AllowlistProperties properties;
    private final Clock clock;

    /**
     * @param allowlist the allowlist table
     * @param properties the configured seed list
     * @param clock the source of the seed timestamp
     */
    public AllowlistSeeder(AllowlistRepository allowlist, AllowlistProperties properties, Clock clock) {
        this.allowlist = Objects.requireNonNull(allowlist, "allowlist");
        this.properties = Objects.requireNonNull(properties, "properties");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public void run(ApplicationArguments args) {
        seed();
    }

    /**
     * Performs the seed. Exposed separately from {@link #run(ApplicationArguments)} so it can be
     * exercised directly by an integration test without an application context.
     *
     * @return the number of entries newly inserted (already-present DIDs are not counted)
     */
    public int seed() {
        List<Did> dids = properties.parsedDids();
        if (dids.isEmpty()) {
            // Closed by default: no seeds means nobody is admitted until the operator adds someone. Said
            // at INFO because it is a normal, intentional state, not a warning.
            log.info("No allowlist DIDs configured; the server is closed until entries are added.");
            return 0;
        }
        int added = 0;
        for (Did did : dids) {
            boolean inserted = allowlist.insertIfAbsent(did, null, SEED_NOTE, clock.instant());
            if (inserted) {
                added++;
                log.info("Seeded allowlist entry for {}", did);
            } else {
                log.debug("Allowlist already contains {}; leaving the table's entry as-is", did);
            }
        }
        log.info("Allowlist seeding complete: {} configured, {} newly added", dids.size(), added);
        return added;
    }
}

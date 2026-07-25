package io.github.stoicswe.eyeandsickle.server.directory.web;

import io.github.stoicswe.eyeandsickle.server.directory.CharacterDirectoryService;
import io.github.stoicswe.eyeandsickle.server.directory.CharacterDirectoryService.IngestResult;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST over the character directory: home servers <em>publish</em> signed home records, and clients
 * <em>resolve</em> "where are my characters?" from a DID ({@code
 * docs/architecture/09-player-state-portability.md} §4).
 *
 * <h2>The client renders this; it never decides it (Invariant I14)</h2>
 *
 * Every endpoint is a thin edge over {@link CharacterDirectoryService}, which owns verification and
 * convergence. The controller's only jobs are to turn a request into a service call and a service result
 * — or a typed refusal — into a status code. No rule lives here: not signature verification, not the
 * monotonic sequence, not the soft slot cap.
 *
 * <h2>Untrusted input, bounded and typed</h2>
 *
 * A published record arrives from a server this one does not control (03 §1). It is size-capped and every
 * malformation is a typed {@link io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeFault} in
 * the service, surfaced here as a {@code 422} carrying the fault name — never a stack trace and never a
 * {@code 500}. A resolution for an unknown or malformed DID returns an empty list, disclosing nothing
 * about who exists.
 *
 * <h2>Gated to federating servers</h2>
 *
 * Registered only when {@code eyeandsickle.federation.enabled=true}: the directory table lives under the
 * federation migration location, so a non-federating home server has neither the table nor a reason to
 * expose these endpoints.
 *
 * <h2>What this controller does not do — authorization</h2>
 *
 * Proving that a publish really comes from the home server it names belongs to the transport/identity
 * layer (the record's own home-server signature is the cryptographic anchor; a DID is a public,
 * gossip-safe identifier, 09 §7). As with the compute and character controllers, principal-based
 * authorization is expected to sit in front of this controller as a filter and is a documented, known
 * gap rather than a silent one.
 */
@RestController
@RequestMapping("/api/directory")
@ConditionalOnProperty(prefix = "eyeandsickle.federation", name = "enabled", havingValue = "true")
public class CharacterDirectoryController {

    /** A cheap DID-shape gate so a clearly-malformed path DID resolves to empty rather than a table scan. */
    private static final Pattern DID_SHAPE = Pattern.compile("^did:[a-z0-9]+:[A-Za-z0-9._%:-]+$");

    private static final int DID_MAX_LENGTH = 512;

    private final CharacterDirectoryService directory;
    private final Clock clock;

    CharacterDirectoryController(CharacterDirectoryService directory, Clock clock) {
        this.directory = Objects.requireNonNull(directory, "directory");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Publishes a signed home record to the directory.
     *
     * <p>The body is the raw home-record JSON envelope, verbatim — the verifier bounds and parses it
     * itself, so the raw bytes reach it without a re-serialization that could change them.
     *
     * @param rawEnvelope the published home-record JSON
     * @return 200 with the convergence outcome if the record's signature verified; 422 with the fault if
     *     it did not
     */
    @PostMapping(value = "/records", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<PublishHomeResult> publish(@RequestBody String rawEnvelope) {
        IngestResult result = directory.ingest(rawEnvelope, clock.instant());
        PublishHomeResult body = PublishHomeResult.from(result);
        HttpStatus status = result.verification().isAccepted() ? HttpStatus.OK : HttpStatus.UNPROCESSABLE_ENTITY;
        return ResponseEntity.status(status).body(body);
    }

    /**
     * Resolves an account's characters and their homes — the "where are my characters?" lookup.
     *
     * @param accountDid the account to resolve
     * @return the account's recognized home bindings as re-verifiable views (200); an empty list for an
     *     unknown or malformed DID
     */
    @GetMapping("/accounts/{accountDid}/homes")
    public List<CharacterHomeView> resolve(@PathVariable String accountDid) {
        if (accountDid.length() > DID_MAX_LENGTH
                || !DID_SHAPE.matcher(accountDid).matches()) {
            return List.of();
        }
        return directory.resolveHomes(accountDid).stream()
                .map(CharacterHomeView::from)
                .toList();
    }
}

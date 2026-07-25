package io.github.stoicswe.eyeandsickle.server.directory.web;

import io.github.stoicswe.eyeandsickle.server.directory.CharacterDirectoryService.IngestResult;

/**
 * The wire result of publishing a home record to the directory.
 *
 * <p>It reports both halves of {@link IngestResult}: whether the record verified, and — if it did — what
 * convergence did with it ({@code ACCEPTED_NEW}, {@code IGNORED_STALE}, and the rest). A refused record
 * carries its {@link io.github.stoicswe.eyeandsickle.server.directory.CharacterHomeFault} name and a
 * human-readable detail for the operator log; a verified one carries the outcome and whether it changed
 * stored state. The controller maps {@link #accepted()} to the HTTP status, so the caller can branch on
 * the code and read the specifics here.
 *
 * @param accepted whether the record's signature verified
 * @param outcome the convergence outcome name if accepted, else {@code null}
 * @param stored whether accepting the record actually changed stored state (a new or advanced binding)
 * @param fault the refusal classification name if rejected, else {@code null}
 * @param detail a human-readable elaboration; never trusted, never parsed
 */
public record PublishHomeResult(boolean accepted, String outcome, boolean stored, String fault, String detail) {

    /**
     * Builds the wire result from an ingest outcome.
     *
     * @param result the service ingest result
     * @return the wire result
     */
    public static PublishHomeResult from(IngestResult result) {
        if (!result.verification().isAccepted()) {
            return new PublishHomeResult(
                    false,
                    null,
                    false,
                    result.verification().fault().name(),
                    result.verification().detail());
        }
        return new PublishHomeResult(true, result.outcome().name(), result.stored(), null, null);
    }
}

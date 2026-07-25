package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.server.identity.CharacterNotActiveException;
import io.github.stoicswe.eyeandsickle.server.identity.CharacterSlotExceededException;
import io.github.stoicswe.eyeandsickle.server.identity.PlayerNotFoundException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the migration slice's typed failures into HTTP status codes, for the controllers in this package
 * only.
 *
 * <h2>Scoped to this package on purpose</h2>
 *
 * {@code @RestControllerAdvice(basePackageClasses = CharacterMigrationController.class)} limits this
 * advice to the migration controllers, exactly as the identity slice scopes its own. That matters most for
 * the broad handlers: an {@link IllegalArgumentException} here is a malformed bundle or DID ({@code 400}),
 * and it must not silently reinterpret another slice's argument errors, nor may another slice's advice
 * reinterpret a migration one.
 *
 * <h2>The type is the status</h2>
 *
 * Each failure encodes why it happened, and this is the one place that becomes a status: a stale home
 * sequence is a {@code 409} rollback conflict (§6.1); a full-state request without operator authority is a
 * {@code 403} (§5); an oversized bundle is a {@code 413}; the no-double-play refusal reuses the identity
 * slice's {@link CharacterNotActiveException} as a {@code 409}.
 */
@RestControllerAdvice(basePackageClasses = CharacterMigrationController.class)
class MigrationExceptionHandler {

    /**
     * @param exception a bundle presenting a home sequence that does not advance the directory
     * @return 409 — the character's home has already moved past the bundle (no rollback / no fork)
     */
    @ExceptionHandler(StaleHomeSequenceException.class)
    ProblemDetail onStaleSequence(StaleHomeSequenceException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception a full-state (Option B) request without operator authority
     * @return 403 — the caller is understood but is not an operator of this server
     */
    @ExceptionHandler(OperatorAuthorizationException.class)
    ProblemDetail onNotOperator(OperatorAuthorizationException exception) {
        return problem(HttpStatus.FORBIDDEN, exception);
    }

    /**
     * @param exception a bundle over a configured size bound
     * @return 413 — refused before verification, a denial-of-service guard
     */
    @ExceptionHandler(MigrationBundleTooLargeException.class)
    ProblemDetail onTooLarge(MigrationBundleTooLargeException exception) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, exception);
    }

    /**
     * @param exception an operation on a migrated or retired character
     * @return 409 — the character's lifecycle state forbids it (no double-play, no replay)
     */
    @ExceptionHandler(CharacterNotActiveException.class)
    ProblemDetail onNotActive(CharacterNotActiveException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception a create that would exceed the account's recognized-character cap
     * @return 409 — the account cannot hold another character
     */
    @ExceptionHandler(CharacterSlotExceededException.class)
    ProblemDetail onSlotExceeded(CharacterSlotExceededException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception a lookup that resolved to no character
     * @return 404
     */
    @ExceptionHandler(PlayerNotFoundException.class)
    ProblemDetail onNotFound(PlayerNotFoundException exception) {
        return problem(HttpStatus.NOT_FOUND, exception);
    }

    /**
     * @param exception a write lost to a concurrent change
     * @return 409 — retryable: re-read and re-decide
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail onConcurrentChange(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception a malformed bundle document or a malformed DID
     * @return 400
     */
    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail onBadRequest(IllegalArgumentException exception) {
        return problem(HttpStatus.BAD_REQUEST, exception);
    }

    private static ProblemDetail problem(HttpStatus status, Exception exception) {
        return ProblemDetail.forStatusAndDetail(status, exception.getMessage());
    }
}

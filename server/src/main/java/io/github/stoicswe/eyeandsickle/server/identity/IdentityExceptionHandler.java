package io.github.stoicswe.eyeandsickle.server.identity;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Turns the identity slice's typed failures into HTTP status codes, for the controllers in this package.
 *
 * <h2>The type is the status</h2>
 *
 * Each exception in this slice encodes <em>why</em> it failed, and the mapping here is the one place that
 * becomes an HTTP status — the mechanism the sign-in exceptions' Javadoc already refers to. Deliberately
 * coarse for authentication failures: {@link SignInException} is a flat {@code 401} that tells an
 * attacker as little as possible about which step failed, while its subclasses split off the cases that
 * are safe to distinguish — a proven-but-unlisted identity ({@code 403}) and a missing provider
 * ({@code 503}).
 *
 * <h2>Scoped to this package on purpose</h2>
 *
 * {@code @RestControllerAdvice(basePackageClasses = CharacterController.class)} limits this advice to the
 * identity package's controllers. Other slices (the compute controller, for one) map their own typed
 * errors, and a broad handler for {@link IllegalArgumentException} here must not silently reinterpret
 * theirs. Within this slice a malformed path DID is a {@code 400}, and a lost status transition under a
 * concurrent writer is a retryable {@code 409}.
 */
@RestControllerAdvice(basePackageClasses = CharacterController.class)
class IdentityExceptionHandler {

    /**
     * @param exception a missing-provider failure
     * @return 503 — the capability is not available on this server
     */
    @ExceptionHandler(SignInUnavailableException.class)
    ProblemDetail onUnavailable(SignInUnavailableException exception) {
        return problem(HttpStatus.SERVICE_UNAVAILABLE, exception);
    }

    /**
     * @param exception an authenticated-but-unlisted identity
     * @return 403 — believed, but not admitted
     */
    @ExceptionHandler(SignInDeniedException.class)
    ProblemDetail onDenied(SignInDeniedException exception) {
        return problem(HttpStatus.FORBIDDEN, exception);
    }

    /**
     * @param exception a bare authentication failure
     * @return 401 — the identity was not proven; deliberately vague
     */
    @ExceptionHandler(SignInException.class)
    ProblemDetail onSignInFailed(SignInException exception) {
        return problem(HttpStatus.UNAUTHORIZED, exception);
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
     * @param exception a create that would exceed the account's slot cap
     * @return 409 — the account's state does not permit another character
     */
    @ExceptionHandler(CharacterSlotExceededException.class)
    ProblemDetail onSlotExceeded(CharacterSlotExceededException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception an operation on a migrated or retired character
     * @return 409 — the character's lifecycle state forbids it (no double-play)
     */
    @ExceptionHandler(CharacterNotActiveException.class)
    ProblemDetail onNotActive(CharacterNotActiveException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception a status transition lost to a concurrent writer
     * @return 409 — retryable: re-read and re-decide
     */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    ProblemDetail onConcurrentChange(OptimisticLockingFailureException exception) {
        return problem(HttpStatus.CONFLICT, exception);
    }

    /**
     * @param exception malformed input, most often a path DID that is not well-shaped
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

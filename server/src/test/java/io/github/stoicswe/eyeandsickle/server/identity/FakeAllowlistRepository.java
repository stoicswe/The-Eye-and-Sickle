package io.github.stoicswe.eyeandsickle.server.identity;

import static org.mockito.Mockito.mock;

import java.util.HashSet;
import java.util.Set;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * An in-memory stand-in for {@link AllowlistRepository} for the Docker-free unit tests.
 *
 * <p>{@link AllowlistRepository} is a concrete class over a {@link JdbcClient}, so a fake has to be a
 * subclass; the super constructor is satisfied with a Mockito mock that is never touched, because every
 * method a service actually calls is overridden here. The point of the fake — over stubbing the
 * repository with Mockito at every call site — is that admission is expressed as a set of DIDs, which
 * reads like the allowlist it stands for.
 */
final class FakeAllowlistRepository extends AllowlistRepository {

    private final Set<Did> allowed = new HashSet<>();
    private int isAllowedQueries = 0;

    FakeAllowlistRepository() {
        super(mock(JdbcClient.class));
    }

    /** Puts a DID on the list, i.e. an operator has admitted it. */
    FakeAllowlistRepository allow(Did did) {
        allowed.add(did);
        return this;
    }

    @Override
    public boolean isAllowed(Did did) {
        isAllowedQueries++;
        return allowed.contains(did);
    }

    /** Whether the join gate ever asked us — used to prove ordering (auth happens before the gate). */
    boolean wasQueried() {
        return isAllowedQueries > 0;
    }
}

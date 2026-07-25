package io.github.stoicswe.eyeandsickle.server.economy.account;

import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * A seam that lets integration tests in other slices (notably the ledger) construct a real {@link
 * AccountRepository}. The repository's constructor is package-private on purpose — production wiring
 * goes through Spring — so a cross-package test cannot call it directly; this factory, which lives in
 * the repository's own package, is the sanctioned way in.
 */
public final class AccountRepositoryTestSupport {

    private AccountRepositoryTestSupport() {}

    /**
     * @param jdbcClient a client on the test database
     * @return a real {@link AccountRepository} backed by it
     */
    public static AccountRepository real(JdbcClient jdbcClient) {
        return new AccountRepository(jdbcClient);
    }
}

package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.springframework.jdbc.core.simple.JdbcClient;

/**
 * An in-memory stand-in for {@link LedgerRepository}, so {@code LedgerService} can be unit-tested
 * without a database.
 *
 * <p>{@link #append(LedgerTransaction)} just records the row, so a test can assert exactly what was —
 * or, on a rejected operation, was <em>not</em> — written. {@link #query(LedgerQuery, String)} records
 * the call and echoes the appended rows; the interesting Dead-Drop visibility SQL is exercised for real
 * in {@code LedgerRepositoryIT}, so here it only needs to prove the service forwards the viewer rather
 * than folding it into the filters.
 */
public final class FakeLedgerRepository extends LedgerRepository {

    /** Every appended row, in call order. */
    public final List<LedgerTransaction> appended = new ArrayList<>();

    /** The arguments of the last {@link #query(LedgerQuery, String)} call, or {@code null}. */
    public LedgerQuery lastQuery;

    public String lastViewer;

    public FakeLedgerRepository() {
        super(mock(JdbcClient.class));
    }

    @Override
    public void append(LedgerTransaction transaction) {
        appended.add(Objects.requireNonNull(transaction, "transaction"));
    }

    @Override
    public List<LedgerTransaction> query(LedgerQuery query, String viewerDid) {
        this.lastQuery = query;
        this.lastViewer = viewerDid;
        return List.copyOf(appended);
    }
}

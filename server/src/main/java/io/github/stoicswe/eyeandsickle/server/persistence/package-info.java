/**
 * How this server talks to its PostgreSQL — the conventions every other package copies.
 *
 * <h2>The decision this package implements</h2>
 *
 * Spring's {@code JdbcClient} over hand-written SQL, mapped to Java <strong>records</strong>. No JPA,
 * no Hibernate, no jOOQ, no {@code @Entity}, no Spring Data repositories. That is open question A-4
 * in {@code docs/design/15-open-questions.md}, resolved; the reasoning is in {@code server/pom.xml}
 * and comes down to three things:
 *
 * <ol>
 *   <li>The signed crypto payloads and item attrs are document-shaped {@code jsonb}
 *       ({@code docs/architecture/06-data-model.md} §4). JPA is awkward with jsonb; JdbcClient passes
 *       it through.
 *   <li>The manual-audit gameplay ({@code docs/design/04-mining.md} §3.1) runs against the compute
 *       ledger, and Invariant I6 makes a <em>discrepancy</em> the thing a player is hunting for.
 *       Queries that carry that meaning should be legible SQL, not a derived ORM projection.
 *   <li>jOOQ was the close runner-up, but its codegen wants a live database at build time, which
 *       would break "{@code mvn verify} must never require Docker".
 * </ol>
 *
 * Inject {@code JdbcClient}, constructor-injected, package-private where possible. Flyway owns the
 * schema; nothing at runtime alters it.
 *
 * <h2>The one rule above all: this server is authoritative</h2>
 *
 * Invariant I14. Anything a cheating client could lie about — balances, item ownership, compute
 * allocation, breach outcomes, duel results — is decided here and validated here. A value that
 * arrived in a request is an <em>assertion</em>, not a fact, and it does not become a fact by being
 * passed to a repository. Nothing in this package writes a value it did not first check against
 * state it read from this database.
 *
 * <h2>Mapping a row to a record</h2>
 *
 * One class per table, holding that table's column names as constants and its mapper as a static
 * field. {@link io.github.stoicswe.eyeandsickle.server.persistence.RowMappers} has the worked
 * example. Three rules:
 *
 * <ul>
 *   <li><strong>Explicit accessors, never reflection.</strong>
 *       {@link io.github.stoicswe.eyeandsickle.server.persistence.Row} explains why: a reflective
 *       mapper that misses a column can leave a field at its default, and on this schema a plausible
 *       default is a zero balance, a zero heat, or a zero-cycle allocation.
 *   <li><strong>Nullability is in the method name.</strong> {@code row.text(...)} requires a value;
 *       {@code row.textOrNull(...)} permits one. A NULL out of a NOT NULL column means the query
 *       selected something other than what the mapper thinks it did.
 *   <li><strong>Never {@code SELECT *}.</strong> Listing columns keeps the query and the mapper
 *       reviewable against each other, stops a later migration silently widening a hot query, and
 *       keeps a column a caller has no business seeing out of the result set by construction.
 * </ul>
 *
 * <h2>jsonb</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.server.persistence.Jsonb}, and note its first line: every
 * jsonb parameter must be cast in the SQL — {@code :attrs::jsonb} — because the driver sends a
 * {@code String} as {@code varchar} and PostgreSQL will not coerce it.
 *
 * <p>jsonb is for genuinely document-shaped data only: signed provenance payloads and envelopes, item
 * attrs, installed rig modules, quorum sampling records. Anything you would filter, join, or
 * constrain belongs in a column — a balance in jsonb is a balance no CHECK constraint can defend, and
 * on an authoritative server the database is the last line of defence.
 *
 * <p>A document whose signature must still verify is stored <em>verbatim</em>. Parsing and
 * re-serializing a provenance envelope before storage risks changing the exact bytes the signature
 * covers, and the symptom is a federation that cannot verify its own records.
 *
 * <h2>Ethecoin and cycles</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.server.persistence.EconomyColumns}. Invariant I1 — compute
 * is never purchasable with ethecoin — is what stops the economy becoming a compounding flywheel, and
 * the protocol module keeps {@code Ethecoin} and {@code Cycles} unconvertible for exactly that
 * reason. As columns they are both {@code bigint} and the type system stops helping, so the column
 * NAME carries the unit: {@code *_wei} for ethecoin, {@code *_cycles} for compute. Asking for one
 * out of the other's column is refused by name.
 *
 * {@snippet lang = java:
 * Ethecoin balance = EconomyColumns.ethecoin(row, "ethecoin_balance_wei");   // fine
 * Cycles ceiling = EconomyColumns.cycles(row, "total_cycles");                    // fine
 * Ethecoin oops = EconomyColumns.ethecoin(row, "total_cycles");                   // refused, loudly
 *}
 *
 * <h2>Enumerations</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.server.persistence.EnumColumns} is the only place a protocol
 * enum is spelled as a database value, using exhaustive switches so renaming a Java constant is a
 * compile error rather than a silent vocabulary change. Columns are {@code text} + a named CHECK
 * rather than a PostgreSQL {@code ENUM} type, because half these vocabularies are still
 * {@code [PROPOSAL]} and a text vocabulary changes in one line.
 *
 * <h2>Concurrency and transactions</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.server.persistence.Mutations}. Every mutable table carries
 * {@code row_version}; mutations write conditionally on it and check the affected-row count. Where
 * the decision depends on rows you did not read — the compute ledger, where the question is about a
 * SUM — take {@code SELECT ... FOR UPDATE} on the parent rig inside the transaction, in a consistent
 * lock order, because Invariant I6 makes cross-rig operations routine and unordered locking makes
 * them deadlock.
 *
 * <p>A ledger row and the balance change it describes are written in ONE transaction. Both
 * {@code ledger_transactions} and {@code provenance_records} are append-only at the database level (a
 * trigger refuses UPDATE and DELETE), so a half-written transfer cannot be tidied up afterwards —
 * which is the point of an evidence surface.
 *
 * <h2>Calibrated numbers</h2>
 *
 * {@link io.github.stoicswe.eyeandsickle.server.persistence.PersistenceProperties} holds the one
 * value the schema forces someone to supply, and should stay that small. Yields, prices, sweep
 * probabilities and gate thresholds belong to the system that owns them, each with its own bound
 * properties class. The economy figures in {@code docs/design/03-economy.md} and
 * {@code docs/design/04-mining.md} are calibrated <em>as a set</em>; a number scattered across three
 * constants in three packages cannot be re-checked when one of them moves.
 *
 * <h2>Where the schema lives</h2>
 *
 * <ul>
 *   <li>{@code db/migration/core/V2__core_schema.sql} — players, allowlist, faction reputation, rigs,
 *       the compute ledger, items, provenance chains, the public ledger, deployed miners, breach
 *       resolutions, and the server-scoped state row.
 *   <li>{@code db/migration/federation/V1001__federation_schema.sql} — validators, duels, flagged
 *       servers, federation peers. Applied only under the {@code federation} profile.
 * </ul>
 *
 * Both directories are scanned by one Flyway instance sharing one history table, so their version
 * ranges are disjoint on purpose: core takes V1, V2, …; federation takes V1001, V1002, …. Do not
 * renumber them.
 *
 * <h2>Testing</h2>
 *
 * Anything that touches SQL gets a Testcontainers integration test named {@code *IT.java}, extending
 * {@code PostgresIntegrationTestBase}, so failsafe runs it under {@code mvn -Pit verify} and the
 * default build stays Docker-free. Everything that does not touch SQL — mapping, vocabulary,
 * validation, arithmetic — gets a plain JUnit test that runs in {@code mvn verify}. The second kind
 * should be the bulk.
 */
package io.github.stoicswe.eyeandsickle.server.persistence;

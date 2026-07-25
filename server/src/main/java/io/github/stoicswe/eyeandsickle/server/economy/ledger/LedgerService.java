package io.github.stoicswe.eyeandsickle.server.economy.ledger;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.server.economy.account.Account;
import io.github.stoicswe.eyeandsickle.server.economy.account.AccountRepository;
import io.github.stoicswe.eyeandsickle.server.economy.account.UnknownPlayerException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Ethecoin balances and the public ledger — the authoritative money layer ({@code
 * docs/design/01-core-resources.md} §2).
 *
 * <h2>Two operations, and the wall between them: mint vs transfer</h2>
 *
 * The whole discipline of {@code docs/design/03-economy.md} §5 rule 3 is that faucets mint and
 * everything else moves. This service makes that a structural wall, not a convention:
 *
 * <ul>
 *   <li>{@link #mint(String, Ethecoin, Map)} is the <strong>only</strong> path that creates ethecoin.
 *       It has no payer, writes a {@link LedgerEntryType#MINING_REWARD} row, and is the one narrow,
 *       auditable place total supply grows. Mining and active-hacking payouts flow through here.
 *   <li>{@link #transfer(String, String, Ethecoin, LedgerEntryType, boolean, Map)} <strong>moves</strong>
 *       existing ethecoin and refuses the faucet type outright. Crack seizures and raid loot are
 *       transfers ({@code docs/design/04-mining.md} §5.1) — the ethecoin already exists on the host's
 *       machine; taking it moves it, it does not mint it.
 * </ul>
 *
 * A crack seizure that called {@code mint} would inflate the economy every time a miner was cracked;
 * putting minting behind a method that physically cannot name a payer, and transfers behind one that
 * physically rejects the faucet type, is what stops that by construction.
 *
 * <h2>Atomicity: the ledger row and the balance move are one act</h2>
 *
 * Every mutating method is {@code @Transactional}. The balance change and the {@code ledger_transactions}
 * row commit together or not at all — a ledger that could be written without the matching balance move
 * (or vice versa) would be evidence of a transfer that did not happen, or a transfer with no evidence.
 * The ledger table is append-only at the database level, so a half-written transfer cannot be tidied
 * up afterwards; the transaction boundary is what guarantees there is never one to tidy.
 *
 * <h2>Local and non-local counterparties</h2>
 *
 * A balance is materialised only for players on <em>this</em> server. The ledger has no foreign key to
 * {@code players} because a counterparty may be an NPC or a remote DID. So a transfer adjusts the
 * materialised balance of whichever side is a local player and simply records the flow for a side that
 * is not: an NPC host paying out a cracked buffer has no balance here to debit (the mining slice
 * debited the buffer); a purchase from an NPC vendor debits the local buyer and lets the ethecoin
 * leave the local economy as a sink. At least one party must be local, or this server has no stake in
 * the transaction and records nothing.
 *
 * <h2>This is an internal API, not a REST surface</h2>
 *
 * Nothing here is exposed to a client directly. Mints are triggered by mining/hacking payouts;
 * transfers by purchases, seizures, raids and splitter conversions — all server-side game events owned
 * by other slices, which call this service. The REST endpoints in this slice are read-only (balance,
 * ledger, storage, unlocks). Keeping spend off the wire is Invariant I14: the client asks the server to
 * act, it never moves money itself.
 */
@Service
public class LedgerService {

    private final AccountRepository accounts;
    private final LedgerRepository ledger;

    LedgerService(AccountRepository accounts, LedgerRepository ledger) {
        this.accounts = Objects.requireNonNull(accounts, "accounts");
        this.ledger = Objects.requireNonNull(ledger, "ledger");
    }

    /**
     * The materialised balance of a local player.
     *
     * @param did the player's DID
     * @return their balance
     * @throws UnknownPlayerException if no local player has that DID
     */
    public Ethecoin balanceOf(String did) {
        Objects.requireNonNull(did, "did");
        return accounts.findByDid(did)
                .orElseThrow(() -> new UnknownPlayerException(did))
                .balance();
    }

    /**
     * Runs an investigator query against the public ledger.
     *
     * @param query the client-chosen filters
     * @param viewerDid the authenticated DID asking, or {@code null} for an anonymous public
     *     investigator; decides which Dead Drops are visible
     * @return the matching rows, newest first
     */
    public List<LedgerTransaction> ledger(LedgerQuery query, String viewerDid) {
        return ledger.query(query, viewerDid);
    }

    /**
     * Mints new ethecoin into a local player's balance — the faucet.
     *
     * <p>The one operation that grows total supply. No payer, {@link LedgerEntryType#MINING_REWARD}
     * only. The recipient must be a local player: a faucet has to land in a real account, or it is
     * minting into the void.
     *
     * @param toDid the recipient's DID; must be a local player
     * @param amount the amount to mint; must be positive
     * @param memo investigator-readable context (which mining block, which contract), or {@code null}
     * @return the ledger row written
     * @throws UnknownPlayerException if the recipient is not a local player
     * @throws IllegalArgumentException if the amount is zero
     */
    @Transactional
    public LedgerTransaction mint(String toDid, Ethecoin amount, Map<String, Object> memo) {
        Objects.requireNonNull(toDid, "toDid");
        requirePositive(amount);

        Account recipient = lockOne(toDid).orElseThrow(() -> new UnknownPlayerException(toDid));
        accounts.writeBalance(recipient.playerId(), recipient.balance().plus(amount), recipient.rowVersion());

        LedgerTransaction tx = new LedgerTransaction(
                UUID.randomUUID(), null, toDid, amount, LedgerEntryType.MINING_REWARD, true, memo, Instant.now());
        ledger.append(tx);
        return tx;
    }

    /**
     * Moves existing ethecoin from one party to another and records it on the public ledger.
     *
     * <p>Refuses the faucet type: minting has exactly one door, and it is not this one. The payer, if a
     * local player, must be able to afford it — the affordability question is asked and answered here,
     * before the subtraction, never discovered from a negative balance.
     *
     * @param fromDid the payer's DID; balance-checked and debited only if a local player
     * @param toDid the payee's DID; credited only if a local player
     * @param amount the amount to move; must be positive
     * @param type the transaction type; must not be a {@link LedgerEntryType#isFaucet() faucet}
     * @param traceable {@code false} for a Dead Drop — recorded, but visible only to the counterparties
     * @param memo investigator-readable context, or {@code null}
     * @return the ledger row written
     * @throws IllegalArgumentException if the amount is zero, the type is a faucet, or payer equals
     *     payee
     * @throws UnknownPlayerException if neither party is a local player — this server has no stake
     * @throws InsufficientFundsException if a local payer cannot cover the amount
     */
    @Transactional
    public LedgerTransaction transfer(
            String fromDid,
            String toDid,
            Ethecoin amount,
            LedgerEntryType type,
            boolean traceable,
            Map<String, Object> memo) {
        Objects.requireNonNull(fromDid, "fromDid");
        Objects.requireNonNull(toDid, "toDid");
        Objects.requireNonNull(type, "type");
        requirePositive(amount);
        if (type.isFaucet()) {
            throw new IllegalArgumentException(
                    type + " is a faucet; use mint(...) for minting. transfer(...) moves existing ethecoin only "
                            + "(docs/design/03-economy.md §5 rule 3).");
        }
        if (fromDid.equals(toDid)) {
            throw new IllegalArgumentException(
                    "A transfer needs two distinct parties; from and to were both " + fromDid);
        }

        // Lock both rows in a consistent order (by player_id, inside AccountRepository) so two transfers
        // touching the same pair in opposite directions serialise rather than deadlock.
        List<Account> locked = accounts.lockForUpdate(List.of(fromDid, toDid));
        Optional<Account> payer = find(locked, fromDid);
        Optional<Account> payee = find(locked, toDid);
        if (payer.isEmpty() && payee.isEmpty()) {
            throw new UnknownPlayerException(fromDid);
        }

        // Debit the payer if they are local. A non-local payer (an NPC host paying out a cracked buffer)
        // has no balance here; the mining slice already moved the buffer, and this records the flow.
        payer.ifPresent(account -> {
            if (account.balance().compareTo(amount) < 0) {
                throw new InsufficientFundsException(fromDid, account.balance(), amount);
            }
            accounts.writeBalance(account.playerId(), account.balance().minus(amount), account.rowVersion());
        });

        // Credit the payee if they are local. A non-local payee (an NPC vendor on a purchase) is a sink:
        // the ethecoin leaves the local economy and only the ledger row remains.
        payee.ifPresent(account ->
                accounts.writeBalance(account.playerId(), account.balance().plus(amount), account.rowVersion()));

        LedgerTransaction tx =
                new LedgerTransaction(UUID.randomUUID(), fromDid, toDid, amount, type, traceable, memo, Instant.now());
        ledger.append(tx);
        return tx;
    }

    private Optional<Account> lockOne(String did) {
        return find(accounts.lockForUpdate(List.of(did)), did);
    }

    private static Optional<Account> find(List<Account> accounts, String did) {
        return accounts.stream().filter(a -> did.equals(a.did())).findFirst();
    }

    private static void requirePositive(Ethecoin amount) {
        Objects.requireNonNull(amount, "amount");
        if (amount.isZero()) {
            // Zero-value rows are noise on an evidence surface, and the schema refuses them
            // (ck_ledger_amount). Refuse here too, with a message about the operation rather than the
            // constraint.
            throw new IllegalArgumentException("A ledger transaction moves a positive amount; was zero");
        }
    }
}

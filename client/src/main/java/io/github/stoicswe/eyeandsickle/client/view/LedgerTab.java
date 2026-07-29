package io.github.stoicswe.eyeandsickle.client.view;

/**
 * The three views of money the LEDGER window offers.
 *
 * <h2>Why these are separate tabs and not one long panel</h2>
 *
 * They answer different questions with different scopes. {@link #CHAIN} is <em>everyone's</em> — a
 * block explorer, showing what the network is doing and what is queued next. {@link #LEDGER} is
 * <em>yours</em> — the audit trail for one balance. Stacked in one column the explorer pushed the
 * transaction table below the fold, so the readout a player opens this window to check was the one
 * they had to scroll for.
 *
 * <p>{@link #CONTRIBUTOR} is the join between them: the blocks on the chain that this rig's hashrate
 * went into. It is last because it is the only one that presupposes both — a reader has to know what
 * a block is and what a payout looks like before "you were 4.1% of this block" means anything.
 *
 * <h2>⚠ The address and balance stay outside the tabs</h2>
 *
 * They are the window's subject rather than one view of it: the address is what a player scans a
 * block's transactions for, and the balance is what the transaction table has to reconcile against.
 * Putting either behind a tab would mean switching away from the thing being compared, which is the
 * one thing {@code docs/design/04-mining.md} §3.1's audit needs both of at once.
 */
public enum LedgerTab {

    /**
     * The explorer: the chain's height and difficulty, the mempool, and recent blocks.
     *
     * <p>First because it is the wider context, and because a player who came here to check whether a
     * transaction confirmed finds the mempool before they find the row it belongs to.
     */
    CHAIN("CHAIN"),

    /** Your own transactions, newest first, each carrying the balance after it. */
    LEDGER("LEDGER"),

    /**
     * Every block this rig put hashrate into — solo wins and the blocks its pool found.
     *
     * <h2>Why this is not a filter on {@link #LEDGER}</h2>
     *
     * Most of its rows are not ledger rows. A pooled payout arrives every sixty seconds as one
     * settlement covering however many blocks the pool found in that window, and under pay-per-share
     * it is not tied to any block at all — so a ledger filtered to "mining" answers "what was I paid"
     * and cannot answer "which blocks was I in". They are genuinely different lists over genuinely
     * different keys, and it is the second question that makes a rig's share of the network checkable
     * against what it actually won.
     */
    CONTRIBUTOR("CONTRIBUTOR");

    private final String label;

    LedgerTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /**
     * What this tab shows, for a screen reader.
     *
     * <p>Here rather than as a ternary at the call site, which is what it was: a two-branch
     * conditional silently gives a third tab the second one's description, and nothing anywhere
     * reports it — the chip renders, reads out wrong, and only a screen-reader user finds out.
     */
    public String description() {
        return switch (this) {
            case CHAIN -> "Show the chain: height, difficulty, the mempool and recent blocks.";
            case LEDGER -> "Show your own transactions, newest first.";
            case CONTRIBUTOR -> "Show every block your rig contributed hashrate to, newest first.";
        };
    }

    /**
     * Brackets, not colour.
     *
     * <p>{@code docs/design/ui-design-language.md} §4.4 — the selected state survives greyscale and a
     * screen reader, which colour alone does not. Same control the rig monitor's tabs draw, because
     * two tab strips in one deck that indicated selection differently would be two conventions.
     */
    public String control(LedgerTab active) {
        return this == active ? "[ " + label + " ]" : "  " + label + "  ";
    }
}

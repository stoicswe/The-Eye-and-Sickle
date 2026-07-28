package io.github.stoicswe.eyeandsickle.client.view;

/**
 * The two views of money the LEDGER window offers.
 *
 * <h2>Why these are separate tabs and not one long panel</h2>
 *
 * They answer different questions with different scopes. {@link #CHAIN} is <em>everyone's</em> — a
 * block explorer, showing what the network is doing and what is queued next. {@link #LEDGER} is
 * <em>yours</em> — the audit trail for one balance. Stacked in one column the explorer pushed the
 * transaction table below the fold, so the readout a player opens this window to check was the one
 * they had to scroll for.
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
    LEDGER("LEDGER");

    private final String label;

    LedgerTab(String label) {
        this.label = label;
    }

    public String label() {
        return label;
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

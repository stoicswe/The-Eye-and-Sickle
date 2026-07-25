package io.github.stoicswe.eyeandsickle.server.economy.gate;

/**
 * The seam to schematic ownership and vault-progression state, which another slice owns.
 *
 * <h2>Why this is a port and not a table read here</h2>
 *
 * The economy slice evaluates the schematic gate ({@code docs/design/02-unlock-gates.md} §2.2) and
 * computes vault capacity, and both need to know what a holder has <em>found</em>. But schematics are
 * "found or earned at designer-paced milestones" — exploration rewards, story beats, deep-infrastructure
 * objectives ({@code docs/design/11-rig-infrastructure.md}) — and the store that records them belongs
 * to the rig-infrastructure / progression slice, not to this one. The Established spine has no
 * schematic-ownership table yet ({@code docs/design/15-open-questions.md}), so rather than invent one
 * (which would silently promote a proposal to a decision) the economy slice depends on this narrow
 * interface and lets the owning slice supply the implementation.
 *
 * <h2>The default is deny-all, on purpose</h2>
 *
 * {@link EconomyConfiguration} registers a {@link Denying} bean only if no real one exists. Until the
 * progression slice provides one, schematic-gated offerings evaluate as <em>not satisfied</em> and the
 * vault sits at its base capacity. That is the safe direction to fail (Invariant I14): a missing
 * dependency withholds an unlock, it never grants one. A permissive default would hand out
 * schematic-gated capability to everyone the moment the real store was late.
 */
public interface SchematicHoldings {

    /**
     * Whether a holder possesses a schematic.
     *
     * @param holderDid the holder's DID
     * @param schematicId the schematic identifier, as named by a {@link
     *     GateCondition.SchematicRequirement}
     * @return {@code true} if the holder has it
     */
    boolean holdsSchematic(String holderDid, String schematicId);

    /**
     * How many Cold Storage Expansions a holder has installed — the input to vault capacity ({@code
     * docs/design/01-core-resources.md} §6).
     *
     * <p>Zero means a fresh vault at base capacity. This is schematic-derived state, never a function
     * of ethecoin, which is what keeps vault capacity unpurchasable (Invariant I12).
     *
     * @param holderDid the holder's DID
     * @return the expansion level; never negative
     */
    int vaultExpansionLevel(String holderDid);

    /**
     * The safe default: nobody holds anything, every vault is at base capacity.
     *
     * <p>Registered by {@link EconomyConfiguration} only when the progression slice has not supplied a
     * real implementation, so the server boots and the economy slice's other gates work while schematic
     * ownership is still unimplemented.
     */
    final class Denying implements SchematicHoldings {

        @Override
        public boolean holdsSchematic(String holderDid, String schematicId) {
            return false;
        }

        @Override
        public int vaultExpansionLevel(String holderDid) {
            return 0;
        }
    }
}

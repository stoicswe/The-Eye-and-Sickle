package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * The default {@link MigrationItemChains}: reads an account's items and their chains straight out of the
 * items slice's tables over {@code JdbcClient}.
 *
 * <h2>Read-only, verbatim, genesis-first</h2>
 *
 * Export must not mutate (the same read serves a backup, §5), and it must reproduce each signed envelope
 * exactly. So it selects {@code items.item_id} for the holder and, per item, the {@code
 * provenance_records.envelope} documents ordered by {@code chain_depth} — the verbatim JSON text the
 * verifier re-checks at the destination. It is deliberately a plain projection, never re-serializing an
 * envelope, because re-serialization could change the bytes a signature covers.
 *
 * <p>The SQL reaches across into the items slice's tables rather than through its {@code ItemStore}
 * interface, which exposes no "list by holder" read; keeping the query here — read-only, and confined to
 * this slice's own file — avoids widening another slice's persistence API for one caller. The holder key
 * is a DID, so this returns the <em>account's</em> items (see {@link MigrationItemChains} on the
 * per-character attribution gap).
 */
@Component
class JdbcMigrationItemChains implements MigrationItemChains {

    private final JdbcClient jdbcClient;

    JdbcMigrationItemChains(JdbcClient jdbcClient) {
        this.jdbcClient = Objects.requireNonNull(jdbcClient, "jdbcClient");
    }

    @Override
    public List<ItemChain> chainsForHolder(CharacterDid holder) {
        Objects.requireNonNull(holder, "holder");
        List<UUID> itemIds = jdbcClient
                .sql("SELECT item_id FROM items WHERE holder_did = :holder ORDER BY item_id")
                .param("holder", holder.value())
                .query((rs, rowNum) -> rs.getObject("item_id", UUID.class))
                .list();

        List<ItemChain> chains = new ArrayList<>(itemIds.size());
        for (UUID itemId : itemIds) {
            List<String> envelopes = jdbcClient
                    .sql("SELECT envelope FROM provenance_records WHERE item_id = :itemId ORDER BY chain_depth")
                    .param("itemId", itemId)
                    .query(String.class)
                    .list();
            // An item row without any provenance records should not exist (ingress and minting always
            // write the chain with the item), but if one did, skip it rather than emit an empty chain the
            // wire type would reject — the item simply is not exportable.
            if (!envelopes.isEmpty()) {
                chains.add(new ItemChain(itemId, envelopes));
            }
        }
        return chains;
    }
}

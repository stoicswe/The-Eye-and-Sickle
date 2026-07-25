package io.github.stoicswe.eyeandsickle.server.migration;

import io.github.stoicswe.eyeandsickle.protocol.game.CharacterDid;
import io.github.stoicswe.eyeandsickle.protocol.game.CharacterMigrationBundle.ItemChain;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * An in-memory {@link MigrationItemChains} for the export unit tests: whatever chains a test seeds for a
 * holder DID, read back verbatim. No database, no items table.
 */
final class FakeMigrationItemChains implements MigrationItemChains {

    private final Map<String, List<ItemChain>> byHolder = new ConcurrentHashMap<>();

    /** Seeds the chains an account will export. */
    void put(CharacterDid holder, List<ItemChain> chains) {
        byHolder.put(holder.value(), chains);
    }

    @Override
    public List<ItemChain> chainsForHolder(CharacterDid holder) {
        return byHolder.getOrDefault(holder.value(), List.of());
    }
}

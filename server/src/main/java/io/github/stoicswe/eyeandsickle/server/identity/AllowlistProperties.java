package io.github.stoicswe.eyeandsickle.server.identity;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The operator's allowlist configuration, bound from {@code eyeandsickle.allowlist} in
 * {@code application.yml}.
 *
 * <h2>Closed by default ({@code docs/architecture/03-server-and-federation.md} §1)</h2>
 *
 * {@code enabled} defaults to {@code true} and the DID list defaults to empty, which together mean a
 * freshly-installed server admits <em>nobody</em> until the operator names someone. That is deliberate:
 * a home server holds real, losable player state, so the safe default is private, not open.
 *
 * <p>Setting {@code enabled} to {@code false} is an explicit operator decision to run <em>without</em>
 * an allowlist — every authenticated DID may join. It never means "no configuration, so let everyone
 * in"; it means the operator chose openness on purpose. The distinction matters because the dangerous
 * misreading — an unset value silently opening the server — is exactly the one the closed default
 * rules out.
 *
 * <h2>These are seeds, not the source of truth</h2>
 *
 * The authoritative allowlist is the {@code allowlist_entries} table, so an operator can add and revoke
 * without a restart. This configuration is the <em>initial</em> membership: {@link AllowlistSeeder}
 * copies {@link #dids()} into the table at startup. Editing the config later adds new seeds but does
 * not un-revoke or delete what the table already holds — the runtime table wins, which is the whole
 * point of having one.
 *
 * @param enabled whether the allowlist is enforced; {@code true} (closed, the default) unless the
 *     operator opts out
 * @param dids the DIDs to seed, as configured. A single comma-separated environment value binds here as
 *     a multi-element list via Spring's relaxed binding; {@link #parsedDids()} also splits defensively.
 */
@ConfigurationProperties(prefix = "eyeandsickle.allowlist")
public record AllowlistProperties(Boolean enabled, List<String> dids) {

    public AllowlistProperties {
        // Closed by default: an unset `enabled` is treated as true, never as "open". A boxed Boolean is
        // used precisely so "not configured" is distinguishable and can be defaulted to the safe value.
        enabled = enabled == null || enabled;
        dids = dids == null ? List.of() : List.copyOf(dids);
    }

    /**
     * Whether the allowlist is enforced.
     *
     * @return {@code true} if only listed DIDs may join (the default), {@code false} if the operator has
     *     opted to run open
     */
    public boolean isEnforced() {
        return enabled;
    }

    /**
     * The configured seed DIDs, parsed and validated.
     *
     * <p>Each entry is additionally split on commas, so a value supplied as one comma-joined string —
     * the shape an environment variable usually takes — yields one {@link Did} per identity regardless
     * of how the binder presented it. A malformed DID fails loudly here rather than being silently
     * dropped, because a typo in the one list that decides who may play is worth stopping startup over.
     *
     * @return the seed DIDs
     * @throws IllegalArgumentException if any configured value is not a well-shaped DID
     */
    public List<Did> parsedDids() {
        List<Did> parsed = new ArrayList<>();
        for (String raw : dids) {
            if (raw == null) {
                continue;
            }
            for (String piece : Arrays.stream(raw.split(",")).map(String::strip).toList()) {
                if (!piece.isEmpty()) {
                    parsed.add(Did.of(piece));
                }
            }
        }
        return List.copyOf(parsed);
    }
}

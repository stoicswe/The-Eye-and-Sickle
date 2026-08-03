package io.github.stoicswe.eyeandsickle.server.lan;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Which mode this server runs in, bound from {@code eyeandsickle.mode}.
 *
 * @param mode federated or LAN; ⚠ defaults to {@link ServerMode#FEDERATED} because that is the mode
 *     with the security machinery switched on. A typo in the property name must not silently produce
 *     a server with no authentication.
 * @param allowPublicAddress escape hatch for the {@link LanAddressInterlock}. ⚠ Off by default and
 *     should stay off: LAN mode's entire trust model is "the network is the boundary", which is false
 *     on a public address. It exists for a container network the interlock cannot introspect, and it
 *     is logged loudly when used.
 */
@ConfigurationProperties(prefix = "eyeandsickle")
public record LanProperties(
        @DefaultValue("FEDERATED") ServerMode mode,
        @DefaultValue("false") boolean allowPublicAddress) {}

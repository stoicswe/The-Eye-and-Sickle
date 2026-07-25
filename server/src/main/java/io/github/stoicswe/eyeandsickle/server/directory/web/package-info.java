/**
 * REST edge over the character directory: publish signed home records, resolve a DID's characters.
 *
 * <p>Thin controllers over {@link io.github.stoicswe.eyeandsickle.server.directory.CharacterDirectoryService}
 * — the client renders these; it never decides them (Invariant I14). Registered only on federating
 * servers ({@code eyeandsickle.federation.enabled=true}), because directory data exists only there
 * ({@code docs/architecture/09-player-state-portability.md} §4). Untrusted input is bounded and every
 * refusal is a typed fault surfaced as a status code, never a stack trace.
 */
package io.github.stoicswe.eyeandsickle.server.directory.web;

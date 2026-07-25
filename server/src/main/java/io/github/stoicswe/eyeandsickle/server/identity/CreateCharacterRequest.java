package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * The body of a create-character request.
 *
 * <p>The only client-supplied field is the display {@code handle}, and it is optional: in a real
 * deployment the character's handle is the account's current AT Proto handle, which the request filter
 * supplies from the authenticated sign-in rather than trusting from the body. A {@code null} handle is
 * legitimate — the provider may resolve none — and the character is created without one. Nothing
 * authoritative is taken from here: the account identity comes from the authenticated principal, and the
 * slot and status are assigned by the server (Invariant I14).
 *
 * @param handle the display handle to give the new character, or {@code null}
 */
public record CreateCharacterRequest(String handle) {}

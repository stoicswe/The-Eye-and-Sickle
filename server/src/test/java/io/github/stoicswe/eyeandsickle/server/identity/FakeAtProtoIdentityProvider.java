package io.github.stoicswe.eyeandsickle.server.identity;

/**
 * A hand-written {@link AtProtoIdentityProvider} for the sign-in flow tests.
 *
 * <p>It is either configured to <em>return</em> a verified identity (authentication succeeded) or to
 * <em>throw</em> one (authentication failed / unavailable), and it records the last credentials it was
 * handed. Being an interface, this seam is trivial to fake by hand — no PDS, no network — which is the
 * whole reason {@link AtProtoIdentityProvider} is an interface.
 */
final class FakeAtProtoIdentityProvider implements AtProtoIdentityProvider {

    private ResolvedIdentity toReturn;
    private RuntimeException toThrow;
    private SignInCredentials lastSeen;
    private int calls = 0;

    static FakeAtProtoIdentityProvider returning(ResolvedIdentity identity) {
        FakeAtProtoIdentityProvider fake = new FakeAtProtoIdentityProvider();
        fake.toReturn = identity;
        return fake;
    }

    static FakeAtProtoIdentityProvider failing(RuntimeException failure) {
        FakeAtProtoIdentityProvider fake = new FakeAtProtoIdentityProvider();
        fake.toThrow = failure;
        return fake;
    }

    /** Reconfigures what the next authentication returns — used to model a returning sign-in. */
    void nowReturns(ResolvedIdentity identity) {
        this.toReturn = identity;
        this.toThrow = null;
    }

    @Override
    public ResolvedIdentity authenticate(SignInCredentials credentials) {
        calls++;
        this.lastSeen = credentials;
        if (toThrow != null) {
            throw toThrow;
        }
        return toReturn;
    }

    SignInCredentials lastSeen() {
        return lastSeen;
    }

    int calls() {
        return calls;
    }
}

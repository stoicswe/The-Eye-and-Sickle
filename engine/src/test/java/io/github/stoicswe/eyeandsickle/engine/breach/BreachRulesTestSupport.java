package io.github.stoicswe.eyeandsickle.engine.breach;

import io.github.stoicswe.eyeandsickle.engine.state.GameSave;
import java.lang.reflect.Method;
import java.time.Instant;

/** Reaches {@code BreachRules}' private spike so a test can check the crack exclusion directly. */
final class BreachRulesTestSupport {

    private BreachRulesTestSupport() {}

    static void spikeAsCrack(GameSave save, Instant now) {
        try {
            Method m =
                    BreachRules.class.getDeclaredMethod("spikeOnAbandon", GameSave.class, boolean.class, Instant.class);
            m.setAccessible(true);
            m.invoke(null, save, true, now);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

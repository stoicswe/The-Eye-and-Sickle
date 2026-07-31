package io.github.stoicswe.eyeandsickle.solo.breach;

import io.github.stoicswe.eyeandsickle.solo.state.SoloSave;
import java.lang.reflect.Method;
import java.time.Instant;

/** Reaches {@code BreachRules}' private spike so a test can check the crack exclusion directly. */
final class BreachRulesTestSupport {

    private BreachRulesTestSupport() {}

    static void spikeAsCrack(SoloSave save, Instant now) {
        try {
            Method m =
                    BreachRules.class.getDeclaredMethod("spikeOnAbandon", SoloSave.class, boolean.class, Instant.class);
            m.setAccessible(true);
            m.invoke(null, save, true, now);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}

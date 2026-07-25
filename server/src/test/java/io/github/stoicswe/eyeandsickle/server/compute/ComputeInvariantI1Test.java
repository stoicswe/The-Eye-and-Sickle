package io.github.stoicswe.eyeandsickle.server.compute;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.game.Cycles;
import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Invariant I1, made mechanical: <em>compute is never purchasable with ethecoin.</em>
 *
 * <p>The compute slice enforces this structurally, "by omission" ({@code package-info}): there is no
 * type, method, field or constructor in the package through which money could ask for capacity. This
 * test proves that omission is real rather than aspirational — it reflects over every class in the
 * slice and asserts that {@link Ethecoin} appears in no signature and on no field. If someone ever adds
 * a {@code raiseCeiling(UUID rig, Ethecoin paid)} — the exact shape the whole economy is built to
 * forbid — this test goes red the moment the code compiles.
 */
class ComputeInvariantI1Test {

    /** Every class the compute slice owns; the reflection sweep covers all of them. */
    private static final List<Class<?>> SLICE = List.of(
            ComputeLedgerService.class,
            ComputeLedgerRepository.class,
            Rig.class,
            RigComputeReconciliation.class,
            ComputeBudgetAssembler.class,
            ComputeProperties.class,
            AllocateComputeRequest.class,
            ComputeMonitorController.class,
            AllocationDisclosurePolicy.class,
            DiscloseAllAllocations.class,
            ConsumptionModel.class,
            LoadFactorThermalRecovery.class,
            ThermalRecoveryStrategy.class,
            InsufficientComputeException.class,
            RigNotFoundException.class,
            AllocationNotFoundException.class,
            ComputeConfiguration.class);

    @Test
    @DisplayName("no method in the compute slice accepts or returns ethecoin")
    void noMethodTouchesEthecoin() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : SLICE) {
            for (Method method : type.getDeclaredMethods()) {
                if (method.getReturnType() == Ethecoin.class) {
                    offenders.add(type.getSimpleName() + "#" + method.getName() + " returns Ethecoin");
                }
                if (referencesEthecoin(method.getParameterTypes())) {
                    offenders.add(type.getSimpleName() + "#" + method.getName() + " takes Ethecoin");
                }
            }
        }
        // A cycles-taking method is the ONLY way capacity is expressed; an ethecoin one would be the bug.
        assertThat(offenders)
                .as("Invariant I1: no ethecoin in a compute-slice method signature")
                .isEmpty();
    }

    @Test
    @DisplayName("no constructor in the compute slice accepts ethecoin")
    void noConstructorTakesEthecoin() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : SLICE) {
            for (Constructor<?> constructor : type.getDeclaredConstructors()) {
                if (referencesEthecoin(constructor.getParameterTypes())) {
                    offenders.add(type.getSimpleName() + " constructor takes Ethecoin");
                }
            }
        }
        assertThat(offenders)
                .as("Invariant I1: no ethecoin in a compute-slice constructor")
                .isEmpty();
    }

    @Test
    @DisplayName("no field in the compute slice stores ethecoin")
    void noFieldStoresEthecoin() {
        List<String> offenders = new ArrayList<>();
        for (Class<?> type : SLICE) {
            for (Field field : type.getDeclaredFields()) {
                if (field.getType() == Ethecoin.class) {
                    offenders.add(type.getSimpleName() + "#" + field.getName());
                }
            }
        }
        assertThat(offenders)
                .as("Invariant I1: no ethecoin field in the compute slice")
                .isEmpty();
    }

    @Test
    @DisplayName("capacity is expressed in Cycles — the sweep is meaningful because both types are real")
    void capacityIsCycles() {
        // Guard against a false-green sweep: prove Ethecoin and Cycles are distinct, loadable types, and
        // that the slice really does traffic in Cycles (so 'no Ethecoin' is a live constraint, not a
        // vacuous one over a package that mentions neither).
        assertThat(Ethecoin.class).isNotEqualTo(Cycles.class);
        assertThat(Rig.class.getRecordComponents())
                .anySatisfy(component -> assertThat(component.getType()).isEqualTo(Cycles.class));
    }

    private static boolean referencesEthecoin(Class<?>[] types) {
        for (Class<?> type : types) {
            if (type == Ethecoin.class) {
                return true;
            }
        }
        return false;
    }
}

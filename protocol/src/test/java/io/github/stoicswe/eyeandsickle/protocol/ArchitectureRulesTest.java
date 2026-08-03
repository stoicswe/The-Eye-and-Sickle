package io.github.stoicswe.eyeandsickle.protocol;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Machine-checks this module's charter.
 *
 * <p>{@code CLAUDE.md} and the package docs say what belongs in {@code protocol} and what does not.
 * Prose alone does not hold: there is constant, reasonable-sounding pressure to move "just the gate
 * check" or "just the compute recovery curve" in here so the client can predict without a round trip.
 * Each such move is individually defensible and collectively fatal to Invariant I14 — the client must
 * never be authoritative over anything a cheater would forge.
 *
 * <p>These rules are the thing that says no when nobody is reviewing carefully.
 *
 * <p>Written as plain JUnit tests rather than with ArchUnit's {@code @AnalyzeClasses} extension, so
 * the module depends on ArchUnit core only and not on a second JUnit Platform TestEngine.
 */
class ArchitectureRulesTest {

    private static final String ROOT = "io.github.stoicswe.eyeandsickle.protocol";

    private static final JavaClasses PROTOCOL_CLASSES = new ClassFileImporter()
            .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages(ROOT);

    @Test
    @DisplayName("the rules below are actually looking at classes")
    void rulesAreNotVacuous() {
        // Every rule here uses allowEmptyShould(true), which means a rule whose `that()` clause
        // matches nothing passes silently. If the import ever breaks — a renamed root package, a
        // build that runs before compilation — the whole suite would go green while checking
        // nothing at all. This test is the tripwire for that.
        assertThat(PROTOCOL_CLASSES).isNotEmpty();
        assertThat(PROTOCOL_CLASSES.stream().map(JavaClass::getPackageName).distinct())
                .contains(
                        ROOT + ".crypto", ROOT + ".provenance", ROOT + ".game", ROOT + ".channel", ROOT + ".identity");
    }

    @Test
    @DisplayName("provenance must not depend on the game vocabulary")
    void provenanceDoesNotDependOnGame() {
        // A signed payload must stay self-contained and independently verifiable years later, by
        // someone holding only the record and a public key. The moment a payload can reference a
        // live game type, verification starts depending on game state.
        noClasses()
                .that()
                .resideInAPackage("..protocol.provenance..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..protocol.game..")
                .because("a signed provenance payload must not reference live game values "
                        + "(docs/architecture/04-item-provenance.md §6)")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("crypto is the bottom layer and depends on nothing above it")
    void cryptoDependsOnNothingAboveIt() {
        noClasses()
                .that()
                .resideInAPackage("..protocol.crypto..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "..protocol.provenance..", "..protocol.game..", "..protocol.channel..", "..protocol.identity..")
                .because("crypto is the shared floor: game -> provenance -> crypto <- channel, "
                        + "with identity sitting above crypto too")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("identity resolves who someone is, never what they own")
    void identityDoesNotDependOnGame() {
        // identity was admitted to this module's charter on 2026-08-02 (see package-info) because
        // the provenance verifier here has always been missing its other half — turning a `kid` into
        // a key. That argument holds only while identity stays identity. The moment resolving a DID
        // can see a compute budget or a faction reputation, a verification failure becomes a function
        // of game state, and "this signature is invalid" starts meaning something different
        // depending on who is asking.
        noClasses()
                .that()
                .resideInAPackage("..protocol.identity..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..protocol.game..", "..protocol.channel..")
                .because("identity answers who, not what-they-own "
                        + "(docs/architecture/10-oauth-and-did-resolution.md)")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("identity is the ONLY package here that may open a socket")
    void networkIoIsConfinedToIdentity() {
        // Before identity landed, this module did no I/O at all, and the reasons for that austerity
        // are unchanged: it is a jlink candidate and it is shared by a JavaFX client and a Spring
        // Boot server. Admitting one package that fetches is a deliberate, argued exception; it must
        // not become a precedent that the wire types quietly follow. A `game` record that phones home
        // to fill in a field would be authoritative-state-by-the-back-door, which is I14.
        noClasses()
                .that()
                .resideOutsideOfPackages("..protocol.identity..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("java.net.http..", "javax.naming..", "javax.net.ssl..")
                .because("network I/O in protocol is confined to identity "
                        + "(protocol/package-info.java, amended 2026-08-02)")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("the transport channel knows nothing about what it carries")
    void channelDoesNotDependOnGameOrProvenance() {
        // The channel defends bytes in flight for the length of a connection. Provenance defends
        // items across years and across servers. Different threat models, different lifetimes — and
        // a transport that can see the payload types is a transport that will eventually grow
        // "just a small" special case for one of them in its framing.
        noClasses()
                .that()
                .resideInAPackage("..protocol.channel..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..protocol.game..", "..protocol.provenance..")
                .because("an encrypted transport must be agnostic to its payload "
                        + "(docs/architecture/07-transport-security.md)")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("no framework may reach the shared module")
    void noFrameworkDependencies() {
        noClasses()
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "javafx..",
                        "jakarta.persistence..",
                        "org.hibernate..",
                        "com.fasterxml.jackson..") // Jackson 2; this project is on Jackson 3
                .because("protocol is shared by a JavaFX client and a Spring Boot server and must "
                        + "stay neutral between them (see protocol/pom.xml enforcer rules)")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("no game rules live here — only wire types and the verifier")
    void noRuleEvaluatingTypes() {
        // Deliberately a name check. It will not stop a determined contributor who names their
        // class something else, but it stops the reflexive `GateEvaluationService` that would
        // otherwise arrive in a hurry on a Friday, and it makes the charter's intent legible at
        // review time.
        noClasses()
                .that()
                .resideInAPackage(ROOT + "..")
                .should()
                .haveSimpleNameEndingWith("Service")
                .orShould()
                .haveSimpleNameEndingWith("Policy")
                .orShould()
                .haveSimpleNameEndingWith("Evaluator")
                .orShould()
                .haveSimpleNameEndingWith("Repository")
                .orShould()
                .haveSimpleNameEndingWith("Controller")
                .because("rule evaluation and persistence are the server's job (Invariant I14); "
                        + "protocol holds wire types and the provenance verifier")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    /**
     * The idioms that actually introduce ambient time or randomness. Banning {@code
     * java.util.Random} and {@code java.time.Clock} — as an earlier version of this test did — bans
     * the two things nobody writes, while {@code Instant.now()} and {@code UUID.randomUUID()} walk
     * straight past. This predicate names the real ones.
     */
    private static final DescribedPredicate<JavaMethodCall> AMBIENT_TIME_OR_RANDOMNESS =
            new DescribedPredicate<>("a call to an ambient clock or randomness source") {
                @Override
                public boolean test(JavaMethodCall call) {
                    String owner = call.getTargetOwner().getFullName();
                    String method = call.getTarget().getName();
                    return switch (owner) {
                        case "java.time.Instant",
                                "java.time.LocalDate",
                                "java.time.LocalDateTime",
                                "java.time.LocalTime",
                                "java.time.OffsetDateTime",
                                "java.time.ZonedDateTime",
                                "java.time.Year",
                                "java.time.YearMonth",
                                "java.time.Clock" ->
                            method.equals("now") || method.equals("systemUTC") || method.equals("systemDefaultZone");
                        case "java.lang.System" -> method.equals("currentTimeMillis") || method.equals("nanoTime");
                        case "java.util.UUID" -> method.equals("randomUUID");
                        case "java.lang.Math" -> method.equals("random");
                        case "java.util.Random",
                                "java.security.SecureRandom",
                                "java.util.concurrent.ThreadLocalRandom" -> true;
                        default -> false;
                    };
                }
            };

    @Test
    @DisplayName("provenance and game read no clock and no RNG — signed bytes must be reproducible")
    void noAmbientTimeOrRandomness() {
        // A payload's timestamp and nonce are INPUTS, chosen by the author of the record and passed
        // in. If these packages could read the clock or generate randomness themselves, the same
        // logical record would serialize differently on two machines and signatures would stop
        // reproducing across servers — which, in a federation, looks exactly like cheating.
        //
        // Scoped to provenance and game deliberately. `crypto` and `channel` MUST generate
        // randomness — ephemeral keys and nonces are their whole job — so a module-wide ban would be
        // wrong rather than merely strict.
        noClasses()
                .that()
                .resideInAnyPackage("..protocol.provenance..", "..protocol.game..")
                .should()
                .callMethodWhere(AMBIENT_TIME_OR_RANDOMNESS)
                .because("timestamp and nonce are inputs to a signed record, never ambient state")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }

    @Test
    @DisplayName("there is no generic Reputation type")
    void noGenericReputationType() {
        // factionReputation (a player's Eye/Sickle standing) and validatorReputation (a federated
        // server's trust score) are unrelated. A generic `Reputation` is the shape a future merge
        // of the two would take, so the type simply must not exist.
        noClasses()
                .should()
                .haveSimpleName("Reputation")
                .because("factionReputation and validatorReputation are different things and must "
                        + "never share a field, column or type (docs/design/glossary.md)")
                .allowEmptyShould(true)
                .check(PROTOCOL_CLASSES);
    }
}

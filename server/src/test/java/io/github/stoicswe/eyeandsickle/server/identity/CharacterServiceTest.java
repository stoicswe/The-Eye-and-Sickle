package io.github.stoicswe.eyeandsickle.server.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.stoicswe.eyeandsickle.protocol.game.Ethecoin;
import io.github.stoicswe.eyeandsickle.protocol.game.Faction;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.DevSignin;
import io.github.stoicswe.eyeandsickle.server.identity.IdentityProperties.Operator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The character lifecycle and the slot policy (09 §1-§2, §6.1), with fakes. The failures are tested
 * hardest: the {@code (max+1)}-th character is refused, local play is uncapped, the cap consults the
 * recognized-count seam rather than raw local rows, slot assignment dodges retained shells, terminal
 * characters cannot be selected or transitioned again (no double-play), and one account cannot act on
 * another's character.
 */
class CharacterServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-24T10:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final Did DID = Did.of("did:plc:aaaaaaaaaaaaaaaaaaaaaaaa");
    private static final Did OTHER = Did.of("did:plc:bbbbbbbbbbbbbbbbbbbbbbbb");

    private static IdentityProperties identityProps() {
        return new IdentityProperties(new Operator(null, null, null), new DevSignin(false), Duration.ofHours(24), null);
    }

    private record Harness(
            CharacterService service, FakePlayerRepository players, InMemoryPlayerSessionStore sessions) {}

    /** Wires a service with the given cap and recognized-count seam. */
    private static Harness harness(int maxCharacters, RecognizedCharacterCount recognized) {
        FakePlayerRepository players = new FakePlayerRepository();
        InMemoryPlayerSessionStore sessions = new InMemoryPlayerSessionStore(CLOCK);
        CharacterService service = new CharacterService(
                players, new CharacterProperties(maxCharacters), recognized, sessions, identityProps(), CLOCK);
        return new Harness(service, players, sessions);
    }

    /** The default single-server count: this server's own active rows. */
    private static Harness harness(int maxCharacters) {
        FakePlayerRepository players = new FakePlayerRepository();
        InMemoryPlayerSessionStore sessions = new InMemoryPlayerSessionStore(CLOCK);
        CharacterService service = new CharacterService(
                players,
                new CharacterProperties(maxCharacters),
                new LocalRecognizedCharacterCount(players),
                sessions,
                identityProps(),
                CLOCK);
        return new Harness(service, players, sessions);
    }

    private static Player terminal(Did did, int slot, CharacterStatus status) {
        return new Player(
                UUID.randomUUID(), did, slot, "old", status, Faction.NONE, Heat.ZERO, Ethecoin.ZERO, NOW, NOW, 0);
    }

    // ------------------------------------------------------------------ create + cap

    @Nested
    @DisplayName("create and the cap")
    class CreateAndCap {

        @Test
        @DisplayName("a fresh account's first character takes slot 1, the next slot 2")
        void assignsLowestFreeSlot() {
            Harness h = harness(3);

            Player first = h.service().createCharacter(DID, "alice");
            Player second = h.service().createCharacter(DID, "alice");

            assertThat(first.slot()).isEqualTo(1);
            assertThat(second.slot()).isEqualTo(2);
            assertThat(first.status()).isEqualTo(CharacterStatus.ACTIVE);
        }

        @Test
        @DisplayName("the (max+1)-th character is refused")
        void refusesBeyondCap() {
            Harness h = harness(3);
            h.service().createCharacter(DID, "alice");
            h.service().createCharacter(DID, "alice");
            h.service().createCharacter(DID, "alice");

            assertThatThrownBy(() -> h.service().createCharacter(DID, "alice"))
                    .isInstanceOf(CharacterSlotExceededException.class);
            // The refusal wrote nothing beyond the three legitimate creates.
            assertThat(h.players().createCalls()).hasSize(3);
        }

        @Test
        @DisplayName("the cap consults the recognized-count seam, not raw local rows")
        void capUsesRecognizedCount() {
            // The account holds nothing on THIS server, but the federation directory recognizes 3
            // elsewhere. An honest server refuses the 4th anyway (09 §2) — the seam is authoritative for
            // the cap, not the local row count.
            Harness h = harness(3, accountDid -> 3);

            assertThatThrownBy(() -> h.service().createCharacter(DID, "alice"))
                    .isInstanceOf(CharacterSlotExceededException.class);
            assertThat(h.players().createCalls()).isEmpty();
        }

        @Test
        @DisplayName("slot assignment dodges a slot still held by a migrated/retired shell")
        void dodgesRetainedShellSlot() {
            // A migrated shell keeps slot 1 (09 §6.1); it does not count against the (active) cap, but its
            // slot number is still held, so a new character must take slot 2.
            Harness h = harness(3);
            h.players().put(terminal(DID, 1, CharacterStatus.MIGRATED));

            Player created = h.service().createCharacter(DID, "alice");

            assertThat(created.slot()).isEqualTo(2);
        }

        @Test
        @DisplayName("local, DID-less characters are uncapped")
        void localPlayIsUncapped() {
            // Local play is outside the account/slot system entirely (09 §1): no cap, ever.
            Harness h = harness(3);

            for (int i = 0; i < 6; i++) {
                Player local = h.service().createLocalCharacter("solo");
                assertThat(local.isLocal()).isTrue();
                assertThat(local.slot()).isNull();
            }
            assertThat(h.players().createLocalCalls()).hasSize(6);
        }
    }

    // ------------------------------------------------------------------ select

    @Nested
    @DisplayName("select")
    class Select {

        @Test
        @DisplayName("selecting an active character opens a session bound to it")
        void opensSession() {
            Harness h = harness(3);
            Player character = h.service().createCharacter(DID, "alice");

            PlayerSession session = h.service().selectCharacter(DID, character.playerId());

            assertThat(session.playerId()).isEqualTo(character.playerId());
            assertThat(session.did()).isEqualTo(DID);
            // The selected character's DID is available to callers as the actor game state stamps (09 §9).
            assertThat(session.characterDid()).isEqualTo(character.characterDid());
            assertThat(session.characterDid().accountDid()).isEqualTo(DID.value());
            assertThat(session.characterDid().slot()).isEqualTo(character.slot());
            assertThat(h.sessions().resolve(session.token())).isPresent();
        }

        @Test
        @DisplayName("two characters of one account select to distinct character DIDs — separate save games")
        void distinctCharacterDidsPerSlot() {
            // The point of the whole feature (09 §9): two characters of ONE account must not collapse to
            // one game identity. Same account DID, different slot, therefore different character DID — the
            // key items, the ledger and miners will scope their state by.
            Harness h = harness(3);
            Player first = h.service().createCharacter(DID, "alice"); // slot 1
            Player second = h.service().createCharacter(DID, "alice"); // slot 2

            PlayerSession firstSession = h.service().selectCharacter(DID, first.playerId());
            PlayerSession secondSession = h.service().selectCharacter(DID, second.playerId());

            assertThat(firstSession.did()).isEqualTo(secondSession.did()); // one account
            assertThat(firstSession.characterDid()).isNotEqualTo(secondSession.characterDid()); // two characters
            assertThat(firstSession.characterDid().slot()).isEqualTo(1);
            assertThat(secondSession.characterDid().slot()).isEqualTo(2);
        }

        @Test
        @DisplayName("selecting a terminal character is refused — no double-play")
        void refusesTerminalCharacter() {
            Harness h = harness(3);
            Player migrated = terminal(DID, 1, CharacterStatus.MIGRATED);
            h.players().put(migrated);

            assertThatThrownBy(() -> h.service().selectCharacter(DID, migrated.playerId()))
                    .isInstanceOf(CharacterNotActiveException.class);
        }

        @Test
        @DisplayName("selecting another account's character is a 404, not another account's session")
        void refusesForeignCharacter() {
            Harness h = harness(3);
            Player mine = h.service().createCharacter(OTHER, "bob");

            assertThatThrownBy(() -> h.service().selectCharacter(DID, mine.playerId()))
                    .isInstanceOf(PlayerNotFoundException.class);
        }

        @Test
        @DisplayName("selecting a missing character is a 404")
        void refusesMissingCharacter() {
            Harness h = harness(3);
            assertThatThrownBy(() -> h.service().selectCharacter(DID, UUID.randomUUID()))
                    .isInstanceOf(PlayerNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------ status transitions (one-way)

    @Nested
    @DisplayName("status transitions are one-way")
    class Transitions {

        @Test
        @DisplayName("retire moves an active character to retired")
        void retireActive() {
            Harness h = harness(3);
            Player character = h.service().createCharacter(DID, "alice");

            h.service().retireCharacter(DID, character.playerId());

            assertThat(h.players()
                            .findCharacter(character.playerId())
                            .orElseThrow()
                            .status())
                    .isEqualTo(CharacterStatus.RETIRED);
        }

        @Test
        @DisplayName("markMigrated moves an active character to migrated")
        void migrateActive() {
            Harness h = harness(3);
            Player character = h.service().createCharacter(DID, "alice");

            h.service().markMigrated(character.playerId());

            assertThat(h.players()
                            .findCharacter(character.playerId())
                            .orElseThrow()
                            .status())
                    .isEqualTo(CharacterStatus.MIGRATED);
        }

        @Test
        @DisplayName("a terminal character cannot be retired again — never back to active")
        void retireOfTerminalRefused() {
            Harness h = harness(3);
            Player migrated = terminal(DID, 1, CharacterStatus.MIGRATED);
            h.players().put(migrated);

            assertThatThrownBy(() -> h.service().retireCharacter(DID, migrated.playerId()))
                    .isInstanceOf(CharacterNotActiveException.class);
            assertThat(h.players().updateStatusCalls()).isEmpty();
        }

        @Test
        @DisplayName("a migrated character cannot be migrated again")
        void migrateOfTerminalRefused() {
            Harness h = harness(3);
            Player retired = terminal(DID, 1, CharacterStatus.RETIRED);
            h.players().put(retired);

            assertThatThrownBy(() -> h.service().markMigrated(retired.playerId()))
                    .isInstanceOf(CharacterNotActiveException.class);
        }

        @Test
        @DisplayName("retiring another account's character is a 404")
        void retireForeignRefused() {
            Harness h = harness(3);
            Player mine = h.service().createCharacter(OTHER, "bob");

            assertThatThrownBy(() -> h.service().retireCharacter(DID, mine.playerId()))
                    .isInstanceOf(PlayerNotFoundException.class);
        }
    }

    // ------------------------------------------------------------------ list

    @Test
    @DisplayName("listCharacters returns only active characters, ordered by slot")
    void listsActiveOnly() {
        Harness h = harness(3);
        h.service().createCharacter(DID, "alice"); // slot 1
        Player toRetire = h.service().createCharacter(DID, "alice"); // slot 2
        h.service().createCharacter(DID, "alice"); // slot 3
        h.service().retireCharacter(DID, toRetire.playerId());

        assertThat(h.service().listCharacters(DID)).extracting(Player::slot).containsExactly(1, 3);
    }

    @Test
    @DisplayName("endSession invalidates the token immediately")
    void endSession() {
        Harness h = harness(3);
        Player character = h.service().createCharacter(DID, "alice");
        PlayerSession session = h.service().selectCharacter(DID, character.playerId());

        h.service().endSession(session.token());

        assertThat(h.sessions().resolve(session.token())).isEmpty();
    }
}

package io.github.stoicswe.eyeandsickle.server.directory;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.stoicswe.eyeandsickle.protocol.channel.CharacterHomeRecord;
import io.github.stoicswe.eyeandsickle.server.identity.Did;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link DirectoryRecognizedCharacterCount} — the directory-backed count that widens the
 * soft slot cap from "this server" to "the whole federation" (09 §2).
 *
 * <p>The identity slice's {@code CharacterService} refuses to create a character once the recognized
 * count reaches {@code maxCharacters} (default 3). What that count <em>sees</em> is the whole point of
 * this seam: it must be the federation-wide number the signed directory converged to, one per occupied
 * slot, so the 4th-character refusal fires on the account's true occupancy rather than just the
 * characters this one server happens to host.
 */
class DirectoryRecognizedCharacterCountTest {

    private static final Instant NOW = Instant.parse("2026-07-24T00:00:00Z");
    private static final Did ACCOUNT = Did.of(CharacterHomeFixture.ACCOUNT_DID);

    private final CharacterHomeFixture fixture = new CharacterHomeFixture();
    private final FakeCharacterDirectoryRepository repository = new FakeCharacterDirectoryRepository();

    private CharacterDirectoryService directory() {
        CharacterDirectoryProperties properties = new CharacterDirectoryProperties(null, null, null);
        return new CharacterDirectoryService(
                repository, new CharacterHomeRecordVerifier(properties, fixture.resolver()), properties);
    }

    private void seedSlots(CharacterDirectoryService directory, int count) {
        for (int slot = 1; slot <= count; slot++) {
            directory.accept(fixture.record(UUID.randomUUID(), slot, 1, fixture.signing.getPrivate()), NOW);
        }
    }

    @Test
    @DisplayName("an account with no recognized characters counts zero")
    void zeroWhenEmpty() {
        assertThat(new DirectoryRecognizedCharacterCount(directory()).countRecognized(ACCOUNT))
                .isZero();
    }

    @Test
    @DisplayName("the count is the federation-wide number of occupied slots")
    void countsRecognizedSlots() {
        CharacterDirectoryService directory = directory();
        seedSlots(directory, 3);

        assertThat(new DirectoryRecognizedCharacterCount(directory).countRecognized(ACCOUNT))
                .isEqualTo(3);
    }

    @Test
    @DisplayName(
            "at the cap the count reports it, so CharacterService refuses the 4th — the recognized count is the gate")
    void reportsTheCapForTheRefusal() {
        CharacterDirectoryService directory = directory();
        seedSlots(directory, 3);

        // The identity slice compares this against maxCharacters (default 3); 3 >= 3 is what makes the 4th
        // creation throw CharacterSlotExceededException. This seam's job is to return the honest 3, which it
        // does even though the three characters could be homed on three different servers.
        int recognized = new DirectoryRecognizedCharacterCount(directory).countRecognized(ACCOUNT);
        assertThat(recognized).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("only the queried account's characters are counted")
    void isScopedToTheAccount() {
        CharacterDirectoryService directory = directory();
        seedSlots(directory, 2);
        // A different account's character must not leak into this account's count.
        directory.accept(recordForAccount("did:plc:elsewhere00000000", 1), NOW);

        assertThat(new DirectoryRecognizedCharacterCount(directory).countRecognized(ACCOUNT))
                .isEqualTo(2);
    }

    private CharacterHomeRecord recordForAccount(String accountDid, int slot) {
        return CharacterHomeRecord.sign(
                accountDid,
                UUID.randomUUID(),
                slot,
                CharacterHomeFixture.HOME_DID,
                CharacterHomeFixture.KID,
                CharacterHomeFixture.ENDPOINT,
                fixture.transportKey,
                1,
                fixture.signing.getPrivate());
    }
}

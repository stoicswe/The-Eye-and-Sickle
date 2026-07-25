package io.github.stoicswe.eyeandsickle.server.directory;

import io.github.stoicswe.eyeandsickle.server.identity.Did;
import io.github.stoicswe.eyeandsickle.server.identity.RecognizedCharacterCount;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * The directory-backed {@link RecognizedCharacterCount}: it counts the characters the whole federation
 * recognizes for an account, by reading the signed character directory (09 §2, §4).
 *
 * <h2>Why this is the answer the cap actually wants</h2>
 *
 * The soft slot cap is "at most {@code maxCharacters} <em>recognized</em> characters", and "recognized"
 * is a federation-wide question the identity slice cannot answer from its own {@code players} rows alone
 * (09 §2). The signed, gossiped directory is where that answer lives: every honest server converges to
 * the same set of home bindings, so counting an account's bindings here counts its characters across the
 * federation, not just the ones this server happens to host. A defecting server's excess character is
 * simply never published as a recognized binding, so it never enters this count — which is exactly the
 * non-recognition the cap relies on (03 §4, Invariant I15).
 *
 * <h2>How it supersedes the identity slice's local default</h2>
 *
 * The identity slice ships {@code LocalRecognizedCharacterCount} as a {@code @ConditionalOnMissingBean}
 * default (it counts only this server's own active rows — the honest floor). This bean is a plain
 * component, so when it is present the identity default steps aside and {@code CharacterService} sees
 * only this one — with no change to the identity slice, exactly as its contract intends.
 *
 * <h2>Gated to federating servers</h2>
 *
 * It is registered only when {@code eyeandsickle.federation.enabled=true}, because a non-federating home
 * server has no {@code character_directory} table (its migration lives under the federation location) and
 * no federation to be recognized by. On such a server this bean is absent and the local default is
 * correct and exact — the whole recognized set <em>is</em> this server's own characters.
 */
@Component
@ConditionalOnProperty(prefix = "eyeandsickle.federation", name = "enabled", havingValue = "true")
public class DirectoryRecognizedCharacterCount implements RecognizedCharacterCount {

    private final CharacterDirectoryService directory;

    /**
     * @param directory the character directory the federation-wide count is read from
     */
    public DirectoryRecognizedCharacterCount(CharacterDirectoryService directory) {
        this.directory = Objects.requireNonNull(directory, "directory");
    }

    @Override
    public int countRecognized(Did accountDid) {
        Objects.requireNonNull(accountDid, "accountDid");
        // count fits an int comfortably — an account holds a handful of characters, and the per-account
        // slot bound (1..16) caps the directory rows one account can occupy anyway. Math.toIntExact turns a
        // wildly-out-of-range value into a loud failure rather than a silent wrap that could defeat the cap.
        return Math.toIntExact(directory.recognizedCharacterCount(accountDid.value()));
    }
}

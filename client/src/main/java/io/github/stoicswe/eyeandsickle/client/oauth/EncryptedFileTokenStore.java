package io.github.stoicswe.eyeandsickle.client.oauth;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * The fallback store: AES-256-GCM in a file, with the key in a second file beside it.
 *
 * <h2>⚠ BE HONEST ABOUT WHAT THIS IS</h2>
 *
 * <strong>The key is next to the data.</strong> Any process running as this user can read both, so
 * against a local attacker this is <em>obfuscation, not protection</em>, and it must never be
 * described to a player as though it were more. {@link #isPlatformSecured()} returns false so the
 * interface layer can say which mode is in force.
 *
 * <p>It is still worth having, because the exposures it does close are the ones that actually happen:
 *
 * <ul>
 *   <li><strong>Cloud backup and sync.</strong> The profile directory is inside
 *       {@code ~/Library/Application Support}, {@code %APPDATA%} or {@code $XDG_DATA_HOME}, all
 *       routinely synced. Backup software copies file <em>contents</em>; a token encrypted here is
 *       ciphertext in that backup instead of a live credential.
 *   <li><strong>A shared or copied save.</strong> Players move profile directories between machines,
 *       and this game invites poking at save files. Ciphertext plus a key file the copier probably
 *       did not take is a meaningfully better outcome than a token in plain sight.
 *   <li><strong>Other users of the machine.</strong> Both files are {@code rw-------} where POSIX
 *       permissions exist.
 *   <li><strong>Casual reading.</strong> Nothing greppable, and nothing in {@code ClientProfile}.
 * </ul>
 *
 * <h2>Crypto choices, and why each one</h2>
 *
 * <ul>
 *   <li><strong>AES-256-GCM</strong> — authenticated, so a tampered file fails to decrypt rather than
 *       yielding a mangled token that produces a confusing failure later.
 *   <li>⚠ <strong>A fresh 12-byte IV on every write, prepended to the ciphertext.</strong> Reusing a
 *       nonce with GCM under one key is catastrophic — it leaks the XOR of plaintexts and the
 *       authentication subkey. A fixed IV would be the single worst mistake available in this file.
 *   <li>⚠ <strong>No additional authenticated data.</strong> The first version bound the DID as AAD,
 *       to stop a blob being swapped between two accounts' files. It does not work: AAD must be
 *       supplied to <em>decrypt</em> as well, and the DID is inside the ciphertext — so there is
 *       nothing to supply until after the thing it was meant to protect has already been decrypted.
 *       Storing the DID in the clear beside the blob would fix that and give the property back, at
 *       the cost of naming the account to anyone reading the file. Not worth it for a threat this
 *       small; recorded so the idea is not re-attempted as though it were free.
 * </ul>
 */
final class EncryptedFileTokenStore implements TokenStore {

    private static final String CIPHER = "AES/GCM/NoPadding";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int KEY_BYTES = 32;

    private static final SecureRandom RANDOM = new SecureRandom();

    private final Path secretsFile;
    private final Path keyFile;

    EncryptedFileTokenStore(Path profileDirectory) {
        this.secretsFile = profileDirectory.resolve("session.enc");
        this.keyFile = profileDirectory.resolve("session.key");
    }

    @Override
    public boolean isPlatformSecured() {
        return false;
    }

    @Override
    public String describe() {
        return "encrypted file (no system keychain available)";
    }

    @Override
    public Credentials load() {
        if (!Files.exists(secretsFile) || !Files.exists(keyFile)) {
            return null;
        }
        try {
            byte[] stored = Base64.getDecoder()
                    .decode(Files.readString(secretsFile, StandardCharsets.UTF_8)
                            .trim());
            if (stored.length <= IV_BYTES) {
                throw new OauthException(
                        OauthException.Kind.STORAGE, "stored credentials are truncated; sign in again");
            }
            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(readKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES);
            return CredentialCodec.decode(new String(plaintext, StandardCharsets.UTF_8));
        } catch (OauthException already) {
            throw already;
        } catch (GeneralSecurityException | IOException | IllegalArgumentException unreadable) {
            // Includes a failed GCM tag, i.e. the file was tampered with or the key does not match.
            // "Sign in again" is the right remedy for all of them and is better than a stack trace.
            throw new OauthException(
                    OauthException.Kind.STORAGE,
                    "stored credentials could not be decrypted; sign in again",
                    unreadable);
        }
    }

    @Override
    public void save(Credentials credentials) {
        try {
            byte[] iv = new byte[IV_BYTES];
            // ⚠ A NEW iv every write. See the class comment — this is the one line here that would
            // be catastrophic to "optimise" into a constant.
            RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(CIPHER);
            cipher.init(
                    Cipher.ENCRYPT_MODE,
                    new SecretKeySpec(readOrCreateKey(), "AES"),
                    new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext =
                    cipher.doFinal(CredentialCodec.encode(credentials).getBytes(StandardCharsets.UTF_8));

            byte[] out = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ciphertext, 0, out, iv.length, ciphertext.length);

            writeRestricted(secretsFile, Base64.getEncoder().encodeToString(out));
        } catch (GeneralSecurityException | IOException failed) {
            throw new OauthException(OauthException.Kind.STORAGE, "could not store credentials", failed);
        }
    }

    @Override
    public void clear() {
        try {
            Files.deleteIfExists(secretsFile);
            Files.deleteIfExists(keyFile);
        } catch (IOException failed) {
            throw new OauthException(OauthException.Kind.STORAGE, "could not clear stored credentials", failed);
        }
    }

    private byte[] readKey() throws IOException {
        return Base64.getDecoder()
                .decode(Files.readString(keyFile, StandardCharsets.UTF_8).trim());
    }

    private byte[] readOrCreateKey() throws IOException {
        if (Files.exists(keyFile)) {
            return readKey();
        }
        byte[] key = new byte[KEY_BYTES];
        RANDOM.nextBytes(key);
        writeRestricted(keyFile, Base64.getEncoder().encodeToString(key));
        return key;
    }

    /**
     * Writes a file readable only by this user where the filesystem supports it.
     *
     * <p>⚠ Permissions are set at <strong>creation</strong>, not afterwards. Creating a file with the
     * default umask and then tightening it leaves a window — short, but real, and on a shared machine
     * the window is the whole vulnerability. On Windows, where POSIX permissions do not exist, this
     * degrades to an ordinary write: the ACL inherited from {@code %APPDATA%} is already user-only,
     * and the DPAPI path is what a Windows machine should be using anyway.
     */
    private static void writeRestricted(Path file, String content) throws IOException {
        Files.createDirectories(file.getParent());
        Set<PosixFilePermission> ownerOnly = PosixFilePermissions.fromString("rw-------");
        try {
            Files.deleteIfExists(file);
            Files.createFile(file, PosixFilePermissions.asFileAttribute(ownerOnly));
        } catch (UnsupportedOperationException windows) {
            // No POSIX view. Fall through to a plain write.
        }
        Files.writeString(file, content, StandardCharsets.UTF_8);
    }
}

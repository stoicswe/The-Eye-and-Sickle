package io.github.stoicswe.eyeandsickle.client.sound;

/**
 * The music beds, named for the situation rather than for the track.
 *
 * <h2>⚠ A CUE IS A SITUATION. IT IS NOT A FILENAME, AND THAT IS THE WHOLE POINT.</h2>
 *
 * Call sites ask for {@code MusicCue.BREACH} because a breach has started, never for a particular
 * piece of music. So re-scoring the game — swapping a track, splitting one bed into two, dropping
 * music from a screen entirely — is an edit to this file and touches no caller. The alternative,
 * where a screen names its own track, means the soundtrack is spread across the client and nobody
 * can answer "what plays where" without reading all of it.
 *
 * <h2>⚠ NO TRACK IS SHIPPED, AND A CUE WITH NO FILE IS SILENCE RATHER THAN A FAILURE</h2>
 *
 * These resources do not exist yet: the game has no soundtrack, and inventing one is content work
 * rather than engineering. {@link Audio#music} therefore treats a missing file as "play nothing",
 * logs it once at {@code FINE}, and carries on. That is deliberate and it is the state the client
 * ships in — a game that refused to start, or that logged a warning per screen change, because a
 * composer has not been hired yet would be a worse client.
 *
 * <p>⚠ Dropping a correctly named {@code .wav} into
 * {@code client/src/main/resources/io/github/stoicswe/eyeandsickle/client/sound/music/} is the entire
 * procedure for scoring a screen. No code changes. {@code SfxTest.everyMusicFileIsClaimed} fails the
 * build on a file in that directory that no cue names, so an asset added under a misspelled name
 * announces itself rather than being silently ignored — which is the failure that would otherwise
 * consume an afternoon.
 *
 * <h2>⚠ WAV ONLY, AND THE SIZE ARITHMETIC MATTERS MORE HERE THAN ANYWHERE ELSE IN THE CLIENT</h2>
 *
 * Measured on this machine, {@code AudioSystem.getAudioFileTypes()} returns <b>WAVE, AU, AIFF</b> and
 * nothing else — the JDK has no MP3 or Vorbis decoder. A minute of music as 22 kHz mono WAV is
 * 2.6 MB, and this client ships five platform uber jars plus a jpackage image, so every megabyte here
 * is six megabytes of release. {@link Sample} records the full table and the way out: format support
 * is an <b>SPI</b> question, so a Vorbis service provider on the classpath would make {@code .ogg}
 * load through the existing code path unchanged, at roughly a tenth of the size. That is a dependency
 * decision, and it should be taken when there is real music to weigh rather than in advance.
 */
public enum MusicCue {

    /** Nothing playing. ⚠ A real cue, not a null — see {@link Audio#music}. */
    NONE(null),

    /** The login screen and the setup assistant: before a character exists. */
    MENU("menu.wav"),

    /** Ordinary play at the deck. */
    DECK("deck.wav"),

    /**
     * A breach is in progress.
     *
     * <p>⚠ The minigame is {@code [PROPOSAL]} and unbuilt ({@code docs/design/05}), so nothing
     * triggers this yet. It is declared because the cue is the part that is certain — whatever the
     * puzzle turns out to be, it is the moment the game most wants its own sound.
     */
    BREACH("breach.wav");

    private static final String DIRECTORY = "/io/github/stoicswe/eyeandsickle/client/sound/music/";

    private final String file;

    MusicCue(String file) {
        this.file = file;
    }

    /** The classpath resource, or null for {@link #NONE}. */
    String resource() {
        return file == null ? null : DIRECTORY + file;
    }

    /** Just the file name, for the both-directions asset check. */
    String fileName() {
        return file;
    }

    static String directory() {
        return DIRECTORY;
    }
}

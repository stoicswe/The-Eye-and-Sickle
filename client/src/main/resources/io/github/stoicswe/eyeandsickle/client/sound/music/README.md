# Music beds

Drop a track in here and it plays. There is no code to change.

The file name has to match the one its cue names in
`client/.../sound/MusicCue.java` — `menu.wav`, `deck.wav`, `breach.wav`. A file no cue
names **fails the build** (`SfxTest.everyMusicFileIsClaimed`), which is deliberate: the
alternative is a track sitting here under a misspelled name, never playing, with nothing
anywhere saying so.

A cue with no file is **silence**, not an error. That is the state the client ships in.

## Format

**WAV, AU or AIFF.** Measured with `AudioSystem.getAudioFileTypes()` on JDK 26 — the JDK
has no MP3 or Vorbis decoder. Anything else in this directory will not play.

Rate, channel count and bit depth do not matter: `Sample` converts whatever arrives to the
engine's 44.1 kHz stereo at load, and that path is verified on both JDKs (the shipped chime
is 11 kHz mono). Music is **streamed**, so only the file's own bytes are held in memory,
never the decoded audio — which for a two-minute bed is the difference between 2.6 MB and
42 MB.

## ⚠ Size, before you commit anything

Uncompressed PCM, per minute:

| | stereo | mono |
|---|---|---|
| 44.1 kHz | 10.6 MB | 5.3 MB |
| 22.05 kHz | 5.3 MB | **2.6 MB** |

**Multiply by six.** This client ships five platform uber jars plus a jpackage image, so a
10 MB track is a 60 MB release. A five-track soundtrack at two minutes each is ~26 MB here
as 22 kHz mono and ~156 MB of build output.

**22.05 kHz mono is the recommended shape** for a bed. It is half the data of stereo, and a
music bed under an interface is not material anybody is listening to in stereo.

```bash
afconvert -f WAVE -d LEI16@22050 -c 1 source.aiff deck.wav
```

## If that is too big

Format support is an **SPI** question, not a code one. `AudioSystem` asks every
`AudioFileReader` on the classpath, and nothing in this package names a format — so adding a
Vorbis service provider as a dependency would make `.ogg` load through the existing path with
**no change to any file**, at roughly a tenth of the size.

That is a dependency decision (the client's enforcer rules are deliberately strict, and this
repo publishes unsigned executables), so it should be taken when there is real music to weigh
rather than in advance. `Sample`'s class comment records the full argument.

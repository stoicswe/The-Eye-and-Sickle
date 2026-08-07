# 08 — Audio

**Status: [PROPOSAL]**, except where it cites an Established rule from `01` or `07`.

This document answers **CL-7** in [`../design/15-open-questions.md`](../design/15-open-questions.md),
which read: *"audio is undesigned. This doc set is visual and interaction only. Sound is one of the
few ways to signal urgency without stealing focus mid-keystroke, so it interacts directly with the
attention ladder (`05` §6) and needs its own doc plus an accessibility pass."*

Implemented in `client/src/main/java/io/github/stoicswe/eyeandsickle/client/sound/`.

---

## 1. What sound is for here

The client is dense with things that change while nobody is looking at them: a transfer finishes, a
scan completes, a block lands, a message arrives, a canary trips. `05` §6's attention ladder is built
almost entirely out of things that occupy **screen space** — a notice, a chip, a badge — and every one
of them costs layout and competes with the thing the player is actually reading.

Sound is the one channel that does not. It reaches somebody who is looking at another window, costs no
pixels, and interrupts nothing mid-keystroke. That is its whole job here, and it sets the boundary:

> **Sound marks that something happened. It never carries the only copy of what happened.**

Everything audible is already a notice, a log line, or both. A player who mutes the game loses no
information — which is what makes the mute button honest, and what lets every failure in the audio
stack be silent rather than reported.

### 1.1 Non-goals

- **No spoken word.** Voice would need localisation of a kind nothing else here needs (`i18n/Text`
  translates prose, not recordings), and it dates a game far faster than its graphics do.
- **No positional or 3D audio.** There is no space to be positioned in; the deck is a flat desk. Pan
  exists as a parameter and is currently unused.
- **No music that reacts to game state beat by beat.** A cue changes on a screen change. Adaptive
  scoring is a much larger commitment and would put the soundtrack on the tick.
- **Sound is never an input.** Nothing the player hears is required to solve a puzzle — that would
  make the game unplayable for deaf players and for anyone with the volume down, which is most people.

---

## 2. Architecture

One `SourceDataLine`, one thread, every voice summed in software.

```
Sfx / MusicCue          the catalogue: what exists, and the rules for playing it
      │
    Audio               the facade — the ONLY public class in the package
      │
   SoftMixer            one device, one thread, N voices, 2 buses + master
      │
    Voice               Sampled (decoded, polyphonic) │ Streamed (decoded as it goes)
      │
 Sample / Tone / Gain   decoding, generation, and the arithmetic of loudness
```

### 2.1 Why not one `Clip` per sound

The obvious implementation, and what the one-chime version did. It fails on a second sound for three
independent reasons:

1. **A `Clip` has one playback cursor.** The same effect twice in quick succession restarts rather
   than layering, so two messages arriving together make one noise. Fixing that needs a *pool* per
   effect — N effects × M voices of mixer lines, for a catalogue expected to grow.
2. **Every open `Clip` holds a line.** Measured on the development machine, every device reported
   `maxLines=unlimited` — a fact about macOS on Apple Silicon, not about Windows or ALSA. Where a
   platform is less generous the failure is that playback silently stops after a while, which is close
   to unattributable.
3. **A `Clip` holds the whole sound decoded.** Music means a multi-megabyte track fully resident, and
   there is no way to fade, duck or crossfade between two except through per-line gain controls that
   not every platform provides.

Software mixing answers all three at once, and makes per-bus volume, ducking and crossfade arithmetic
rather than three more mechanisms.

### 2.2 The line is the clock

`SourceDataLine.write` blocks until the device has room. **Measured: 200 ms of frames written into an
80 ms buffer took 162 ms** — the call returns immediately until the buffer fills, then paces the caller
exactly.

So the mix loop is a plain `while` with no sleep, no `Pulse` subscription, no `Timeline` and no
`AnimationTimer`. The device's own consumption rate is the schedule, and it is more accurate than any
clock the client could ask for.

⚠ This is also why the package sits outside `UiContractTest`'s reach without needing an exemption:
that test scans **all of `src/main/java`** for `AnimationTimer` and rations it to two files by name.
An audio engine built on a timer would have had to argue for a third.

### 2.3 Threads

| Thread | Why |
|---|---|
| `eas-audio` | The mix loop. A **platform** thread, not virtual: `write` blocks in a *native* call, and a virtual thread blocked in native code pins its carrier. `RichPresence` uses a virtual thread and is right to — it blocks on sockets, which unmount. |
| `eas-audio-loader` | Decoding, off both the FX thread and the mixer's. One of it: a decode on the mixer thread would put a multi-megabyte read behind a hard deadline. |

Both are daemons. Nothing the player does blocks on either.

### 2.4 Formats

The engine's internal format is **44.1 kHz, 16-bit, stereo, little-endian**. Everything converts to it
at load; the mixer never negotiates.

Measured with `AudioSystem.getAudioFileTypes()` on JDK 26: the JDK decodes **WAVE, AU and AIFF** and
nothing else. Resampling *is* provided — verified on both JDKs on the development machine, 9,924 frames
at 11,025 Hz mono → 39,704 frames at 44,100 Hz stereo, duration preserved exactly. (Unlike secp256k1,
which `protocol/crypto` records as behaving *differently* on those same two runtimes, the two agreed.)

⚠ **What can be decoded is a classpath question, not a code question.** `javax.sound.sampled` is an
SPI, and nothing in the package names a format — so a Vorbis or MP3 service provider on the classpath
would load those formats through the existing path unchanged. That is the escape hatch for §4's size
problem, and it is a dependency decision rather than an engineering one.

---

## 3. The catalogue

### 3.1 Effects — `Sfx`

A closed enum, for the reason `PresenceState` is one: the set of things the game can do to a player's
ears should be a list somebody can read, not something discoverable only by grepping.

Each constant declares its own **gain**, **retrigger guard** and **pitch spread**.

⚠ **The retrigger guard is not a detail.** The engine is polyphonic, so nothing otherwise stops forty
log lines becoming forty simultaneous chimes. `DirectView` already had to solve this by hand — one
chime per poll rather than one per message — and the general answer belongs on the constant, because
the next caller will not know they were supposed to.

⚠ **Pitch spread is the fix for the most fatiguing thing a game can do with audio**, which is playing
the *identical* waveform for a repeated action. Ten keystrokes with a few percent of spread read as a
keyboard; ten bit-identical ones read as a machine, and after a minute they read as a fault.

| | Bus | Guard | Spread | Wired? |
|---|---|---|---|---|
| `MESSAGE` | effects | 250 ms | — | **yes** — `Notifications`, `DirectView` |
| `CONFIRM` | effects | 80 ms | 2% | no |
| `REFUSE` | effects | 120 ms | 2% | no |
| `TICK` | effects | 25 ms | 6% | no |
| `DONE` | effects | 300 ms | 1% | no |
| `ALERT` | effects | 1000 ms | — | no |

⚠ **Declared is not the same as wired, deliberately.** Deciding that a refusal makes a noise is a
design decision about the attention ladder, not a plumbing one, and it should be taken per surface.
Wiring one is a single call: `Audio.shared().play(Sfx.REFUSE)`.

### 3.2 Music — `MusicCue`

A cue is a **situation**, never a filename: call sites ask for `BREACH` because a breach started. So
re-scoring the game is an edit to one enum and touches no caller.

| Cue | When | Wired? |
|---|---|---|
| `NONE` | silence | — |
| `MENU` | login screen, setup assistant | yes |
| `DECK` | ordinary play | yes |
| `BREACH` | during a breach | no — the minigame is `[PROPOSAL]` and unbuilt |

⚠ **No track ships, and a cue with no file is silence rather than a failure.** That is the state the
client ships in. The cues are wired anyway, so that dropping a correctly named `.wav` into
`client/.../sound/music/` is the *whole* procedure for scoring a screen.

### 3.3 Generated sounds — `Tone`

Five of the six effects are **synthesised**, not recorded. This is the same decision the client already
makes about its cursor, its window chrome, its icons and its wallpaper — §9 bans an icon set outright
and every mark on screen is geometry. Generating a confirmation blip is that decision one sense along,
and it buys the same things: nothing to license, nothing to ship, and a sound that follows a constant.

It also answers §4 honestly: a generated effect costs **zero bytes** in six build outputs.

⚠ **It is not a replacement for recorded audio and must not become one.** What is there suits an
interface: short, tonal, unmistakable, uninteresting to hear twice. It cannot make a room tone, a
mechanism, a voice or a music bed, and attempting any of those with oscillators is how a game ends up
sounding like a 1980s answering machine.

⚠ **Generation is deterministic; per-play variation is not.** Every generated sample is built from a
fixed seed, so a constant produces bit-identical audio on every machine and every launch — a generated
asset that differed per run would mean two players hearing different games, and no regression check
could compare one against a previous one. The randomness that *is* allowed is the pitch spread, and it
is safe because **nothing derived from it reaches a rule**.

---

## 4. ⚠ Size, and why it constrains music more than anything else in the client

Uncompressed PCM, per minute:

| | stereo | mono |
|---|---|---|
| 44.1 kHz | 10.6 MB | 5.3 MB |
| 22.05 kHz | 5.3 MB | **2.6 MB** |

**Multiply by six.** The client ships five platform uber jars plus a jpackage image. A five-track
soundtrack at two minutes each is ~26 MB as 22 kHz mono and ~156 MB of release.

22.05 kHz mono is the recommended shape for a bed. If that is still too much, §2.4's SPI note is the
way out — Vorbis is roughly a tenth of the size for no code change.

---

## 5. Accessibility

Held against [`07-accessibility.md`](07-accessibility.md).

### 5.1 ⚠ Reduce motion does NOT silence anything

`Pulse.reducedMotion` suppresses decorative movement under **WCAG 2.2.2**, and this package
deliberately does not consult it. **Sound is not motion.** A player who cannot tolerate moving elements
has said nothing whatever about audio, and treating one setting as the other would take away the one
channel that reaches somebody who is not looking at the screen.

This is worth stating explicitly because the client's instinct everywhere else is that a decorative
subscription uses `Pulse.animate` and therefore stops under reduce motion. That instinct is wrong here,
and applying it would be an accessibility setting that costs a player their notifications.

### 5.2 WCAG 1.4.2 — Audio Control

> *Audio that plays automatically for more than three seconds must have a mechanism to pause or stop
> it, or to control its volume independently of the system.*

A music bed is exactly that. The **music bus is that mechanism**, and it is why music and effects are
separate controls rather than one volume — collapsing them would mean a player who wants the
soundtrack off also loses their notifications.

### 5.3 Sound is never the only signal

Everything audible is also a notice, a log line, or both. That is §1's boundary, and it is what makes
the client fully playable in silence — which is the state it ships in, since only one effect is wired
and no music exists.

### 5.4 Levels

The default master is **60**, not 100: a game that announces itself at full volume the first time it is
opened is one people mute permanently instead of turning down. Music defaults to **70** under it,
because a soundtrack at the same level as a notification is one that masks it.

⚠ The slider is a **square-law taper** (amplitude = fraction²), not linear amplitude. Perceived
loudness goes roughly as the square root of amplitude, so a linear slider spends most of its travel in
a band where everything sounds nearly full. This is a deliberate change from the one-chime version,
and it is quieter at the same setting — the safe direction for a default to move.

---

## 6. Settings

Machine-wide, the line accessibility settings sit on: volume is a property of where the player is
sitting — headphones, an office, a sleeping household — not of which character they loaded.

| Control | Default | Note |
|---|---|---|
| Master | 60 | `soundVolumePercent` — key deliberately unchanged from when it was the only one |
| Music | 70 | §5.2's independent control |
| Sound effects | 100 | Already rationed per sound by gain and guard |
| Silence when the window is not in front | **on** | Off would be this file's usual instinct; a game that plays over the video call somebody alt-tabbed to gets muted at the OS and never turned back on |
| Turn music down while an effect plays | on | Ramped, never stepped — a step change on continuous material is a click |
| Music level while an effect plays | 45 | Ducking to silence is more distracting than not ducking |
| Output | system default | Stored by **name**, not index — indices change when anything is plugged in |
| *(status line)* | — | Reports what the engine **actually did**, never what was configured |

⚠ **Muting is not writing zero into the volume setting.** That would destroy the player's chosen level
the first time they switched windows, and they would come back to a game that had forgotten its own
volume.

⚠ **A device name that no longer resolves falls back to the default rather than to silence.**
Headphones get unplugged, and no sound at all is a far worse answer than the wrong speakers. The
setting still remembers what was asked for, so reconnecting restores it.

---

## 7. Failure

**The whole facility fails silent, and it fails once.** A headless build box, a machine with no mixer,
a device held exclusively by something else, an asset that will not decode: none is worth a dialog, a
stack trace, or a retry per sound. The mixer latches off and every call becomes a cheap no-op.

⚠ Loading paths catch **`Throwable`**, not `Exception`. A machine with no audio stack fails in the
native layer, which is an `Error`, and a `catch (Exception)` would let it past — taking down the
notification that was being delivered when it happened.

The one place this is *not* silent is the Settings status line, which says so in words. On a machine
with no working device every control on that page still moves, and without it there is no way to tell a
muted game from a broken one.

---

## 8. Testing

| | Needs a device? | |
|---|---|---|
| `GainTest` | no | The taper, the equal-power crossfade, the limiter |
| `VoiceTest` | no | Mixing, looping, pitch, pan, fades, streamed decode |
| `SfxTest` | no | The catalogue both ways, retrigger guards, generation |
| `AudioTest` | no | The facade never throws with no device; encapsulation |
| `AudioTest.RealDevice` | **yes — opt-in** | `-Deyeandsickle.audio.device=true` |

⚠ The device tests are gated at the **class**, the same arrangement `SecretStoreTest.Roundtrip` uses
and for the same reasons: they make a noise on the developer's machine and hold a real device, which a
build has no business doing; and on a machine with no device they would pass by doing nothing, which is
worse than not running.

⚠ **The pure/impure split is the point, not a limitation.** The rules that decide what a player hears
are checkable on a build box with no sound hardware, which is where the build actually runs.

---

## 9. Open

- **AU-1: no soundtrack exists.** The cues are wired and the loader works; there is no music. Whether
  to take a Vorbis SPI dependency (§4) should be decided when there is real music to weigh.
- **AU-2: five effects are declared and unwired** (§3.1). Which surfaces should speak is an attention
  -ladder decision per `05` §6, not a plumbing one.
- **AU-3: pan is implemented and unused.** There is no space on the desk to position sound in. It may
  earn its keep if a breach ever spans windows (`05` §44, **UI-8**).
- **AU-4: the breach has no audio design.** The minigame is `[PROPOSAL]`; `BREACH` is declared because
  the *cue* is the certain part — whatever the puzzle turns out to be, it is the moment the game most
  wants its own sound.

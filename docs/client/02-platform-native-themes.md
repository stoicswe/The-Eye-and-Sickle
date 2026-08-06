# 02 — The Platform-Native Theme Family

**Status:** ⚠️ **[PROPOSAL]** — the *existence* of this family and its base-theme assignments are proposed in `00-client-overview.md` §3.2; everything here about how adaptation actually works, and every per-platform rule, is new ground. Three things underneath it are **Established** and are cited rather than re-decided: cross-platform macOS/Linux/Windows from one codebase and JavaFX + AtlantaFX as the theming mechanism (`../architecture/01-tech-stack.md` §1), one `javafx.stage.Stage` per tool (`../design/00-vision-and-pillars.md` §6), and the client as a non-authoritative view layer (Invariant I14). Toolkit and platform claims below were checked against live sources in this pass; anything not checked is marked **⚠ unverified** inline and appears in §8.
**Depends on:** `00-client-overview.md` §3.2 · §3.4 · §3.5 · §4 · §6, `01-visual-language.md` (**the token contract — token and class names here are cited, never redefined**), `../architecture/01-tech-stack.md` §1, `../design/00-vision-and-pillars.md` §6, `../design/05-hacking-minigame.md` §5
**Depended on by:** `03` (window catalogue), `04` (command grammar + teaching layer), `05` (docked fallback + accessibility), the `client/` module implementation — specifically the `PlatformProfile`, `ThemeService` and `NativePalette` types this document specifies

---

## 1. What "native" means here

### 1.1 Four levels of nativeness; we buy three

"Native" is not one thing, and most arguments about it are really arguments about which level someone meant.

| Level | What it would mean | Do we? | Reasoning |
|---|---|---|---|
| **L0 — Widget set** | Draw with the platform's own controls (`NSButton`, Win32 common controls, GTK widgets) | **No** | JavaFX draws its own controls. Chasing pixel-identical platform widgets is unwinnable, and it would put a per-platform fork under every primitive in `01-visual-language.md` §8. The token contract would not survive it. |
| **L1 — Chrome** | Title bars, window buttons, menu placement, file dialogs, dock/taskbar behaviour | **Yes** | Mostly owned by the OS anyway. Cheap to get right, conspicuous when wrong. |
| **L2 — Idioms** | The shortcut modifier, the platform's reserved shortcuts, close-vs-quit semantics, scroll and text-editing keys | **Yes** | This is muscle memory. Violating it is what actually makes an application feel foreign — more than any colour ever does. |
| **L3 — System settings** | Light/dark, accent colour, reduced motion, reduced transparency, persistent scrollbars, high contrast, output scale, locale number format | **Yes — and this is the level that matters most** | These are decisions the player already made, deliberately, often for access reasons. Overriding them is the single most user-hostile thing a desktop application does. |

> **The claim the native family makes is not "you cannot tell this is JavaFX."** It is: **"nothing about this program fought me."** Every OS-level decision the player has already made survives contact with the game.

There is an irony worth naming once and then not dwelling on: this is a game about surveillance, and the native family is the half of the client that reads your system settings. §2.8 bounds exactly what is read, and `00-client-overview.md` §7 already establishes that none of it leaves the machine.

### 1.2 What "native" does not license

Three prohibitions, each a direct consequence of `00-client-overview.md` §3.4's rule that *a theme is a skin*:

1. **No per-platform information architecture.** The thirteen windows in `00-client-overview.md` §6.1 exist on all three platforms with the same ids, the same contents, and the same cross-window links. A macOS build does not get an extra pane; a Windows build does not fold two windows into one.
2. **No per-platform game semantics.** `-es-compute-available` is in the teal-cyan family on every platform; `-es-trace-fill` is in the red family on every platform. The OS accent colour drives chrome only, never a Layer 2 token — the rule is stated in `00-client-overview.md` §3.2 and made mechanical in §2.7 below.
3. **No per-platform copy.** The client's vocabulary is Unix (`00-client-overview.md` §5, pillar C6) and Unix is the same everywhere. We do not say "Finder" on macOS and "File Explorer" on Windows for a game concept, and we do not rename `storage` to something friendlier on any platform.

And a fourth, negative one: **native is not a licence to be less accessible than the story theme.** The floor in `00-client-overview.md` §3.5 — 4.5:1 text contrast, redundant encoding, identical information, keyboard-complete, accessible names, OS preferences honoured — applies to every variant of the native family without exception.

### 1.3 The acceptance test a reviewer can actually run

Put the client beside the platform's own least-glamorous first-party utility — Activity Monitor on macOS, Task Manager on Windows, GNOME System Monitor or KSysGuard on Linux — on the same display, in dark mode, with a non-default accent colour selected. Then ask, in order: does the title bar match? Are the window buttons in the right place and the right shape? Is the menu where the platform puts menus? Does the focus ring look like the platform's? Does the scrollbar behave like the platform's? Is the body text set in the platform's UI font at a plausible size?

Six questions. If all six pass, the family is doing its job, and no amount of additional polish buys much more.

---

## 2. Automatic adaptation — the mechanism

### 2.1 `PlatformProfile` — one place, resolved once

Platform branching spreads. It starts as one `if (isMac)` in a menu builder and ends as forty scattered checks that nobody can enumerate, which makes the checklist in §7 unverifiable. The discipline that prevents it is the same one `CLAUDE.md` applies to the `protocol` module boundary: name the seam, then forbid crossing it anywhere else.

```java
public record PlatformProfile(
        HostOs os,                    // MACOS | WINDOWS | LINUX
        DesktopEnvironment desktop,   // GNOME | KDE | XFCE | TILING | OTHER | NOT_APPLICABLE
        WindowSystem windowSystem,    // QUARTZ | DWM | X11 | XWAYLAND | UNKNOWN
        String uiFontFamily,          // resolved against Font.getFamilies()
        String uiDisplayFontFamily,   // optical-size sibling, or == uiFontFamily
        String monoFontFamily,
        boolean systemMenuBar,        // true only on macOS
        boolean mnemonics,            // false only on macOS
        boolean trayAvailable) { }
```

> **Rule:** `PlatformProfile` is the only type in the client permitted to read `os.name`, `XDG_CURRENT_DESKTOP`, `XDG_SESSION_TYPE`, `WAYLAND_DISPLAY`, or `Font.getFamilies()`. Everything else asks the profile. A `grep` for `os.name` outside this one file is a defect, and it is worth an ArchUnit test in the client module for exactly the reason the server module already has one.

The profile is **immutable for the session**. Everything it holds is genuinely fixed once the process starts — the OS does not change, the desktop environment does not change, the installed font set effectively does not change. Everything that *can* change while running lives in §2.2 and is **observed, never cached**. Conflating the two is how an application ends up needing a restart to notice dark mode.

| Field | Primary source | Corroboration | If nothing resolves |
|---|---|---|---|
| `os` | `System.getProperty("os.name")` | — | fail loudly; there is no fourth case |
| `desktop` | `XDG_CURRENT_DESKTOP` (colon-separated, e.g. `ubuntu:GNOME`) | `DESKTOP_SESSION`, then `GTK.theme_name` (§2.2) | `OTHER` → conservative profile (§5.7) |
| `windowSystem` | `XDG_SESSION_TYPE` | `WAYLAND_DISPLAY` present ⇒ `XWAYLAND` | `UNKNOWN`, treated as `X11` |
| fonts | `Font.getFamilies()` walked against the chains in `01-visual-language.md` §3.2 | — | JavaFX logical families `System` / `Monospaced`, which always resolve |

### 2.2 `Platform.getPreferences()` — the verified surface

**Verified** against the JavaFX 26 javadoc for `javafx.application.Platform.Preferences`. Eight typed properties, all read-only and all observable:

| Property | Type | Default | Since | What the client does with it |
|---|---|---|---|---|
| `colorScheme` | `ReadOnlyObjectProperty<ColorScheme>` | `ColorScheme.LIGHT` | 22 | Selects the light or dark member of the active native variant (§2.4) |
| `accentColor` | `ReadOnlyObjectProperty<Color>` | `#157EFB` | 22 | Drives exactly five chrome tokens, and no others (§2.7) |
| `backgroundColor` | `ReadOnlyObjectProperty<Color>` | `Color.WHITE` | 22 | Corroborating signal only — its relative luminance cross-checks `colorScheme` (§2.6) |
| `foregroundColor` | `ReadOnlyObjectProperty<Color>` | `Color.BLACK` | 22 | Corroborating signal only. **Never** applied directly as `-es-fg-primary` — the theme owns that, and an OS foreground with no known background pairing cannot be contrast-verified |
| `reducedMotion` | `ReadOnlyBooleanProperty` | `false` | 24 | Motion driven in code is *skipped*, not shortened (`01-visual-language.md` §7.4) |
| `reducedTransparency` | `ReadOnlyBooleanProperty` | `false` | 24 | Scrims become opaque; overlay surfaces lose translucency (`01-visual-language.md` §5.5) |
| `persistentScrollBars` | `ReadOnlyBooleanProperty` | `false` | 24 | Scrollbars always visible rather than auto-hiding |
| `reducedData` | `ReadOnlyBooleanProperty` | `false` | 24 | No behaviour yet — `00-client-overview.md` **CL-6** |

`Platform.Preferences` also extends `ObservableMap<String, Object>` with platform-specific string keys and typed accessors returning `Optional` — `getBoolean`, `getString`, `getColor`, `getInteger`, `getDouble`, `getValue`. The javadoc is explicit that availability is version- and configuration-dependent: *"applications should not assume that a particular preference is always available."*

> **Rule:** the eight typed properties are the contract. Raw string keys are an optimisation, always read through the `Optional`-returning accessors, and every raw-key read has a defined behaviour for the empty case. A raw key is never allowed to be the only source for a decision.

The four raw keys this client reads, all **verified** as present in the javadoc's platform key tables:

| Key | Type | Platform | Used for |
|---|---|---|---|
| `Windows.SPI.HighContrast` | Boolean | Windows | Enter the high-contrast treatment (§2.7, §4.6) |
| `Windows.UIColor.AccentLight1…3`, `AccentDark1…3` | Color | Windows | Take the OS's own accent ramp instead of deriving one (§4.6) |
| `macOS.NSColor.selectedContentBackgroundColor` | Color | macOS | `-es-accent-subtle`, the selected-row fill (§3.6) |
| `GTK.theme_bg_color` | Color | Linux | Luminance cross-check for dark mode (§5.3) — the load-bearing Linux signal |

### 2.3 Which signals actually exist on which platform

This is the part that decides how much fallback logic is needed, and it is **not** uniform. Compiled from the javadoc's per-platform mapping tables in this pass:

| Property | macOS | Windows | Linux (GTK) |
|---|---|---|---|
| `colorScheme` | ✅ (NSColor-derived) | ✅ | ⚠ derived from **`GTK.theme_name`** — see §5.3 |
| `accentColor` | ✅ `macOS.NSColor.controlAccentColor` | ✅ `Windows.UIColor.Accent` | ❌ **not mapped** — falls back to the JavaFX default `#157EFB` |
| `backgroundColor` / `foregroundColor` | ✅ | ✅ `Windows.UIColor.Background` / `Foreground` | ✅ `GTK.theme_bg_color` / `theme_fg_color` |
| `reducedMotion` | ✅ `accessibilityDisplayShouldReduceMotion` | ✅ `Windows.SPI.ClientAreaAnimation` (inverse) | ✅ `GTK.enable_animations` (inverse) |
| `reducedTransparency` | ✅ `accessibilityDisplayShouldReduceTransparency` | ⚠ **not listed** in this pass | ❌ **not mapped** |
| `persistentScrollBars` | ✅ `NSScroller.preferredScrollerStyle` | ✅ `AutoHideScrollBars` (inverse) | ✅ `GTK.overlay_scrolling` (inverse) |
| `reducedData` | ✅ `NWPathMonitor` | ✅ `InternetCostType` | ✅ `GTK.network_metered` |

Two consequences worth internalising before writing any code:

- **On Linux the accent colour is not available.** Without intervention every Linux player gets JavaFX's default blue regardless of what they chose in their settings. That is not a catastrophe — it is a defensible, readable blue — but it is a *silent* failure to be native, and it is the strongest argument for the XDG portal work in §5.3 / **PN-1**, which also exposes `accent-color`.
- **On Linux, and possibly Windows, `reducedTransparency` will always read `false`.** So the client must not make translucency the *only* way something is legible, on any platform, ever. `01-visual-language.md` §5.5 already restricts translucency to elevation levels 3–4; this is the reason that restriction has to hold rather than being a preference.

⚠ **unverified:** the Windows `reducedTransparency` row. Windows exposes `Windows.UISettings.AdvancedEffectsEnabled`, which is the natural source, but the mapping was not listed in the tables read in this pass. Confirm before relying on it (**PN-5**).

### 2.4 Resolution: setting × signal → theme

`00-client-overview.md` §3.2 fixes the base-theme assignment. This document adds the **tri-state follow setting**, because "follow the OS" and "I want dark, always" are different intentions and collapsing them is how a player ends up unable to keep a dark game on a light desktop.

| Player setting | macOS | Windows | Linux |
|---|---|---|---|
| `follow system` (**default**) + scheme `LIGHT` | `CupertinoLight` | `PrimerLight` | `PrimerLight` |
| `follow system` + scheme `DARK` | `CupertinoDark` | `PrimerDark` | `PrimerDark` |
| `light` (pinned) | `CupertinoLight` | `PrimerLight` | `PrimerLight` |
| `dark` (pinned) | `CupertinoDark` | `PrimerDark` | `PrimerDark` |
| manual variant | `NordLight` · `NordDark` · `Dracula` · any Cupertino/Primer member, on any platform | " | " |

*(AtlantaFX 2.1 ships `PrimerLight`, `PrimerDark`, `NordLight`, `NordDark`, `CupertinoLight`, `CupertinoDark` and `Dracula` — **verified**. Cupertino derives from the iOS palette, Primer from GitHub Primer, per AtlantaFX's own description.)*

When the setting is `follow system`, the OS signal is still observed and the switcher reports it as `following system: dark`. When pinned, the signal is observed but not acted on, and the switcher reports `pinned: dark` — so a player who wonders why the app did not follow their desktop has the answer on screen rather than in a support thread.

Variant ids extend the family id from `00-client-overview.md` §3.2 without competing with it: the family is `native`, and a specific variant is `native/cupertino-dark`, `native/primer-light`, `native/nord-dark`, `native/dracula`. `native` alone means "family, auto-resolved". The story family's ids (`uos`, `uos-hc`) are unchanged.

### 2.5 The palette gap this exposes, and how it is closed

`01-visual-language.md` §2.3.3 and §2.3.4 publish exactly two native Layer 2 palettes — "native dark" and "native light" — measured against **Primer's** canvas values. But macOS gets Cupertino, and Nord and Dracula are offered as manual choices. That is **five of the seven AtlantaFX themes whose Layer 2 contrast is currently unverified**, and the floor in `00-client-overview.md` §3.5 is only credible if it is verifiable.

The fix is mechanical and belongs in the client module rather than in a document:

```java
// generated + asserted; one entry per (themeId, token)
public final class NativePalette {
    static Map<String, Map<EsToken, Color>> BY_THEME;
}
```

1. At build time, resolve each AtlantaFX theme's Layer 0 surfaces (`-color-bg-default`, `-color-bg-subtle`, `-color-bg-inset`, `-color-bg-overlay`) from its compiled stylesheet.
2. Emit a Layer 2 value per token per theme, starting from the published dark/light values and adjusting lightness only — hue family is fixed by the contract (`00-client-overview.md` **CL-2** proposes exactly that rule; this is the mechanism that would make it enforceable).
3. Assert the floor: 4.5:1 for every token that carries text, 3:1 for every meter fill, boundary and focus indicator, against **every** surface that theme can place it on. A palette change that breaks the floor fails the build, exactly as `01-visual-language.md` §2.3 already requires for the story theme.

This subsumes `01-visual-language.md` **V-2** rather than duplicating it: V-2 asks whether the Primer canvas hexes are right; this asks the more general question, and the generated table answers both. Tracked here as **PN-2** because it is a prerequisite for shipping macOS at all.

### 2.6 Live switching, and what must not move

All eight preferences are observable properties, so the client binds listeners and never polls. `Application.setUserAgentStylesheet(...)` is application-wide, so replacing it restyles every open `Stage` without per-window wiring — the scaffold already depends on this (`EyeAndSickleClient.start`), and `00-client-overview.md` §4.3 already establishes it as the live-switch mechanism.

One implementation wrinkle the scaffold has not met yet: `setUserAgentStylesheet` takes a **single** URL, and the client needs the AtlantaFX theme *plus* its own Layer 1/Layer 2 token stylesheet. ⚠ **unverified** whether JavaFX CSS supports `@import`; assume it does not. The working approach:

- `ToolWindow` is already the single seam through which every `Stage` and `Scene` is created (verified in the scaffold: `show(Stage)` and `openNew()` are the only constructors of a `Scene`). It registers each `Scene` with `ThemeService`.
- `ThemeService.apply(variantId)` sets the AtlantaFX user-agent stylesheet application-wide, then swaps the token stylesheet in each registered `Scene.getStylesheets()`.
- Because `ToolWindow` is the only door, "a window that did not get the theme" is structurally impossible rather than a thing to remember.

Four constraints on the swap itself:

1. **Instant.** `DUR_INSTANT` (`01-visual-language.md` §7.1). No cross-fade, ever, and specifically not during a live breach — the same rule `00-client-overview.md` §4.3 applies to a user-initiated switch applies to an OS-driven one.
2. **No layout movement.** Spacing, type scale, density and radius are Java constants (`01-visual-language.md` §1.3), so a colour-scheme flip cannot reflow anything. If it does, a numeric token has leaked into a stylesheet.
3. **No state loss.** Only stylesheets are replaced. No `Scene` is rebuilt, no `Node` recreated. Scroll position, text selection, in-flight input, window geometry, and the terminal buffer all survive. A player who flips their desktop to dark mid-breach must not lose their place.
4. **Debounced.** Some desktops emit several preference changes while an appearance transition animates. Coalesce on a **250 ms trailing window** before reapplying, so one user action produces one restyle rather than four.

### 2.7 The accent colour: five tokens, and a contrast guard

`00-client-overview.md` §3.2 states the rule — the OS accent may drive chrome-accent tokens only, never a game-semantic token, because a player whose system accent is red must not end up with "breach succeeded" in red. This section is the mechanism, not a restatement.

**Exactly five tokens accept the OS accent.** The list is closed:

`-es-accent-fg` · `-es-accent-emphasis` · `-es-accent-muted` · `-es-accent-subtle` · `-es-focus-ring`

Derivation from the single `Color` the OS gives us:

| Token | Derivation | Floor it must clear |
|---|---|---|
| `-es-accent-emphasis` | the accent as supplied | 3:1 against `-es-surface-base` (it is a fill); its text uses `-es-fg-on-emphasis`, which must clear **4.5:1 against the fill** |
| `-es-accent-fg` | accent, hue held, lightness moved until the floor is met | 4.5:1 against `-es-surface-base`, `-es-surface-raised`, `-es-surface-sunken` and `-es-surface-overlay` |
| `-es-accent-muted` | accent at reduced chroma, composited over `-es-surface-base` | none (hover wash; decorative) |
| `-es-accent-subtle` | accent at low chroma, composited over `-es-surface-base` | text drawn on it must still clear 4.5:1 |
| `-es-focus-ring` | accent, hue held, lightness moved | **3:1 against both the control fill and the surface behind it** (WCAG 2.2 SC 1.4.11) |

Three guards, in order:

1. **Adjust before rejecting.** Hold the hue, move lightness. Most accents (Windows' default blue, macOS's multicolour, GNOME's blue) need no adjustment at all; saturated yellows and limes on a light canvas need a lot.
2. **Fall back rather than ship unreadable.** If the floor cannot be met while holding the hue, the token falls back to the active AtlantaFX theme's own accent, and the client records why in its diagnostic log. A player is allowed to pick lime green as their system accent; they are not thereby allowed to end up with invisible links.
3. **The focus ring never negotiates.** If the accent-derived focus ring cannot clear 3:1 against both neighbours, it falls back immediately — no adjustment attempt. Keyboard-complete with a visible focus indicator is a floor item (`00-client-overview.md` §3.5 point 4), and personalisation does not outrank it.

Windows is the one platform where step 1 is mostly unnecessary: the OS publishes its own accent ramp (`AccentLight1…3` / `AccentDark1…3`), so we take the OS's tones rather than computing our own, and only run the guard (§4.6).

### 2.8 High contrast

`Windows.SPI.HighContrast` is the only reliable high-contrast signal across the three platforms (macOS's "Increase contrast" is not surfaced as a typed property; the XDG portal exposes a `contrast` key — **verified**, `0` = no preference, `1` = higher contrast — but only if we take the portal dependency).

When high contrast is signalled, the native family applies the same treatment `uos-hc` applies to the story family (`00-client-overview.md` §3.3):

- every foreground raised toward 7:1 rather than 4.5:1,
- `-es-border-divider` replaced by `-es-border-control` everywhere, so structure is drawn rather than implied,
- every `*-subtle` fill suppressed — subtle fills are the first thing that stops being distinguishable under a high-contrast scheme,
- all surface translucency dropped,
- `RADIUS_0` for gauge tracks, per the licence `01-visual-language.md` §4.5 already grants.

Whether this becomes a named variant (`native-hc`, a sibling of `uos-hc`) or a modifier applied over any variant is **PN-6**. Whether the client should consume the platform's *actual* high-contrast colours rather than its own harder palette is **PN-5** — it sounds more correct and it hands the contrast floor to a colour set we cannot verify.

### 2.9 What the client reads from the host, exhaustively

Stated as a closed list so that "the client is not a telemetry client" (`00-client-overview.md` §7) is checkable rather than aspirational.

**Reads:** the eight `Platform.Preferences` properties; the four raw preference keys in §2.2; `System.getProperty` for `os.name` and `os.arch`; the environment variables `XDG_CURRENT_DESKTOP`, `DESKTOP_SESSION`, `XDG_SESSION_TYPE`, `WAYLAND_DISPLAY`; `Font.getFamilies()`; `Screen` geometry and output scale; the JVM default `Locale` for number and time formatting (`01-visual-language.md` §9.3).

**Never reads:** the user's files outside the client's own profile directory; the GTK or Qt theme's stylesheet contents; installed applications; hostname, MAC address, or any hardware identifier; browser or shell history; anything at all for the purpose of fingerprinting.

**Never transmits:** any of the above. None of it reaches the home server, and none of it is game state (I14 runs in the other direction: the *server* owns game state, the *client* owns presentation, and presentation preferences stay local — `00-client-overview.md` §4.5).

### 2.9a What the client sends anywhere that is not a home server, exhaustively

Stated as a closed list for the same reason §2.9 is: so `00-client-overview.md` §7 is checkable rather than aspirational. Every entry is **opt-in and dark by default**, and adding another is a documented decision, not a convenience.

| Destination | Sent | Gate |
| --- | --- | --- |
| The quote provider the player picked (`client/stocks/HttpStockFeed`, `SymbolLookup`) | A ticker symbol, and the player's own API key in the URL | Blank key by default — the panel runs on the simulated feed until the player pastes one |
| The Discord client running on this machine (`client/presence/*`) | One constant from `PresenceState`, an elapsed timestamp, and this process's pid | `discordPresenceEnabled`, off by default; also inert with no application id configured |
| The player's own **Bluesky PDS** (`client/bsky/BlueskyChat`) ‡ | An app password once, at sign-in; thereafter a bearer token and a conversation id | No account connected by default; needs a handle **and** an app password the player put in the OS credential store |
| The **handle resolver** and the **PLC directory** (`client/bsky/PdsDirectory`) § | The handle the player typed, then their DID — both public by construction. **No credential, ever** | Same gate: only on a sign-in the player initiated |

> **‡ THE THIRD ENTRY, added 2026-08-06 on explicit direction — and the first that is somebody's real social account.**
>
> The COMS window's **DIRECT** tab wraps the player's own Bluesky direct messages: conversations,
> groups and history. Three things make it a different shape from the two above it and each is
> load-bearing.
>
> **It is READ-ONLY of the player's own data, and the game sends none of its own.** No handle, DID,
> avatar, balance, standing, item, machine name or address leaves — the request body at sign-in is
> the credential the player typed, and every later request carries a bearer token and a convo id.
> Nothing about the *game* is transmitted, which is what keeps `00` §7's "not a telemetry client"
> true rather than merely narrow.
>
> **Nothing that comes back is ever written to a save.** Those are messages other people wrote, and
> `GameSave.messages` is the engine's own inbox whose entries carry entitlements. The cache dies with
> the window. That separation is **I14** at its smallest scale and the tab strip is the seam.
>
> **The credential is in the OS store, never in a file** (`client/credentials/`). If the machine has
> no store, there is nowhere safe to keep an app password and the feature is simply off — it does not
> fall back to `settings.json`.
>
> ⚠ **Consent is Bluesky's, not this game's.** `listConvos` splits conversations into `accepted` and
> `request`; the client shows both and marks the pending ones. Building a second, parallel allow-list
> here would be this game keeping a social graph, which is exactly what §2.9 forbids.

> **§ THE FOURTH ENTRY EXISTS TO KEEP THE THIRD ONE HONEST, added 2026-08-06.**
>
> The client cannot know which server hosts an account until it looks, and **`bsky.social` is not
> that server** — it is the entryway, which fronts session methods for every Bluesky-hosted account
> and holds none of them. Assuming otherwise is what made direct messages fail outright: sign-in
> succeeded and every `chat.bsky.*` call came back **501 MethodNotImplemented**, because the entryway
> has no chat method to forward.
>
> ⚠ **The order is the privacy argument, and it is the whole reason this is two requests rather than
> zero.** The free fix is to keep signing in at the entryway and adopt the `didDoc` that
> `createSession` returns — which is what `@atproto/api` does, and which is kept here as a second
> correction. On its own it means **a self-hosting player's app password reaches Bluesky before
> anyone discovers their account is not there.** A password belongs to exactly one server, so the
> host is settled first out of public data, and the credential is posted only to the machine meant to
> hold it.
>
> ⚠ **Nothing about the player or the game is sent** — a handle and a DID, both already public, and
> no bearer token. It happens **once per sign-in**, never on a poll.
>
> ⚠ **The endpoint that comes back is a credential destination, so it is validated rather than
> trusted**: HTTPS only (an `http://` endpoint would put the app password in clear), no userinfo
> (`https://real@evil/` reads as one host and resolves to another), and the `#atproto_pds` service is
> matched by name rather than taken as the first entry in the array — a labeler sits in the same
> list. Anything unusable falls back to the entryway, which is correct for every Bluesky-hosted
> account.

None of the first two carries anything from §2.9's read list, nor an operator handle, DID, avatar, balance, standing, item, machine name or address. ⚠ **The last two do carry a handle and a DID, and the distinction is which one:** that is the player's **Bluesky** identity, which they typed in themselves and which is public on a public network — never the *operator* handle, DID or avatar the game holds, and never anything else about the game. No entry in this table transmits game state in either direction. ⚠ The Discord entry is **not a network socket** — it is a Unix domain socket or a named pipe to a local process — and its candidate paths are **composed from environment variables rather than discovered by listing a directory**, because enumerating `$TMPDIR` would mean reading the names of every other program's IPC endpoints, which is the fingerprinting §2.9 says this client never does.

---

## 3. macOS

### 3.1 Design language and base theme

macOS 26 "Tahoe" is current as of this writing (**verified**, July 2026) and introduced the **Liquid Glass** material — translucent, refractive, responding to motion with real-time highlights. AtlantaFX's Cupertino pair derives from Apple's **palette**, not from that material system (**verified** against AtlantaFX's own theme description).

**We take the palette and do not chase the material.** Liquid Glass needs real-time refraction and dynamic light response; JavaFX CSS offers neither, and a static gradient approximating glass is the kind of half-imitation that reads worse than an honest flat surface. It would also collide with `reducedTransparency` and with `01-visual-language.md` §5.5's rule that elevation 1–2 uses surface steps rather than effects. Apple's own design has taken criticism on the desktop for legibility (**verified**: macOS 27 is reported to be walking parts of it back), which is a second reason not to bet the client's readability on imitating it.

What the native family *does* adjust on macOS, entirely within the licences the token contract already grants:

| Aspect | macOS value | Licence |
|---|---|---|
| Control corner radius | one step up: buttons, fields and chips at `RADIUS_LG` (8), panels at `RADIUS_MD` (4) | `01-visual-language.md` §4.5 — "a theme may shift the whole scale by one step" |
| Focus ring | 2 px, accent-derived, outside the control's bounds | **unchanged** — macOS's own ring is thicker, but the width is fixed by `01-visual-language.md` §1.5 and consistency across platforms wins here |
| Icon stroke | unchanged | `01-visual-language.md` §6.1 — geometry never varies |

The focus-ring deviation is deliberate and is the one place the native family knowingly declines to match the platform: a focus indicator that changes size between platforms is a focus indicator that has to be re-verified for every platform, and 2 px meeting 3:1 is defensible everywhere.

### 3.2 Window chrome and traffic lights

- **Traffic lights** sit top-left in a fixed order (close, minimise, zoom) with fixed geometry. We never draw our own and never assume their inset.
- **Every one of the thirteen windows uses `StageStyle.DECORATED`.** That buys real traffic lights, real Mission Control behaviour, real window tiling, and real full-screen — none of which we could reimplement and all of which a Mac user will try.
- **`StageStyle.EXTENDED` + `HeaderBar` is not shipped in v1.** It is genuinely attractive for the `rig-monitor`, which is a compact strip whose headline compute figure would sit beautifully in a header bar. But it is **verified** as a *preview* API in JavaFX 25 and still `@Deprecated`-as-preview in 26, with javadoc that says it "may be changed or removed in a future release." Betting the game's most important window (pillar C2) on an API that may be withdrawn is a bad trade for a cosmetic gain. Tracked as **PN-3**.
  If it is adopted later: `HeaderBar` exposes `left` / `center` / `right` content and read-only `leftSystemInset` / `rightSystemInset` properties describing the system-reserved button area (**verified**). **Those insets are read at runtime, never hardcoded** — traffic-light geometry belongs to the OS and has changed across macOS releases.
- **Closing the last window does not quit.** This is a macOS convention and we honour it. It composes cleanly with pillar C2: the `rig-monitor` cannot be permanently closed — ⌘W minimises it to the compact strip (`00-client-overview.md` §2, C2). On macOS, closing every window therefore leaves the app running with the rig monitor available from the Dock and from `Shortcut+0`; ⌘Q quits. On Windows and Linux, closing the last window quits, after the confirmation in §6.3.
- **The green button puts a window in its own Space**, which is actively hostile to an operator's desk — a full-screened terminal hides the map and the rig monitor. We do not disable it (that would be fighting the platform), but the supported answer to "I want one big window" is the docked layout (`00-client-overview.md` §6.4), and the macOS first-run tips say so.

### 3.3 Menus — the one system menu bar

macOS has exactly one menu bar, at the top of the screen, belonging to the focused application. JavaFX's hook is **verified**: `MenuBar.setUseSystemMenuBar(true)` — *"Use the system menu bar if the current platform supports it"* — with the caveat, quoted from the javadoc, that it *"should not be set on more than one MenuBar instance per Stage… the last menu set is allowed to modify the system menu bar."*

The multi-window problem is real: thirteen `Stage`s, one menu bar, and the system bar swapping as focus moves. The naive implementation — each window contributes its own menus — produces a menu bar whose *shape* changes as the player's eye moves between windows, which is precisely the failure pillar C5 exists to prevent.

> **Rule: one menu model, many bars.** Every `Stage` builds a `MenuBar` from the same shared `MenuModel` with `useSystemMenuBar(true)`. Window-specific commands are present in every window's copy and **disabled** where they do not apply, never added or removed. The menu is the application's, not the window's.

The menu structure on macOS:

| Menu | Contents | Notes |
|---|---|---|
| *(app menu)* | About, Settings…, Services, Hide, Quit | **Created by the platform, not by us.** Its title comes from the bundle |
| Session | Connect, disconnect, switch home server, sign out | No `File` menu — the client has no documents (`00-client-overview.md` §7: not a real shell, no filesystem access) |
| Tools | One item per tool window, in switcher order, with `⌘1`…`⌘9` | Mirrors `00-client-overview.md` §6.1 |
| View | Density, teaching level, theme, docked layout toggle | The `⌘⇧` bindings from `00-client-overview.md` §6.3 |
| Window | Minimise, Zoom, Bring All to Front, then every open `Stage` | macOS convention; mirrors the `switcher` window's content |
| Help | Term index, `man` lookup, keyboard reference | The teaching layer's front door (`00-client-overview.md` §5.2) |

Two macOS-specific consequences:

- **We do not duplicate platform items.** No Quit, About, or Settings in a menu of ours on macOS — they live in the app menu. On Windows and Linux they go under Session and View respectively.
- **The app menu's title comes from the bundle**, via `CFBundleName` / jpackage's `--mac-package-name`, which is **verified** to be limited to 16 characters and intended exactly for the menu bar. `Eye and Sickle` is 14 characters and fits; `The Eye and Sickle` is 18 and does not. Running unbundled (`mvn -pl client javafx:run`) shows the main class name instead — that is expected, not a bug, and should be written down before someone files it. ⚠ **unverified:** whether JavaFX 26 offers any public API to add items to the macOS application menu; the search in this pass surfaced only third-party JNA-based libraries, which we are not taking a dependency on. **PN-7**.

### 3.4 Keyboard

`KeyCombination.SHORTCUT_DOWN` is **verified** to map to *"control on Windows and meta (command key) on Mac"* — one binding, correct modifier per platform, which is the whole reason `00-client-overview.md` §6.3 writes every shortcut as `Shortcut+`.

What the platform owns and we must not take:

| Combination | macOS meaning | Our position |
|---|---|---|
| `⌘Q` | Quit | Never rebound. Intercepted for confirmation during a live engagement (§6.3) |
| `⌘W` | Close window | Bound to close-this-window. On `rig-monitor`, minimise-to-strip (C2), explained inline once |
| `⌘M` / `⌘H` | Minimise / Hide | Platform's; untouched |
| `⌘,` | Settings | Bound to Settings on all three platforms; the macOS app menu item routes to the same place |
| **`` ⌘` ``** | **Cycle windows of the frontmost application** | **We do not bind it on macOS.** The OS already does exactly what `00-client-overview.md` §6.3 specifies for `` Shortcut+` ``. Same key, same behaviour, different owner — the best possible outcome |
| `⌘.` | Historical "cancel" | Our abort binding (`00-client-overview.md` §6.3) lands on an idiomatic key. Still always confirms |
| `⌘0` | No system meaning | Free for the rig monitor, which is what C2 needs |
| `⌥←/→`, `⌘←/→` | Word / line-start-end in text | The terminal window must implement the **platform's** editing set, not one set. The key table belongs to `04` |

**No mnemonics on macOS.** The platform has no menu access keys; `PlatformProfile.mnemonics` is false, and menu labels carry no mnemonic markers there.

**The `Alt`/`Option`-held attribution overlay** (`00-client-overview.md` §6.3) is safe on macOS — Option is a normal modifier there. It is *not* safe on Windows or GNOME; §4.4 and §5.5 specify the mitigation, and the resulting rule is global: **`Shortcut+Shift+A` is always bound as an equivalent toggle, so attribution is never Alt-only.** A pillar-C3 feature reachable by only one key that one platform steals is a pillar-C3 feature that does not exist on that platform.

### 3.5 Fonts

The resolution orders are fixed by `01-visual-language.md` §3.2 and are not restated here. Three macOS specifics:

- **`Font.getFamilies()` may not report `SF Pro Text` under that name.** Depending on the JDK and macOS version it can surface as `.AppleSystemUIFont`, `System Font`, or not at all. This is exactly why §3.2 of the visual-language doc resolves the chain at startup rather than putting a family name in CSS — `-fx-font-family` takes no fallback list (**verified** there). Tracked as `01-visual-language.md` **V-3**; the checklist item in §7 is to log the actual list on a real machine.
- **The chain's last entry is the safety net, and it is a real one.** `System` and `Monospaced` are JavaFX *logical* families that always resolve to something platform-appropriate. So the worst case on any platform is "the platform's own default UI font" — which is precisely the native goal. The named entries earlier in the chain are an optimisation over a floor that is already correct.
- **`SF Mono` may require the Xcode/Terminal install** on some systems; `Menlo` ships with macOS and is the reliable second entry.

### 3.6 Accent, selection, and dark mode

- **Accent** comes from System Settings → Appearance, defaulting to "Multicolour". Available via the typed `accentColor` property (backed by `macOS.NSColor.controlAccentColor`).
- **Selection colour is separate from accent on macOS**, and it is the tell. A selected row that does not match the OS selection tint is the single most obvious "this is not a Mac app" signal in a list-heavy application — and this client is nothing but lists. **Rule: on macOS, `-es-accent-subtle` (the selected-row fill, `01-visual-language.md` §1.5) takes `macOS.NSColor.selectedContentBackgroundColor` when available**, falling back to the derived accent tone otherwise. Cheap, verified key, disproportionate payoff.
- **Automatic light/dark at sunset** fires the `colorScheme` property like any other change; the 250 ms debounce in §2.6 covers the transition.
- ⚠ **Packaging gotcha, checklist item MAC-9:** a bundle whose `Info.plist` sets `NSRequiresAquaSystemAppearance = true` is pinned to light mode forever, and legacy Java packaging tooling has been known to set it. Verify it is absent — or explicitly `false` — in whatever jpackage produces. This would silently defeat the entire adaptation mechanism on macOS and would look like a bug in our code.

### 3.7 File dialogs, notifications, and the Dock

- **File dialogs.** `FileChooser` and `DirectoryChooser` delegate to `NSOpenPanel` / `NSSavePanel` (**verified**). They cannot be styled, which is correct — a native file dialog is native.
  ⚠ **The owner-chain trap, and it is a serious one in a thirteen-window client.** The javadoc is **verified** to state that when an owner window is set, *"input to all windows in the dialog's owner chain is blocked while the dialog is being shown."* Passing the primary stage as owner would therefore block the rig monitor — which is precisely the failure `01-visual-language.md` §5.3 bans `Modality.APPLICATION_MODAL` to prevent. **Rule: always pass the requesting `Stage` as owner. Never the primary stage. Never null.**
  The client needs file dialogs rarely and only for **export**: a ledger extract, a log transcript, a saved provenance chain. There is no import path — `00-client-overview.md` §7 rules out user-supplied themes and any filesystem access beyond the profile directory.
- **Notifications: none in v1.** JavaFX has no notification API. The three options are `java.awt.SystemTray` / `TrayIcon.displayMessage` (drags AWT into the process alongside JavaFX and behaves inconsistently on macOS), a JNI/JNA bridge (a native dependency for a nicety), or nothing. We choose nothing, and it is not a compromise: `00-client-overview.md` §2 pillar C5 already requires that alerts *accrete into a triage list* rather than stealing focus, and an OS notification is focus theft with a sound. Tracked as **PN-4**, where the genuinely interesting question is a Dock **badge** — attention without interruption — rather than notifications.
- **Dock badge and progress** are `java.awt.Taskbar` API, and mixing AWT into a JavaFX process starts a second toolkit for a decoration. Rejected in v1; part of **PN-4**.

### 3.8 HiDPI

- Retina displays report an output scale of **2.0**. `Screen.getOutputScaleX()` is **verified** as *"the recommended output scale factor… should be applied to a scene in order to compensate for the resolution and viewing distance of the output device"*, and `Screen.getBounds()` is **verified** to be *"reported adjusted for the outputScale"*.
- **What would break if this were ignored is raster assets — and we ship none.** Icons are `SVGPath` constants (`01-visual-language.md` §6.2). That decision was made for other reasons and pays off here: there is no @1x/@2x asset set to maintain across three platforms and mixed-scale monitors.
- **The real risk is mixed-scale multi-monitor**, which is `00-client-overview.md` **CL-8**. Concrete rule this document adds:
  > Persisted window geometry (`00-client-overview.md` §4.5) is stored in **logical** units and re-validated on restore against `Screen.getScreensForRectangle(...)`. If no screen intersects the stored rectangle, the window falls back to the primary screen's visual bounds rather than opening off-screen. And after `show()`, the client **reads back** `getX/getY/getWidth/getHeight` and persists what actually happened, not what was requested.
  That second half matters on every platform and is load-bearing on Linux (§5.4).
- Do not attempt sub-pixel hairlines. A 0.5 px border is not expressible; 1 px logical renders as 2 device px at 2.0 and is fine.

### 3.9 macOS gotchas worth knowing in advance

1. **`setAlwaysOnTop(true)` on macOS floats the window above *other applications*, not just our own** — it is a window level, not an app-relative ordering. For the rig monitor that is the intent (`01-visual-language.md` §5.4), but it also means the rig monitor floats over the player's browser. They can revoke it; the macOS first-run tip must say so, because a player who does not know it is a setting will read it as rudeness.
2. **A full-screened `Stage` gets its own Space** and the other twelve windows are not visible beside it (§3.2).
3. **`⌘Q` quits immediately** unless intercepted. During a live engagement that is a real, persisted loss (`../design/05-hacking-minigame.md` §2 makes `aborted` an outcome with consequences). §6.3 makes the interception a cross-platform rule.
4. **Reduced motion and reduced transparency live under Accessibility → Display**, and both are **verified** as mapped on macOS — so macOS is the platform where `01-visual-language.md` §7.4 and §5.5 are fully exercised. Test there first; it is the only place all eight signals are known-good.
5. **Menu bar ownership is exclusive.** If any code path sets `useSystemMenuBar(true)` on a second `MenuBar` within one `Stage`, the javadoc is **verified** to say the last one wins and the previous installed menu is unset. One bar per stage, built from one model (§3.3).

---

## 4. Windows

### 4.1 Design language and base theme

Windows 11's Fluent design uses the **Mica** material for long-lived windows, **Segoe UI Variable** as the system font with Text/Small/Display optical variants, and rounded window corners. Microsoft's own title-bar guidance names **48 px** as the recommended height for a title bar containing interactive content (**verified**).

`00-client-overview.md` §3.2 assigns `PrimerLight` / `PrimerDark`. The reasoning, restated because it is the one assignment that looks arbitrary: AtlantaFX ships **no Fluent theme**, and Primer's neutral ramp and control density are the closest fit in the set. We are matching *neutrality and legibility*, not imitating Fluent — and specifically, **we do not fake Mica with a gradient**. Mica samples the desktop wallpaper through the DWM backdrop APIs; a static approximation is a static approximation, and it fights `reducedTransparency` and the elevation model in `01-visual-language.md` §5.5.

Radius needs no adjustment on Windows: Fluent's control radius is close to our `RADIUS_MD` (4) default, so unlike macOS (§3.1) the scale is not shifted.

### 4.2 Window chrome and caption buttons

- **Caption buttons** sit top-right in a fixed order — minimise, maximise/restore, close — with close rightmost and red on hover. Order and geometry belong to DWM.
- **`StageStyle.DECORATED` for all thirteen windows.** This is not merely the safe choice: it is what makes **Snap Layouts** work (hovering the maximise button offers layout zones), along with `Win`+arrow snapping, Task View, and Alt+Tab. Snap Layouts are genuinely useful for an operator's desk — a Windows player can arrange four tool windows in one gesture — and they are the one platform feature that materially improves the multi-window fantasy for free.
- ⚠ **The dark title bar problem, and it is the most likely "this looks foreign" report we will get.** A dark-themed application on Windows 11 gets a **light** caption unless it opts in by calling `DwmSetWindowAttribute(hwnd, DWMWA_USE_IMMERSIVE_DARK_MODE, TRUE)` — **verified** as supported from Windows 11 build 22000. No evidence was found in this pass that JavaFX 26 does this, and there is no public JavaFX API for it. **This must be measured on a real Windows 11 machine before shipping** (checklist WIN-2, open question **PN-8**). The three outcomes:
  1. *JavaFX already handles it* — nothing to do; delete this note.
  2. *It does not, and we accept a light caption on a dark window* — visibly wrong, cheap, honest.
  3. *It does not, and we use `StageStyle.EXTENDED` + `HeaderBar` to draw our own header* — cost: a preview API (§3.2, **PN-3**), plus ⚠ **unverified** interaction with Snap Layouts, which need the maximise-button hover region to be declared. Losing Snap Layouts to gain a dark caption would be a bad trade.

### 4.3 Menus

- **In-window `MenuBar` at the top of the `Stage`.** `useSystemMenuBar` has no effect here. The same shared `MenuModel` as macOS (§3.3), minus the app menu: Quit moves under Session, Settings and About move under View and Help.
- **Thirteen windows do not each get a full menu bar.** The `rig-monitor` — the one window that is always present (`00-client-overview.md` §2, C2) — carries the full bar. Every other `Stage` carries a compact menu button in its own header row, opening the same model. This is a chrome difference, which §6 permits, and it is the honest answer to "a menu bar on a 320 px-wide tool window is absurd." The real navigation surface on every platform is the command palette (`Shortcut+K`).
- **Mnemonics.** Windows users expect access keys, and `PlatformProfile.mnemonics` is true here and on Linux. JavaFX parses `_` in menu-item text as a mnemonic marker; ⚠ **unverified** whether `mnemonicParsing` defaults to true for `MenuItem` in JavaFX 26 — set it explicitly rather than relying on the default.

### 4.4 Keyboard

`SHORTCUT_DOWN` maps to **Ctrl** (**verified**).

| Combination | Windows meaning | Our position |
|---|---|---|
| `Alt+F4` | Close window | Platform's. Intercepted for confirmation during a live engagement (§6.3) |
| `Ctrl+W` | Close (conventional in tabbed/document apps) | Bound to close-this-window, symmetric with macOS. `rig-monitor` minimises to strip |
| `F1` | Help | Bound to the term index — the teaching layer's front door (`00-client-overview.md` §5.2) |
| `F10` / `Alt` | Focus the menu bar, reveal access keys | **Collides with our `Alt`-held attribution overlay** — see below |
| `F6` | Cycle panes within a window | Bound in the docked layout; specified in `05` |
| `Win`+anything | Reserved by the OS | **Never bind `META` on Windows** |
| `` Ctrl+` `` | No OS owner | Bound to cycle tool windows, per `00-client-overview.md` §6.3 — unlike macOS (§3.4), here we own it |

> **The `Alt` collision, and the mitigation.** On Windows, pressing and holding `Alt` puts the menu bar into access-key mode and arms it to consume the next keystroke. `00-client-overview.md` §6.3 binds "`Alt`/`Option` held" to revealing attribution overlays (pillar C3). The mitigation is twofold: (a) the overlay triggers on `Alt` *held* without consuming any keystroke, so releasing `Alt` without a second key leaves the menu bar in its usual state; and (b) **`Shortcut+Shift+A` is bound on every platform as an equivalent toggle**, so attribution is never reachable only via a key the platform contests. The same applies on GNOME, where `Alt` is a window-management modifier (§5.5).

### 4.5 Fonts

The Windows chain is fixed by `01-visual-language.md` §3.2. Two Windows-specific refinements:

- **Optical sizes are real on Windows and we should use them.** Segoe UI Variable ships Text, Small and Display variants (**verified**). `PlatformProfile` therefore carries **two** UI families on Windows: `uiFontFamily` (a Text variant) for everything at or below `TYPE_TITLE_2` (18 px), and `uiDisplayFontFamily` (a Display variant) for `TYPE_TITLE_1` (22 px) and `TYPE_DISPLAY` (28 px). This is cheap native polish that costs one extra field. ⚠ **unverified:** the exact family strings `Font.getFamilies()` reports for the optical variants — log them on a real machine (checklist WIN-7).
- **Windows 10 has plain Segoe UI**, no Variable. The chain already covers it, and `uiDisplayFontFamily` collapses to equal `uiFontFamily`.
- **Cascadia Mono** ships with Windows 11 via Terminal; **Consolas** is the guaranteed floor and is present on every Windows since Vista.
- ⚠ **Text rendering on dark surfaces.** JavaFX's Prism pipeline does its own text rendering, and LCD sub-pixel antialiasing (`prism.lcdtext`) can produce colour fringing on near-black backgrounds. **Rule: never set `prism.*` system properties in shipped code** — they exist for reproducing a bug report. But *do* look at `TYPE_MONO_BODY` (13 px) on `-es-surface-sunken` on a real Windows machine before shipping (checklist WIN-8); the terminal buffer is where the player spends their attention.

### 4.6 Accent, dark mode, and high contrast

- **Windows publishes its own accent ramp**, and this is the one place a platform hands us exactly what we need. `Windows.UIColor.Accent` plus `AccentLight1…3` and `AccentDark1…3` are **verified** present. **Rule: on Windows, `-es-accent-muted` and `-es-accent-subtle` take OS ramp tones rather than tones we derive.** The contrast guard in §2.7 still runs — the OS ramp is tuned for Fluent's canvases, not ours — but the *hue relationships* come from the OS, which is what makes a Windows app look like it belongs.
- **Windows has two separate dark toggles** — "Windows mode" (taskbar, Start) and "app mode" (application chrome). Only app mode should drive us. The typed `colorScheme` property is the intended source.
  ⚠ **A note on the raw keys:** the javadoc's Windows mapping table read in this pass lists `Windows.SPI.HighContrastColorScheme` as the `ColorScheme` source, which reads oddly — that key names the *high-contrast scheme*, and the natural signal for light/dark is `Windows.UIColor.Background`. **Do not build on the raw keys here.** Use the typed property, and corroborate with the relative luminance of `backgroundColor` if the two ever disagree; if they do, that is a JavaFX bug worth reporting, not something to work around silently.
- **High contrast:** `Windows.SPI.HighContrast` (Boolean, **verified**) triggers the treatment in §2.8. Windows is the only platform where this signal is reliable today, so it is where the treatment gets tested.
- **"Show accent colour on title bars and window borders"** is a DWM setting applied to our decorated windows for free. Nothing to implement — and another argument for `DECORATED` (§4.2).

### 4.7 File dialogs, notifications, and the taskbar

- **File dialogs** use the Vista-era `IFileDialog` (**verified**). Same owner-chain rule as §3.7, for the same reason.
- **Notifications: none in v1**, same reasoning and the same open question (**PN-4**).
- **Thirteen taskbar buttons.** Windows groups them under one application icon, which is fine and even useful — the group *is* a window switcher. But it makes window titles load-bearing, because a taskbar tooltip and a grouped thumbnail label truncate from the right.
  > **Rule, applying to all three platforms: window titles put the tool name first — `Rig Monitor — Eye and Sickle`, not `The Eye and Sickle — Rig Monitor`.**
  > This is a **correction to the current scaffold**, which uses the second form in `ToolWindow.rigMonitor()`. Tool-first is right on Windows because of truncation, right on macOS because the app name is already in the menu bar, and right on Linux because window lists and Alt-Tab labels truncate the same way.
- **Jump lists and taskbar overlay badges** are `java.awt.Taskbar`; rejected in v1 with the Dock badge (**PN-4**).

### 4.8 HiDPI

- Windows uses **per-monitor DPI awareness v2**, and mixed 100 % / 125 % / 150 % / 175 % setups are the norm rather than the exception. `Screen.getOutputScaleX()` will therefore return **non-integer** values — unlike macOS's clean 2.0.
- **What breaks if this is ignored:** anything that assumes an integer scale. A 1 px logical border at 1.5× lands on a half-device-pixel boundary and may render as 1 or 2 device pixels inconsistently along its length. The client is already protected by construction — `01-visual-language.md` §1.5 marks `-es-border-divider` and `-es-border-faint` as **decorative only**, and `-es-border-control` must independently clear 3:1 — but the protection is only real if it is verified. Checklist WIN-10 is to view a dense table at 150 % and confirm no boundary is *carried* by a hairline.
- **DPI changes while running**, when a window is dragged between monitors or a laptop is docked. Geometry persistence must survive it (§3.8's read-back rule), and the rig monitor's compact strip must stay legible after being sized at 175 % and moved to 100 %.
- ⚠ **unverified:** `glass.win.uiScale` and related overrides. They exist for reproducing scaling bugs. **Never set them in shipped code.**

### 4.9 Windows gotchas worth knowing in advance

1. **An always-on-top window that is also focusable can steal focus when shown.** `Stage.show()` focuses by default. The rig monitor is shown once at startup and never re-shown programmatically — background events mark the switcher entry and wait (`00-client-overview.md` §6.2, "raising is never stealing"). This is a general rule that bites hardest on Windows.
2. **Aero Shake** minimises every window except the shaken one. Twelve of our thirteen will vanish and the player will think something broke. Nothing to fix, but geometry persistence must be written **on close, not on every move**, or a shake will overwrite a good layout with a minimised one.
3. **SmartScreen** will warn on an unsigned installer. Out of scope here — it belongs to the packaging `[PROPOSAL]` in `../architecture/01-tech-stack.md` §1 and the note at the bottom of `client/pom.xml` — but it is the first thing a Windows player experiences, so it should not be discovered late.
4. **`Alt` is contested** (§4.4). Verify the mitigation with a screen reader running, not only by eye.

---

## 5. Linux — "native" is not one thing, and pretending otherwise is the failure mode

### 5.1 The honest framing

There is no Linux HIG. GNOME (libadwaita: header bars, a close button only, no menu bar, a primary "hamburger" menu) and KDE Plasma (Breeze: conventional title bars with minimise/maximise/close, in-window menu bars, a system tray) are genuinely different design languages with different conventions — and Xfce, Cinnamon, MATE, and the tiling window managers are different again. `00-client-overview.md` §3.2 currently assigns Primer to every Linux session and **CL-1** already flags the question.

> **Position this document proposes, as a resolution to CL-1:** adapt **behaviour and chrome** per desktop environment where the signal is reliable; do **not** ship per-DE palettes in v1.
>
> The reasoning is a cost comparison, not a preference. A per-DE palette multiplies the contrast-verification surface (§2.5, `00-client-overview.md` §3.5 point 1, §4.4 point 3) for a benefit that amounts to "the window looks like Adwaita." Behaviour differences — where the menu is, whether a tray exists, whether the window manager will honour always-on-top — are what actually make an application feel foreign, and they are cheap to get right. If CL-1 is closed this way, log it in `../design/15-open-questions.md` §3.

### 5.2 Desktop-environment detection

| Source | Form | Reliability |
|---|---|---|
| `XDG_CURRENT_DESKTOP` | colon-separated list, e.g. `ubuntu:GNOME`, `KDE`, `XFCE`, `sway` | Primary. Match case-insensitively on any element |
| `DESKTOP_SESSION` | single token | Fallback when the above is empty |
| `GTK.theme_name` | `Adwaita`, `Breeze`, `Yaru`, … (**verified** as a reported key) | Corroboration only — a theme name is a *choice*, not an environment |

**Rule:** desktop detection may only affect the aspects enumerated in §5.6. It may never affect information architecture, terminology, game semantics, or the contrast floor (§1.2).

### 5.3 Dark-mode detection — the ladder, and why it needs one

**Verified:** on Linux, JavaFX derives `ColorScheme` from **`GTK.theme_name`**. That is a string heuristic, and it has a well-known blind spot: since GNOME 42, the desktop's light/dark *style* is a separate preference (`org.gnome.desktop.interface color-scheme` = `prefer-dark`) that does not necessarily change the GTK theme name — the theme may remain `Adwaita` while the whole desktop is dark. A theme-name heuristic then reports `LIGHT` on a dark desktop, and the game is the one bright window on the player's screen.

The ladder, in order, first confident answer wins:

1. **`Platform.getPreferences().getColorScheme()`.** Correct when the player selected `Adwaita-dark` or `Breeze-Dark` explicitly. Free.
2. **Relative luminance of `GTK.theme_bg_color`** (**verified** as a reported `Color` key). If the WCAG relative luminance of the GTK background is below 0.5, treat the session as dark. **This is the load-bearing step**, because the background colour changes when the style changes even when the theme *name* does not. It costs one arithmetic operation, needs no D-Bus, no subprocess, and no new dependency.
3. **The XDG desktop portal.** `org.freedesktop.portal.Settings.Read("org.freedesktop.appearance", "color-scheme")` — **verified** value semantics: `0` = no preference, `1` = prefer dark, `2` = prefer light. The same namespace also exposes `accent-color` as an `(ddd)` sRGB tuple and `contrast` as `0`/`1`, which would close **both** Linux gaps identified in §2.3 in one move. The cost is a D-Bus client: JavaFX has none, and **`00-client-overview.md` §7 bans subprocess spawning as a security boundary**, so shelling out to `gsettings`, `busctl` or `kreadconfig` is not available as a shortcut. A pure-Java D-Bus dependency is a real decision → **PN-1**.
4. **The in-app override**, which always exists (§2.5) and is the final answer.

⚠ **needs confirmation on a real KDE session:** KDE reports `GTK.theme_name` = `Breeze` for GTK applications, but the Qt/KDE colour scheme is configured separately. Step 2 should still work, because `kde-gtk-config` propagates the KDE colours into the GTK background — verify rather than assume (checklist LIN-4).

### 5.4 Wayland vs X11 — what actually differs for a thirteen-window client

**Verified:** JavaFX has **no native Wayland backend** as of JavaFX 26; on a Wayland session it runs under **XWayland**. Detection is `XDG_SESSION_TYPE` corroborated by `WAYLAND_DISPLAY`.

| Concern | X11 | Wayland (via XWayland) | What we do |
|---|---|---|---|
| **Window positioning** | `Stage.setX/setY` works | Goes through X11 to the compositor, which may adjust or ignore it; behaviour differs between Mutter, KWin and wlroots | **Geometry is a request, not a guarantee.** After `show()`, read back `getX/getY/getWidth/getHeight` and persist what actually happened (§3.8) |
| **`alwaysOnTop`** | Honoured by most window managers | Usually honoured by Mutter and KWin; **meaningless under tiling compositors** | See below |
| **Output scale** | Reported per screen | Compositors often apply fractional scaling themselves and hand X11 clients an integer scale, so a JavaFX window can look **upscaled and soft** on a 125 %/150 % desktop | Read `Screen.getOutputScaleX()`; never assume 1.0; verify at 1.0/1.25/1.5/2.0 |
| **Multi-monitor** | `Screen.getScreens()` reports the X layout | Same, with mixed-DPI as the weak spot | `00-client-overview.md` **CL-8** |

**The always-on-top problem deserves its own paragraph**, because pillar C2 depends on it. The rig monitor is the one window that must always be readable, and on Linux we cannot guarantee the window manager will float it — and, ⚠ as far as could be determined, **there is no API to read back whether `setAlwaysOnTop` took effect**. So we do not detect; we compensate:

- `Shortcut+0` raises the rig monitor and is unremappable (`00-client-overview.md` §6.3). A keystroke-initiated raise is honoured even by window managers with focus-stealing prevention, because it followed user input.
- The compact rig strip stays legible at small sizes precisely so it survives being tiled.
- **The docked layout (`00-client-overview.md` §6.4) is offered explicitly during first run on Linux**, and offered *by default* where a tiling environment is detected (§5.8).

And the strategic version of the same point: **if a native Wayland backend ever lands, positioning becomes impossible rather than merely unreliable** — Wayland clients cannot place their own surfaces. A thirteen-window client whose layout cannot be restored is a materially worse product. That is the single strongest argument in this doc set for making the docked layout *excellent* rather than merely *present*, which is `05`'s job.

### 5.5 Chrome and menus

- **`StageStyle.DECORATED` on Linux, always, in v1.** We take the window manager's title bar. On GNOME that yields a title bar with a close button, which is exactly what any non-GTK application gets there and is unremarkable. The alternative — `UNDECORATED` plus our own drag handling, or `EXTENDED` + `HeaderBar` (preview, §3.2) — buys a GNOME-shaped header bar at the cost of the right-click window menu, snapping, tiling integration, and correct behaviour across a dozen window managers we cannot test. That is not a close call.
- **Server-side decorations everywhere.** No client-side decorations, no per-DE decoration switching.
- **Menus: identical to Windows** (§4.3) — a full `MenuBar` on the rig monitor, a compact menu button on every other `Stage`, one shared model, plus the command palette. On GNOME this reads as an application's primary menu; on KDE it reads as an ordinary menu bar. **One implementation, two acceptable readings** — which is a much better outcome than two implementations.
- **We do not attempt global menus.** GNOME's appmenu is deprecated and KDE's global menu needs a D-Bus protocol. Neither is worth it.
- **`Alt` is a window-management modifier under GNOME** (Alt+drag moves a window). The §4.4 mitigation applies unchanged: attribution overlays are also on `Shortcut+Shift+A`.

### 5.6 What desktop detection is allowed to change

| Aspect | GNOME | KDE Plasma | Tiling WM | Other / unknown |
|---|---|---|---|---|
| Window style | `DECORATED` | `DECORATED` | `DECORATED` | `DECORATED` |
| Menu presentation | compact menu button reads as primary menu | menu bar on `rig-monitor`, buttons elsewhere | compact menu button | compact menu button |
| System tray offered | no (GNOME has no tray by default) | yes, as an option | no | no |
| `alwaysOnTop` attempted | yes | yes | **no** — meaningless | yes |
| Docked layout at first run | offered | offered | **offered as the default** | offered |
| Font resolution order | `01-visual-language.md` §3.2 chain | same chain, `Noto Sans` reordered first? → **PN-9** | §3.2 chain | §3.2 chain |

Everything not in this table is identical across desktop environments. That is the point of having the table.

### 5.7 The conservative fallback profile

What a session gets when nothing resolves — an unusual window manager, a container, a remote X session, a distribution nobody predicted:

`PrimerLight`/`PrimerDark` selected by the §5.3 ladder (defaulting to light, which is JavaFX's own default) · `DECORATED` stages · compact menu button on every window · no tray · JavaFX logical fonts `System` and `Monospaced` · `alwaysOnTop` attempted · geometry read back after `show()` · the in-app colour-scheme and reduced-motion overrides surfaced prominently during first run.

> **The fallback profile must be a good experience, not a degraded one.** It is what every unusual Linux setup gets, and "unusual Linux setup" describes a meaningful share of the audience for a self-hostable game about resisting a surveillance state. If the fallback feels like a punishment, we have mis-targeted the players most likely to run their own home server.

### 5.8 Linux gotchas worth knowing in advance

1. **The system UI font cannot be read.** The GTK key table JavaFX exposes contains colours and booleans and **no font key** (**verified** by its absence). GNOME's font preference lives in `gsettings`, which we cannot reach without a subprocess (banned) or D-Bus (**PN-1**). This is *why* `01-visual-language.md` §3.2 specifies a resolution chain rather than reading the system font: it is not a shortcut, it is the only mechanism available.
2. **Fonts are genuinely not guaranteed.** A minimal container, a NixOS install, or a Steam Deck may have neither Inter nor Cantarell nor JetBrains Mono. `System` and `Monospaced` always resolve (§3.5). Note the inversion this creates: because the story theme **bundles** its faces (`01-visual-language.md` §3.2), uOS is the *more* visually predictable family on a font-poor Linux box — the opposite of the intuition.
3. **Tiling window managers** (i3, sway, Hyprland) tile everything: thirteen windows become thirteen tiles and `alwaysOnTop` is meaningless. This is the strongest single argument in the client for the docked layout. Detection is imperfect — `XDG_CURRENT_DESKTOP` may report `sway` or `i3` — and where it succeeds, first run **offers the docked layout as the default** rather than opening thirteen windows into a tiler (§5.6).
4. **Fractional-scaling softness under XWayland** (§5.4). Verify at 125 % and 150 %; the mono readouts in the rig monitor are where it shows first.
5. **Flatpak and Snap sandboxing.** File dialogs route through `org.freedesktop.portal.FileChooser` when available (**verified**) — good, no work. But the profile directory (`00-client-overview.md` §4.5) lands inside the sandbox, at a path that differs per packaging format. Document where, or players will lose their window layouts on a repackage and blame the game.
6. **Focus-stealing prevention.** Several window managers refuse to raise a window that has not recently received user input. `Shortcut+0` is a keystroke and is safe. A programmatic raise from a background event is exactly what `00-client-overview.md` §6.2 already forbids — here it would not merely be rude, it would silently fail.
7. **Software rendering fallback.** If the `es2` pipeline cannot initialise, Prism falls back to software, and a thirteen-window client becomes sluggish. `-Dprism.verbose=true` is the diagnostic. **Never pin the pipeline in shipped code.**

---

## 6. Identical everywhere vs. allowed to differ

### 6.1 Identical — a platform may not change these

| Category | Specifics |
|---|---|
| Information architecture | The thirteen windows and their ids (`00-client-overview.md` §6.1); what each contains; every cross-window link |
| Terminology | `../design/glossary.md` names, verbatim, with their canonical capitalisation (`01-visual-language.md` §9.2); the Unix analogue vocabulary; every `man` entry |
| Game semantics | Every Layer 2 token's meaning and hue family; the five heat bands; the four authority states; the three provenance states; the five gates |
| Number and unit formats | `01-visual-language.md` §9.3 — the *shape* is fixed; only the locale's thousands separator and decimal mark vary |
| The token contract | All 69 colour tokens, all numeric tokens, all 10 primitives, all 17 state classes |
| Layout | Spacing scale, type scale, row heights, hit targets, density behaviour |
| Keyboard **actions** | Every action in `00-client-overview.md` §6.3 is reachable on every platform, with the same meaning |
| Accessibility | The floor in `00-client-overview.md` §3.5, unchanged; every accessible name, role and description |
| Copy | Every string, including error messages and empty states |

### 6.2 Allowed to differ — and the reason each one may

| Aspect | macOS | Windows | Linux | Why differing is correct |
|---|---|---|---|---|
| Base AtlantaFX theme | Cupertino | Primer | Primer | `00-client-overview.md` §3.2 |
| Corner radius step | one step up | default | default | Platform control language (§3.1); within the licence in `01-visual-language.md` §4.5 |
| Window buttons | traffic lights, top-left | caption buttons, top-right | WM's choice | Owned by the OS; drawing our own is the error |
| Menu placement | system menu bar | in-window, full bar on `rig-monitor` | in-window, full bar on `rig-monitor` | One menu bar exists per *application* on macOS and per *window* elsewhere |
| Mnemonics | none | yes | yes | macOS has no menu access keys |
| Shortcut modifier | ⌘ | Ctrl | Ctrl | `SHORTCUT_DOWN` (**verified**) |
| `` Shortcut+` `` | **not bound** — the OS does it | bound | bound | Same behaviour; different owner (§3.4) |
| Quit on last window closed | no | yes | yes | Platform convention |
| Selected-row fill source | OS selection colour | derived from accent ramp | derived from accent | macOS treats selection and accent as separate settings (§3.6) |
| Accent tone derivation | computed | **OS-supplied ramp** | JavaFX default, or portal (**PN-1**) | Take what the OS gives; compute only what it does not (§2.7) |
| Tray / dock idiom | Dock | taskbar group | per DE, often none | §5.6 |
| File dialog | `NSOpenPanel` | `IFileDialog` | GTK or portal | Native by delegation (**verified**) |
| UI and mono font | §3.5 | §4.5 | §5.8 | `01-visual-language.md` §3.2 |

### 6.3 Different mechanism, identical outcome

Three cases where the platform dictates *how* but not *what*. These are the ones most likely to be implemented on one platform and forgotten on the others.

1. **Quit during a live engagement always confirms.** `⌘Q` on macOS, `Alt+F4` and the caption close button on Windows, the WM close button on Linux. All three route through the same `setOnCloseRequest` path and the same window-scoped confirmation used by `Shortcut+.` (`00-client-overview.md` §6.3). Quitting mid-breach is a persisted `aborted` outcome with real consequences (`../design/05-hacking-minigame.md` §2, §4); losing a breach to a stray keystroke is not a consequence the design intended. Note that `Modality.APPLICATION_MODAL` remains banned — the confirmation is a `ModalPane` in the affected window (`01-visual-language.md` §5.3).
2. **The rig monitor is always one keystroke away.** `Shortcut+0` on all three. Always-on-top is a *reinforcement*, not the mechanism — because on Linux it may not work (§5.4) and on macOS it floats over other applications (§3.9).
3. **Attribution overlays are always reachable.** `Alt`-held where the platform permits, and `Shortcut+Shift+A` everywhere without exception (§3.4, §4.4).

---

## 7. Per-platform pre-ship checklist

Written to be executed, not read. Each item names how to verify it and what a pass looks like. Anything marked ⚠ in the body above appears here as a check.

### 7.1 Shared — run on all three platforms

| # | Check | How | Pass |
|---|---|---|---|
| SH-1 | Colour scheme follows the OS live | Flip the OS to dark with the client open, twelve windows and a scrolled log | Every window restyles within one frame after the 250 ms debounce; no window is missed; nothing reflows; scroll position, selection and text input survive |
| SH-2 | Pinned themes ignore the OS | Pin light, flip the OS to dark | Nothing changes; the switcher reads `pinned: light` |
| SH-3 | Contrast floor on the active theme | Run the §2.5 harness against every AtlantaFX variant, not only the two published palettes | 4.5:1 text, 3:1 non-text, on every surface each token can sit on |
| SH-4 | Greyscale test | Screenshot each window, desaturate (`01-visual-language.md` §2.4) | Every state distinction survives |
| SH-5 | Reduced motion | Enable it in the OS | Code-driven motion is *skipped*, not shortened; the switcher alert pulse is a static filled indicator |
| SH-6 | Accent guard | Set a saturated yellow or lime system accent | Links, primary buttons and the focus ring remain readable; nothing game-semantic changed hue |
| SH-7 | Window titles are tool-first | Read every title | `Rig Monitor — Eye and Sickle`, not the scaffold's current inverted form (§4.7) |
| SH-8 | Geometry read-back | Move and resize every window, quit, relaunch | Layout restores; nothing opens off-screen; a disconnected monitor does not strand a window |
| SH-9 | Quit-during-breach confirms | Start an engagement, trigger the platform's quit and close paths | All routes confirm; the rig monitor is never blocked by the dialog |
| SH-10 | Attribution without `Alt` | Press `Shortcut+Shift+A` | Overlays appear on every visible meter |
| SH-11 | No platform branching outside the seam | `grep -r 'os\.name'` in `client/src/main` | One hit, in `PlatformProfile` |
| SH-12 | Font chain resolved, not guessed | Log the resolved UI and mono families at startup | Both are real families reported by `Font.getFamilies()`; the logical fallback is reached only when expected |

### 7.2 macOS

| # | Check | How | Pass |
|---|---|---|---|
| MAC-1 | System menu bar, one model | Focus each of the thirteen windows in turn | The menu bar's *shape* never changes; only enablement does |
| MAC-2 | No duplicated platform items | Inspect every menu | No Quit, About or Settings outside the app menu |
| MAC-3 | App menu title | Launch the bundled app | Reads `Eye and Sickle` (14 chars, within the **verified** 16-char limit) |
| MAC-4 | `` ⌘` `` is not bound by us | Press it with three windows open | The OS cycles them; the client does not double-handle |
| MAC-5 | Traffic lights and full screen | Zoom, minimise, full-screen each window | All behave as platform-standard; nothing is drawn by us |
| MAC-6 | Selection colour | Change the macOS highlight colour; select a ledger row | `-es-accent-subtle` matches the OS selection tint (§3.6) |
| MAC-7 | Reduced transparency | Enable it in Accessibility → Display | Scrims opaque; overlay surfaces lose translucency |
| MAC-8 | File dialog owner chain | Export a ledger from `ledger` while a breach runs | Only `ledger` blocks; the rig monitor stays live and readable |
| MAC-9 | ⚠ `NSRequiresAquaSystemAppearance` | Inspect the built bundle's `Info.plist` | Absent, or explicitly `false` (§3.6) |
| MAC-10 | ⚠ Font family strings | Log `Font.getFamilies()` on a real Mac | Correct `01-visual-language.md` §3.2's macOS chain and close **V-3** |
| MAC-11 | Retina and mixed scale | Drag every window between a 2.0 and a 1.0 display | No blur, no clipped text, no lost geometry |
| MAC-12 | Always-on-top is explained | First run | The rig monitor's over-other-apps behaviour is stated and revocable (§3.9) |

### 7.3 Windows

| # | Check | How | Pass |
|---|---|---|---|
| WIN-1 | Snap Layouts | Hover the maximise button | Layout zones appear; four tool windows arrange in one gesture |
| WIN-2 | ⚠ **Dark caption** | Set app mode to dark; observe the title bar | Dark. If light, take a decision from the three options in §4.2 and close **PN-8** |
| WIN-3 | `Alt` does not break attribution | Hold `Alt`, release without a second key | Overlay appeared; the menu bar is left in its normal state; the next keystroke is not swallowed |
| WIN-4 | Accent ramp from the OS | Change the system accent | `-es-accent-muted` / `-es-accent-subtle` follow the OS ramp, not a derived one (§4.6) |
| WIN-5 | High contrast | Enable a high-contrast theme | The §2.8 treatment applies; no `*-subtle` fill remains load-bearing |
| WIN-6 | Two dark toggles | Change "Windows mode" only, leaving app mode light | The client does **not** flip |
| WIN-7 | ⚠ Optical sizes | Log `Font.getFamilies()` | Text and Display variants resolve; `TYPE_DISPLAY` uses Display (§4.5) |
| WIN-8 | ⚠ Text on dark | Read `TYPE_MONO_BODY` on `-es-surface-sunken` | No colour fringing; no `prism.*` property set in shipped code |
| WIN-9 | No focus theft | Trigger a background alert while another app is focused | The switcher entry marks; nothing raises |
| WIN-10 | Fractional scaling | View a dense table at 150 % | No boundary is carried by a hairline alone (§4.8) |
| WIN-11 | Aero Shake | Shake one window | The others minimise; relaunching restores the saved layout, not the minimised one |
| WIN-12 | Taskbar labels | Hover the grouped icon | Tool names are readable before truncation (SH-7) |

### 7.4 Linux

| # | Check | How | Pass |
|---|---|---|---|
| LIN-1 | GNOME dark style | GNOME 42+, set Dark in Settings **without** changing the GTK theme | The client goes dark — i.e. ladder step 2 fired (§5.3) |
| LIN-2 | Explicit dark theme | Select `Adwaita-dark` | The client goes dark via ladder step 1 |
| LIN-3 | In-app override | Force light on a dark desktop | Honoured, and the switcher says `pinned: light` |
| LIN-4 | ⚠ KDE | Repeat LIN-1 on Plasma with Breeze Dark | The client goes dark; if not, the luminance step needs a KDE-specific source |
| LIN-5 | XWayland | Run under a Wayland session | Windows open, position, resize and restore; geometry read-back matches what the compositor granted |
| LIN-6 | Fractional scaling | 125 % and 150 % under Wayland | Mono readouts in `rig-monitor` remain crisp enough to read at `TYPE_MONO_READOUT` |
| LIN-7 | Tiling WM | Launch under sway or i3 | Docked layout is offered as the default; the client is usable tiled |
| LIN-8 | Always-on-top absent | Any WM that ignores it | `Shortcut+0` still raises the rig monitor instantly |
| LIN-9 | Font-poor system | A minimal container with no Inter, Cantarell or JetBrains Mono | Logical `System` / `Monospaced` resolve; nothing renders as boxes; `uos` still renders in its bundled faces |
| LIN-10 | Accent absence | Any Linux session | The default `#157EFB` is used and reads correctly; no game-semantic token moved (§2.3) |
| LIN-11 | Flatpak / Snap | Install both packagings | File dialogs open through the portal; the profile directory path is documented |
| LIN-12 | Software fallback | Force `-Dprism.order=sw` once | The client still runs; no pipeline property is pinned in shipped code |

---

## 8. Open questions

Deliberately undecided here. Prefix `PN-` chosen to avoid collision with `CL-` (`00-client-overview.md`), `V-` (`01-visual-language.md`) and the existing prefixes in `../design/15-open-questions.md`. Log there in §2 if this doc set is adopted.

- **PN-1: Do we take a pure-Java D-Bus dependency to read the XDG desktop portal on Linux?** It is the only route to a correct accent colour on Linux (**verified**: no GTK accent mapping exists), and it also supplies `contrast` and a portal-grade `color-scheme` (**verified** value semantics in §5.3). Against it: a new runtime dependency, on a bus that may not be present, in a client whose non-goals include arbitrary subprocesses. The §5.3 luminance step means we are *correct* without it and merely *less native*. Decide before Linux first-run copy is written, because the answer changes what the appearance settings pane must offer.
- **PN-2: Generate and assert the Layer 2 palette per AtlantaFX theme (§2.5).** `01-visual-language.md` publishes two native palettes measured against Primer canvases, but macOS ships Cupertino and Nord/Dracula are offered manually — five of seven themes are currently unverified against the contrast floor. **This is a prerequisite for shipping macOS at all**, not a nice-to-have, and it subsumes **V-2**.
- **PN-3: When does `StageStyle.EXTENDED` + `HeaderBar` become safe to adopt?** **Verified** as a preview API in JavaFX 25 and still preview-deprecated in 26. It would give the rig monitor a header-bar readout on macOS and could solve the Windows dark-caption problem. Revisit when it leaves preview; do not adopt for `rig-monitor` before then (pillar C2).
- **PN-4: OS-level attention — notifications, Dock badges, taskbar overlays.** v1 ships none, and pillar C5's "alerts accrete, they never steal focus" makes notifications actively wrong. A *badge* is different: attention without interruption, which is exactly what a triage model wants. The blocker is that `java.awt.Taskbar` and `SystemTray` pull AWT into a JavaFX process. Worth revisiting once, deliberately.
- **PN-5: What should high contrast actually do (§2.8)?** Our own harder palette is verifiable; the platform's real high-contrast colours are more correct and unverifiable. Related: ⚠ confirm whether Windows maps `reducedTransparency` at all (§2.3), since `Windows.UISettings.AdvancedEffectsEnabled` is the obvious source and was not listed in this pass.
- **PN-6: Does the native family need a named `native-hc` variant?** The story family has `uos-hc` as a first-class id (`00-client-overview.md` §3.3). If high contrast is a modifier applicable to any variant instead, the theme-id grammar in §2.4 needs a modifier slot. Decide with **PN-5**; they are the same decision seen from two ends.
- **PN-7: ⚠ Is there any public JavaFX API for the macOS application menu's items?** Only third-party JNA libraries surfaced in this pass. If not, we live with the platform defaults for About and Settings, which is acceptable — but it should be a decision, not a discovery during macOS polish.
- **PN-8: ⚠ Does JavaFX 26 opt into the Windows 11 immersive dark title bar (§4.2)?** Must be measured on a real Windows 11 build ≥ 22000. It is the most likely cosmetic defect report on the platform with the largest desktop share, and the three remedies have very different costs.
- **PN-9: Per-desktop-environment font resolution order on Linux (§5.6).** `Cantarell` first on GNOME and `Noto Sans` first on KDE would be more native than one chain, at the cost of one more branch. `01-visual-language.md` §3.2 owns the chain, so this must be agreed there rather than forked here.
- **PN-10: Should the `switcher` (and the compact rig strip) use `StageStyle.UTILITY`?** It is literally a tool palette, which is what UTILITY is for. But UTILITY restricts minimise/maximise on some platforms, and "closing the rig monitor minimises it to a strip" (pillar C2) depends on those controls existing. Needs testing on all three before it is worth the risk.
- **PN-11: Tiling-window-manager detection (§5.8).** `XDG_CURRENT_DESKTOP` reporting `sway`/`i3`/`Hyprland` is a weak signal with a long tail. Getting it wrong in the *offering* direction is harmless (we suggest the docked layout to someone who did not need it); getting it wrong in the other direction opens thirteen windows into a tiler. Bias the heuristic accordingly, and confirm the list of tokens worth matching.
- **PN-12: Does the native family need per-platform first-run copy?** §3.9, §4.9 and §5.8 each contain something a player genuinely needs told once — always-on-top over other apps on macOS, Snap Layouts on Windows, the docked layout on a tiler. That is three platform-conditional strings in an onboarding flow `07` owns, and §1.2 forbids per-platform *game* copy. The boundary between "platform guidance" and "copy" needs stating before `07` is written.

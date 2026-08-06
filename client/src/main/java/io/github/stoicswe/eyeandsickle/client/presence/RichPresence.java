package io.github.stoicswe.eyeandsickle.client.presence;

import io.github.stoicswe.eyeandsickle.client.events.EventTypes;
import io.github.stoicswe.eyeandsickle.client.session.GameSession;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tells Discord, and only Discord, roughly what the player is doing — when they have asked it to.
 *
 * <h2>⚠ OFF BY DEFAULT, and it stays off until somebody deliberately turns it on</h2>
 *
 * This is the one thing in the client that transmits anything about the player to a party that is
 * not a home server. {@code docs/client/00-client-overview.md} §7's <b>"not a telemetry client"</b>
 * non-goal was amended for it rather than quietly stretched, and {@code docs/client/02} §2.9's
 * exhaustive outbound list grew from nothing to one entry. The amendment's argument, in short: §7
 * governs <em>collection</em> — the game gathering facts and sending them somewhere the player did
 * not choose — and every clause of that inverts here. It is opt-in, off by default, it goes to a
 * program the player installed and is already running, on their own account, and nothing whatsoever
 * reaches this project's infrastructure. The precedent already in the tree is
 * {@code stocks/HttpStockFeed}: an outbound connection to a third party the player picked, dark
 * until they supply the credential themselves.
 *
 * <h2>⚠ What it may say is fixed in {@link PresenceState} and cannot be widened here</h2>
 *
 * {@link #activity} takes a {@code PresenceState} and an {@code Instant}. It is not given a
 * {@code GameSession} and there is nothing in scope for it to read one from, so no handle, balance,
 * address, item or standing can reach the wire without somebody changing this method's signature —
 * which is a review-visible act rather than an interpolation inside a format string.
 * {@code PresenceLeakTest} drives every state with probe values and asserts they never appear.
 *
 * <p>⚠ The payload does carry this process's <b>pid</b>, because the protocol requires it: Discord
 * uses it to know which running program owns an activity and to clear it if that program dies. It is
 * a small integer that means nothing off this machine.
 *
 * <h2>⚠ A process-wide singleton, like {@code ClientLog} and for the same reason</h2>
 *
 * The Settings panel toggles it, {@code EyeAndSickleClient} attaches sessions to it, and its
 * lifetime is the process rather than any character's. Threading one instance through six
 * constructors to reach a settings page would be the only argument for doing otherwise.
 * {@code CLAUDE.md} records that this codebase is wary of static state — the caveat there is
 * {@code Tickers}, and it applies to <b>things that could influence a rule</b>. This influences
 * nothing: it holds no game state, decides nothing, and is pure output.
 *
 * <h2>⚠ ONE update per {@link #MIN_INTERVAL}, coalesced — this is Discord's limit, not a preference</h2>
 *
 * Discord accepts roughly one activity update per fifteen seconds and silently drops the rest. Focus
 * events arrive far faster than that when somebody is cycling windows, so the worker holds the
 * <em>latest wanted</em> state and sends that when the window opens — never a queue, which would
 * replay a stale sequence minutes behind the player. It is also what makes a window closing safe to
 * report as {@link PresenceState#DECK}: the focus event that follows overwrites it long before
 * anything is transmitted.
 *
 * <h2>⚠ Two clocks, deliberately, and this is the documented inversion of the house rule</h2>
 *
 * The rule everywhere else in this client is that anything with a deadline takes the session's
 * clock. Neither clock here is a game deadline, so neither takes it:
 *
 * <ul>
 *   <li>the <b>pacing</b> uses {@link System#nanoTime()}, because the question is how long
 *       <em>this machine</em> has taken and a wound-back wall clock would let it hammer Discord —
 *       the same reasoning {@code ui/chrome/Frost} records for its budget;
 *   <li>the <b>elapsed timestamp</b> uses {@link Instant#now()}, because Discord renders it as real
 *       time in front of another human. Under a test clock the session clock would tell somebody's
 *       friends the player started in 2026.
 * </ul>
 */
public final class RichPresence implements AutoCloseable {

    private static final Logger LOG = Logger.getLogger(RichPresence.class.getName());

    private static final ObjectMapper MAPPER = JsonMapper.builder().build();

    private static final RichPresence INSTANCE = new RichPresence();

    /**
     * Run-time override for the Discord application id.
     *
     * <p>{@code -Deyeandsickle.discord.appId=…}. Wins over the built-in one, so a packaged client can
     * be pointed at a different application without a rebuild — which is how this gets exercised
     * against a test application, and how a fork uses its own.
     */
    public static final String APPLICATION_ID_PROPERTY = "eyeandsickle.discord.appId";

    /** Where the build-time id lands. The one Maven-filtered resource; see {@code client/pom.xml}. */
    private static final String BUILD_PROPERTIES = "/io/github/stoicswe/eyeandsickle/client/build.properties";

    /**
     * The id Maven filtered in, resolved once.
     *
     * <h2>⚠ A FILTERED RESOURCE, for the reason {@code SystemReport.clientVersion} records</h2>
     *
     * The client runs from loose classes in an IDE, from a shaded jar and from a jpackage image, and
     * a resource is present in all three where a jar manifest is present in one. Reading it once is
     * safe because it cannot change while the process runs — unlike the system property above, which
     * is read fresh every time so a test can set it.
     *
     * <h2>⚠ BLANK IS A SUPPORTED STATE, not a missing step</h2>
     *
     * The id is public — it identifies the game to Discord and is not a credential — but it belongs
     * to whoever registered the application, so a fork and a contributor's local build both have
     * none. With it blank the feature turns itself off with one {@code FINE} line, the Settings
     * switch is disabled and says why, and the whole suite still passes. An unfiltered copy still
     * holds the literal {@code ${…}} placeholder, which is treated as blank rather than sent to
     * Discord as an application id.
     */
    private static final String BUILT_IN_APPLICATION_ID = readBuiltInApplicationId();

    /**
     * The art the portal serves for this activity.
     *
     * <p>⚠ An asset <em>key</em>, not a URL — the image is uploaded to the Discord application's Art
     * Assets and named there. A key with nothing behind it renders as no image, which is why this
     * being wrong is invisible in every test and only shows on somebody else's screen.
     */
    static final String LARGE_IMAGE = "uos";

    static final String LARGE_TEXT = "The Eye and Sickle";

    /** Discord's own ceiling on activity updates. Sending faster is dropped, not queued. */
    public static final Duration MIN_INTERVAL = Duration.ofSeconds(15);

    /**
     * How long to wait before looking for Discord again after failing to find it.
     *
     * <p>⚠ Not finding it is the ordinary case — most players will not have it running — so the
     * retry has to be cheap to be wrong about. Scanning up to two hundred candidate paths every time
     * the player changes window would be a directory-stat storm in exchange for noticing Discord
     * starting a few seconds sooner.
     */
    static final Duration RECONNECT_BACKOFF = Duration.ofSeconds(60);

    private final Object lock = new Object();

    /** ⚠ Guarded by {@link #lock}: what the player is doing now. */
    private PresenceState desired = PresenceState.MENU;

    /** ⚠ Guarded by {@link #lock}: what Discord was last told, or null if nothing yet. */
    private PresenceState published;

    /** ⚠ Guarded by {@link #lock}. Monotonic — see the class note on the two clocks. */
    private long lastSentNanos = Long.MIN_VALUE;

    /** ⚠ Guarded by {@link #lock}. */
    private long reconnectNotBeforeNanos = Long.MIN_VALUE;

    /** ⚠ Guarded by {@link #lock}. */
    private boolean running;

    /** ⚠ Guarded by {@link #lock}. */
    private Thread worker;

    /** ⚠ Guarded by {@link #lock}. */
    private Transport transport;

    /** ⚠ Guarded by {@link #lock}. The bus subscription, or null when no session is attached. */
    private AutoCloseable subscription;

    /** How long the client has been up, for the elapsed line Discord draws. Wall clock, see above. */
    private volatile Instant since = Instant.now();

    private volatile boolean connected;

    /**
     * ⚠ Swappable so the leak test can drive this without Discord installed. Package-private and
     * never reassigned in production code.
     */
    private volatile Supplier<Transport> connector = this::connectToDiscord;

    /**
     * ⚠ Package-private test seam. Null means "resolve normally"; {@code ""} means "this build has no
     * id", which is a state a test could not otherwise reach once the pom carries a real one.
     */
    private volatile String applicationIdForTest;

    private RichPresence() {}

    /** The one instance. */
    public static RichPresence shared() {
        return INSTANCE;
    }

    // ── the switch ─────────────────────────────────────────────────────────────────────────────

    /**
     * Turns presence on or off.
     *
     * <p>Safe to call from the FX thread and from any other: it starts or interrupts a thread and
     * never waits for a pipe. Turning it off <b>clears the activity</b> rather than merely stopping
     * updates — a presence left frozen on "At a terminal" after the player switched it off is the
     * feature continuing to say something about them, which is the opposite of what they asked for.
     */
    public void setEnabled(boolean enabled) {
        if (enabled) {
            start();
        } else {
            stop();
        }
    }

    public boolean isEnabled() {
        synchronized (lock) {
            return running;
        }
    }

    /** Whether a pipe to Discord is currently open. Read by Settings, which explains the answer. */
    public boolean isConnected() {
        return connected;
    }

    /**
     * The configured application id, or blank when there is none.
     *
     * <h2>⚠ AN INSTANCE METHOD, AND IT WAS STATIC UNTIL A REAL ID WAS CONFIGURED</h2>
     *
     * As a static reading only the property and the resource, a test could express "no id" only by
     * clearing the system property — which works exactly while {@code <discord.app.id>} in the pom is
     * empty, and stops the moment somebody fills it in. That is a test coupled to the build
     * configuration, and it failed the first time the id was set, which is the first time it
     * mattered. Worse than the red build: with the built-in id satisfying the check, that test
     * started the worker holding the <b>real</b> connector and would have opened a pipe to the
     * developer's own Discord — the side effect {@code DiscordIpcTest} refuses by not calling
     * {@code connect} at all.
     *
     * <p>⚠ The run-time property is read <b>fresh every call</b> and the built-in one is cached. The
     * asymmetry is deliberate: the property is an override somebody sets to change behaviour, and a
     * cached one could not be changed; the resource is a build artefact that cannot change while the
     * process runs.
     */
    public String applicationId() {
        String forced = applicationIdForTest;
        if (forced != null) {
            return forced.trim();
        }
        String override = System.getProperty(APPLICATION_ID_PROPERTY, "").trim();
        return override.isEmpty() ? BUILT_IN_APPLICATION_ID : override;
    }

    private static String readBuiltInApplicationId() {
        try (java.io.InputStream in = RichPresence.class.getResourceAsStream(BUILD_PROPERTIES)) {
            if (in == null) {
                return "";
            }
            java.util.Properties properties = new java.util.Properties();
            properties.load(in);
            String id = properties.getProperty("discordAppId", "").trim();
            // An unfiltered copy still holds "${discord.app.id}". Handing that to Discord as an
            // application id would be a handshake that fails for a reason nothing on screen explains.
            return id.startsWith("${") ? "" : id;
        } catch (java.io.IOException | RuntimeException absent) {
            return "";
        }
    }

    private void start() {
        synchronized (lock) {
            if (running) {
                return;
            }
            if (applicationId().isEmpty()) {
                // ⚠ FINE, not WARNING. A build with no id configured is a normal build, and this
                // would otherwise be a warning in every contributor's log for a feature they never
                // asked for. Settings says the same thing where somebody is actually looking.
                LOG.log(Level.FINE, "discord presence not started: no application id configured");
                return;
            }
            running = true;
            since = Instant.now();
            published = null;
            lastSentNanos = Long.MIN_VALUE;
            reconnectNotBeforeNanos = Long.MIN_VALUE;
            // ⚠ A virtual thread, and it does nothing but block on a pipe. The blocking reads in
            // DiscordIpc are the reason it exists at all: they must not be on the FX thread and they
            // must not be on a pool anything else shares.
            worker = Thread.ofVirtual().name("discord-presence").start(this::run);
            LOG.log(Level.INFO, "discord presence enabled");
        }
    }

    private void stop() {
        Thread stopping;
        synchronized (lock) {
            if (!running) {
                return;
            }
            running = false;
            stopping = worker;
            worker = null;
            lock.notifyAll();
        }
        if (stopping != null) {
            // ⚠ Interrupt as well as notify. The worker may be parked inside a blocking read on the
            // pipe rather than on the monitor, and only closing the channel unblocks that — which is
            // what the worker does on its way out.
            stopping.interrupt();
        }
        LOG.log(Level.INFO, "discord presence disabled");
    }

    // ── what the player is doing ───────────────────────────────────────────────────────────────

    /** Records the state to report. Cheap, non-blocking, and safe from the FX thread. */
    public void show(PresenceState state) {
        if (state == null) {
            return;
        }
        synchronized (lock) {
            if (desired == state) {
                return;
            }
            desired = state;
            lock.notifyAll();
        }
    }

    /**
     * Follows a session's desk events, so presence tracks the focused tool with no per-view wiring.
     *
     * <h2>⚠ Subscribed at the BUS, not at the call sites</h2>
     *
     * The desk already narrates itself — {@code DeskManager} publishes opened, raised, focused and
     * closed at one chokepoint — so this needs no hook in fourteen views and cannot fall behind a
     * fifteenth. That is the same argument {@code EventRecorder} makes for subscribing in the bus's
     * own constructor.
     *
     * <p>⚠ The subscription is <b>closed</b> in {@link #detach}. {@code EventBus}'s class comment is
     * explicit that a bus which cannot be detached from leaks a listener per subscriber, and a
     * session outliving its presence hook would keep a dead character's windows driving the report.
     */
    public void attach(GameSession session) {
        detach();
        if (session == null) {
            return;
        }
        AutoCloseable handle = session.events().subscribe(EventTypes.of(EventTypes.WINDOW), event -> {
            String what = event.shortType();
            if (what.endsWith(".closed")) {
                // Nothing says what has focus now; the focus event that follows corrects it, and the
                // coalescing window means this intermediate value is never transmitted.
                show(PresenceState.DECK);
                return;
            }
            show(PresenceState.forWindowId(event.subject()));
        });
        synchronized (lock) {
            subscription = handle;
        }
        show(PresenceState.DECK);
    }

    /** Stops following a session and falls back to the menu. */
    public void detach() {
        AutoCloseable closing;
        synchronized (lock) {
            closing = subscription;
            subscription = null;
        }
        if (closing != null) {
            try {
                closing.close();
            } catch (Exception ignored) {
                LOG.log(Level.FINEST, "presence subscription did not close cleanly");
            }
        }
        show(PresenceState.MENU);
    }

    // ── the worker ─────────────────────────────────────────────────────────────────────────────

    private void run() {
        try {
            while (true) {
                PresenceState send;
                synchronized (lock) {
                    while (running && desired == published) {
                        lock.wait();
                    }
                    if (!running) {
                        break;
                    }
                    long waitMs = millisUntilAllowed();
                    if (waitMs > 0) {
                        lock.wait(waitMs);
                        // ⚠ Re-loop rather than sending: `desired` may have changed again while we
                        // waited, and the whole point of coalescing is that the newest one wins.
                        continue;
                    }
                    send = desired;
                }
                if (deliver(send)) {
                    synchronized (lock) {
                        published = send;
                        lastSentNanos = System.nanoTime();
                    }
                }
            }
        } catch (InterruptedException stopping) {
            Thread.currentThread().interrupt();
        } finally {
            clearAndDisconnect();
        }
    }

    /** How long before another update is allowed. ⚠ Monotonic; see the class note on clocks. */
    private long millisUntilAllowed() {
        if (lastSentNanos == Long.MIN_VALUE) {
            return 0;
        }
        long elapsed = System.nanoTime() - lastSentNanos;
        long remaining = MIN_INTERVAL.toNanos() - elapsed;
        return remaining <= 0 ? 0 : TimeUnit.NANOSECONDS.toMillis(remaining) + 1;
    }

    /** @return whether the state actually reached Discord. */
    private boolean deliver(PresenceState state) {
        Transport open = ensureConnected();
        if (open == null) {
            return false;
        }
        try {
            open.send(activity(state, since));
            return true;
        } catch (IOException | RuntimeException gone) {
            // ⚠ Drop the connection rather than retrying on it. A pipe that has failed once is a pipe
            // Discord has closed, and writing into it forever while reporting `connected` is the
            // failure this branch exists to prevent.
            LOG.log(Level.FINE, "discord presence update failed: {0}", gone.getClass().getSimpleName());
            disconnect();
            synchronized (lock) {
                reconnectNotBeforeNanos = System.nanoTime() + RECONNECT_BACKOFF.toNanos();
            }
            return false;
        }
    }

    private Transport ensureConnected() {
        synchronized (lock) {
            if (transport != null) {
                return transport;
            }
            if (reconnectNotBeforeNanos != Long.MIN_VALUE && System.nanoTime() < reconnectNotBeforeNanos) {
                return null;
            }
        }
        Transport opened = connector.get();
        synchronized (lock) {
            if (!running) {
                // Switched off while we were connecting. Close it rather than storing it, or the
                // toggle leaves a live pipe behind.
                closeQuietly(opened);
                return null;
            }
            if (opened == null) {
                reconnectNotBeforeNanos = System.nanoTime() + RECONNECT_BACKOFF.toNanos();
                connected = false;
                return null;
            }
            transport = opened;
            connected = true;
            return transport;
        }
    }

    /** ⚠ Clears the activity before closing, so nothing is left standing about the player. */
    private void clearAndDisconnect() {
        Transport open;
        synchronized (lock) {
            open = transport;
        }
        if (open != null) {
            try {
                open.send(clearActivity());
            } catch (IOException | RuntimeException alreadyGone) {
                LOG.log(Level.FINEST, "discord presence not cleared");
            }
        }
        disconnect();
    }

    private void disconnect() {
        Transport closing;
        synchronized (lock) {
            closing = transport;
            transport = null;
            connected = false;
        }
        closeQuietly(closing);
    }

    private static void closeQuietly(Transport transport) {
        if (transport == null) {
            return;
        }
        try {
            transport.close();
        } catch (IOException | RuntimeException ignored) {
            LOG.log(Level.FINEST, "discord transport did not close cleanly");
        }
    }

    private Transport connectToDiscord() {
        return DiscordIpc.connect(applicationId());
    }

    // ── the payload ────────────────────────────────────────────────────────────────────────────

    /**
     * The {@code SET_ACTIVITY} command for one state.
     *
     * <h2>⚠ ITS PARAMETERS ARE THE PRIVACY GUARANTEE</h2>
     *
     * A {@code PresenceState} and an {@code Instant}, and nothing else is in scope. There is no
     * session here to read a handle from, no engine to read a balance from and no host to read an
     * address from — so the set of things this can transmit is the set of constants in
     * {@link PresenceState}. Adding a parameter is the only way to change that, and a reviewer
     * cannot miss one.
     *
     * <p>⚠ Serialised with Jackson rather than concatenated. Hand-built JSON is how a quote in a
     * value silently produces a malformed frame, and this codebase already has {@code Jsonb.CAST}
     * recorded as an entry about trusting a string where a document was meant.
     */
    static String activity(PresenceState state, Instant since) {
        Map<String, Object> assets = new LinkedHashMap<>();
        assets.put("large_image", LARGE_IMAGE);
        assets.put("large_text", LARGE_TEXT);

        Map<String, Object> activity = new LinkedHashMap<>();
        activity.put("details", state.details());
        activity.put("timestamps", Map.of("start", since.getEpochSecond()));
        activity.put("assets", assets);

        return command(activity);
    }

    /** ⚠ A null activity is how the protocol says "nothing"; an empty object leaves a blank card up. */
    static String clearActivity() {
        return command(null);
    }

    private static String command(Map<String, Object> activity) {
        Map<String, Object> args = new LinkedHashMap<>();
        // Required by the protocol: it is how Discord knows which process owns the activity, and how
        // it clears one whose process died. Machine-local and meaningless anywhere else.
        args.put("pid", ProcessHandle.current().pid());
        args.put("activity", activity);

        Map<String, Object> command = new LinkedHashMap<>();
        command.put("cmd", "SET_ACTIVITY");
        command.put("nonce", UUID.randomUUID().toString());
        command.put("args", args);
        return MAPPER.writeValueAsString(command);
    }

    // ── what Settings says ─────────────────────────────────────────────────────────────────────

    /**
     * One line describing the current state, for the Settings panel.
     *
     * <p>⚠ It distinguishes "off", "no id in this build" and "on but Discord is not running",
     * because those have three different fixes and a switch that appears to do nothing reads as
     * broken — the rule the rounded-corners setting already records.
     */
    public String describe() {
        if (applicationId().isEmpty()) {
            return "Unavailable in this build: no Discord application id is configured.";
        }
        if (!isEnabled()) {
            return "Off. Nothing is sent.";
        }
        return connected
                ? "On, and connected to Discord."
                : "On. Discord is not running on this machine, so nothing is being sent.";
    }

    /** Stops the worker and closes the pipe. Called from the client's own shutdown. */
    @Override
    public void close() {
        stop();
    }

    // ── test seam ──────────────────────────────────────────────────────────────────────────────

    /** ⚠ Package-private, for {@code PresenceLeakTest}. Production code never calls this. */
    void useTransport(Supplier<Transport> connector) {
        this.connector = connector == null ? this::connectToDiscord : connector;
    }

    /**
     * ⚠ Package-private. Forces the answer {@link #applicationId()} gives.
     *
     * <p>{@code null} restores normal resolution; {@code ""} is "this build has no id", which is the
     * state a test cannot otherwise reach once {@code <discord.app.id>} is filled in. Production code
     * never calls this.
     */
    void useApplicationId(String id) {
        this.applicationIdForTest = id;
    }

    /** ⚠ Package-private. Restores the singleton so one test cannot leak into the next. */
    void reset() {
        stop();
        useTransport(null);
        useApplicationId(null);
        synchronized (lock) {
            desired = PresenceState.MENU;
            published = null;
            connected = false;
        }
    }
}

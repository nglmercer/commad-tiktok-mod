package dev.nglmercer.tiktokminecraft.tiktok;

import dev.nglmercer.tiktokminecraft.config.PluginConfig;
import dev.nglmercer.tiktokminecraft.event.LiveEventNormalizer;
import dev.nglmercer.tiktokminecraft.event.TikTokEventContext;
import dev.nglmercer.tiktokminecraft.log.LogCategory;
import dev.nglmercer.tiktokminecraft.log.PluginLog;
import io.github.nglmercer.tiktoklive.EmbeddedJavaConnectionProvider;
import io.github.nglmercer.tiktoklive.LiveState;
import io.github.nglmercer.tiktoklive.TikTokLive;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Owns the TikTok LIVE connection.
 *
 * <p>The connection is direct: discovery and signing happen inside the
 * process through the SDK's embedded Java signer. No broker, no native
 * library and no external process is involved.</p>
 *
 * <h2>Threading</h2>
 * <p>SDK callbacks arrive on the SDK's own event thread. They only normalize
 * the event and hand it to the consumer; every Minecraft operation happens
 * later on the server thread. {@link #connect()} never blocks the caller, so
 * it is safe to call it from the server thread.</p>
 */
public final class TikTokLiveService implements AutoCloseable {

    /** Receives normalized events and connection state changes. */
    public interface Listener {
        void onEvent(TikTokEventContext event);

        void onState(LiveConnectionState state, String detail);
    }

    /** Minimum delay between automatic reconnect attempts. */
    private static final long RECONNECT_BASE_DELAY_SECONDS = 5L;
    /** Maximum delay between automatic reconnect attempts. */
    private static final long RECONNECT_MAX_DELAY_SECONDS = 120L;
    /** How often an offline creator is checked again. */
    private static final long OFFLINE_RETRY_SECONDS = 60L;

    private final PluginLog log;
    private final Path cacheDirectory;
    private final Listener listener;
    private final ScheduledExecutorService timers;
    private final AtomicBoolean connecting = new AtomicBoolean(false);
    private final AtomicInteger reconnectAttempts = new AtomicInteger();

    private volatile PluginConfig config;
    private volatile TikTokLive live;
    private volatile EmbeddedJavaConnectionProvider provider;
    private volatile LiveConnectionState state = LiveConnectionState.IDLE;
    private volatile String roomId = "";
    private volatile boolean stopped;
    private volatile boolean manualDisconnect;
    private volatile ScheduledFuture<?> pendingRetry;

    public TikTokLiveService(
            PluginLog log, Path cacheDirectory, Listener listener, PluginConfig config) {
        this.log = log;
        this.cacheDirectory = cacheDirectory;
        this.listener = listener;
        this.config = config;
        this.timers = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tiktokminecraft-timers");
            thread.setDaemon(true);
            return thread;
        });
    }

    /** The current configuration used by the connection. */
    public void applyConfig(PluginConfig config) {
        this.config = config;
    }

    public LiveConnectionState state() {
        return state;
    }

    public String roomId() {
        return roomId;
    }

    public int reconnectAttempts() {
        return reconnectAttempts.get();
    }

    public boolean isConnected() {
        return state == LiveConnectionState.CONNECTED;
    }

    /**
     * Starts the connection.
     *
     * <p>Safe to call from the server thread: the SDK's connect call is
     * asynchronous and this method returns immediately. A second call while a
     * connection is already active is ignored, so duplicate connections are
     * impossible.</p>
     */
    public void connect() {
        if (stopped) {
            return;
        }
        if (state == LiveConnectionState.CONNECTED) {
            log.debug(LogCategory.CONNECTION, "Already connected; ignoring request");
            return;
        }
        if (!connecting.compareAndSet(false, true)) {
            log.debug(LogCategory.CONNECTION, "Connection already in progress; ignoring request");
            return;
        }
        manualDisconnect = false;
        reconnectAttempts.set(0);
        cancelPendingRetry();
        try {
            EmbeddedJavaConnectionProvider newProvider =
                    EmbeddedJavaConnectionProvider.builder()
                            .cacheDirectory(cacheDirectory)
                            .build();
            TikTokLive newLive = TikTokLive.builder(config.uniqueId())
                    .connectionProvider(newProvider)
                    .fetchGifts(config.fetchGifts())
                    .build();
            register(newLive);
            provider = newProvider;
            live = newLive;
            setState(LiveConnectionState.CONNECTING, "Connecting to @" + config.uniqueId());
            log.info(LogCategory.CONNECTION, "Connecting to @" + config.uniqueId());
            newLive.connect().whenComplete((result, failure) -> {
                connecting.set(false);
                if (failure != null) {
                    handleFailure(failure);
                    return;
                }
                if (result != null && result.status() == io.github.nglmercer.tiktoklive.ConnectionStatus.OFFLINE) {
                    // The SDK also emits an "offline" event, which is where
                    // the state and the log message are published.
                    if (config.reconnect() && !manualDisconnect && !stopped) {
                        scheduleOfflineRetry();
                    }
                    return;
                }
                // The connected state is published by the onConnected callback.
                log.debug(LogCategory.CONNECTION, "Connect future completed");
            });
        } catch (RuntimeException failure) {
            connecting.set(false);
            handleFailure(failure);
        }
    }

    /** Stops the connection and releases the provider. */
    public void disconnect() {
        manualDisconnect = true;
        connecting.set(false);
        TikTokLive current = live;
        live = null;
        if (current != null) {
            try {
                current.disconnect();
            } catch (RuntimeException ignored) {
                // The SDK is being torn down; nothing useful can be done.
            }
            try {
                current.close();
            } catch (RuntimeException ignored) {
                // Same rationale: shutdown must never throw.
            }
        }
        EmbeddedJavaConnectionProvider currentProvider = provider;
        provider = null;
        if (currentProvider != null) {
            try {
                currentProvider.close();
            } catch (RuntimeException ignored) {
                // Same rationale: shutdown must never throw.
            }
        }
        roomId = "";
        setState(LiveConnectionState.IDLE, "Disconnected");
        log.info(LogCategory.CONNECTION, "Disconnected");
    }

    /** Drops the current connection and immediately starts a new one. */
    public void reconnect() {
        disconnect();
        if (stopped) {
            return;
        }
        connect();
    }

    private void register(TikTokLive target) {
        target.onChat(event -> dispatch(event, target));
        target.onGift(event -> dispatch(event, target));
        target.onGiftFinal(event -> dispatchGiftFinal(event, target));
        target.onLike(event -> dispatch(event, target));
        target.onMember(event -> dispatch(event, target));
        target.onSocial(event -> dispatch(event, target));
        target.onRoomUser(event -> dispatch(event, target));
        target.onUnknown(event -> dispatch(event, target));

        target.onConnected(state -> {
            roomId = state.roomId();
            setState(LiveConnectionState.CONNECTED, "Connected to room " + state.roomId());
            log.info(LogCategory.CONNECTION, "Connected to room " + state.roomId());
        });
        target.onDisconnected(state -> {
            roomId = state.roomId();
            setState(LiveConnectionState.IDLE, "Disconnected");
            log.info(LogCategory.CONNECTION, "Disconnected");
        });
        target.onReconnecting(reconnecting -> {
            setState(LiveConnectionState.RECONNECTING,
                    "Reconnecting (attempt " + reconnecting.attempt() + ")");
            log.warn(LogCategory.CONNECTION, "Reconnecting, attempt " + reconnecting.attempt()
                    + " in " + reconnecting.delay().toMillis() + "ms");
        });
        target.onOffline(state -> {
            roomId = state.roomId();
            setState(LiveConnectionState.OFFLINE, "Creator is not live right now");
            log.warn(LogCategory.CONNECTION,
                    "@" + config.uniqueId() + " is not live right now");
        });
        target.onError(this::handleFailure);
    }

    private void dispatch(io.github.nglmercer.tiktoklive.LiveEvent event, TikTokLive target) {
        if (stopped || target != live) {
            return;
        }
        try {
            TikTokEventContext normalized =
                    LiveEventNormalizer.normalize(event, target.state().roomId());
            if (config.tiktokIgnoreHistory() && normalized.isHistory()) {
                return;
            }
            listener.onEvent(normalized);
        } catch (RuntimeException failure) {
            log.warn(LogCategory.CONNECTION,
                    "Failed to normalize a LIVE event: " + failure.getMessage());
        }
    }

    /**
     * The SDK reports a completed gift streak separately. It is normalized as
     * {@code gift.final} so a rule can trigger once per streak instead of once
     * per repetition.
     */
    private void dispatchGiftFinal(
            io.github.nglmercer.tiktoklive.GiftEvent event, TikTokLive target) {
        if (stopped || target != live) {
            return;
        }
        try {
            TikTokEventContext normalized =
                    LiveEventNormalizer.normalize(event, target.state().roomId());
            if (config.tiktokIgnoreHistory() && normalized.isHistory()) {
                return;
            }
            java.util.Map<String, Object> payload =
                    new java.util.LinkedHashMap<String, Object>(normalized.payload());
            payload.put("gift.final", Boolean.TRUE);
            listener.onEvent(new TikTokEventContext(
                    normalized.eventId(),
                    normalized.roomId(),
                    TikTokEventContext.GIFT_FINAL,
                    normalized.userId(),
                    normalized.uniqueId(),
                    normalized.nickname(),
                    normalized.timestamp(),
                    normalized.history(),
                    payload));
        } catch (RuntimeException failure) {
            log.warn(LogCategory.CONNECTION,
                    "Failed to normalize a gift streak event: " + failure.getMessage());
        }
    }

    private void handleFailure(Throwable failure) {
        if (stopped) {
            return;
        }
        String message = failure == null ? "unknown error" : String.valueOf(failure.getMessage());
        log.warn(LogCategory.CONNECTION, "TikTok connection error: " + message);
        setState(LiveConnectionState.ERROR, message);
        if (manualDisconnect || !config.reconnect()) {
            return;
        }
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        int attempt = reconnectAttempts.incrementAndGet();
        long delay = Math.min(
                RECONNECT_MAX_DELAY_SECONDS,
                RECONNECT_BASE_DELAY_SECONDS * Math.min(attempt, 8L));
        log.info(LogCategory.CONNECTION,
                "Scheduling reconnect attempt " + attempt + " in " + delay + "s");
        schedule(delay);
    }

    /**
     * The creator is not live. The session is polled again later so the
     * plugin picks the room up as soon as the creator goes live, without an
     * administrator having to intervene.
     */
    private void scheduleOfflineRetry() {
        log.info(LogCategory.CONNECTION,
                "The creator is offline; checking again in " + OFFLINE_RETRY_SECONDS + "s");
        schedule(OFFLINE_RETRY_SECONDS);
    }

    private void schedule(long delaySeconds) {
        try {
            synchronized (this) {
                if (pendingRetry != null) {
                    pendingRetry.cancel(false);
                    pendingRetry = null;
                }
                pendingRetry =
                        timers.schedule(
                                () -> {
                                    pendingRetry = null;
                                    if (stopped || manualDisconnect) {
                                        return;
                                    }
                                    connect();
                                },
                                delaySeconds,
                                TimeUnit.SECONDS);
            }
        } catch (RuntimeException ignored) {
            // The scheduler is shutting down; the connection stays down.
        }
    }

    /**
     * Cancels a scheduled retry.
     *
     * <p>Without this a retry that was scheduled before a manual reconnect
     * would fire afterwards and open a second connection, which would leave
     * the first client's listeners registered and deliver every event
     * twice.</p>
     */
    private void cancelPendingRetry() {
        synchronized (this) {
            if (pendingRetry != null) {
                pendingRetry.cancel(false);
                pendingRetry = null;
            }
        }
    }

    private void setState(LiveConnectionState next, String detail) {
        state = next;
        try {
            listener.onState(next, detail);
        } catch (RuntimeException ignored) {
            // A misbehaving listener must not break the connection.
        }
    }

    /** Stops accepting events and releases every resource. */
    @Override
    public void close() {
        if (stopped) {
            return;
        }
        stopped = true;
        disconnect();
        timers.shutdownNow();
        setState(LiveConnectionState.STOPPED, "Plugin disabled");
    }
}

package io.github.syncnuke;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.NoVideoLoadedException;
import io.github.syncnuke.player.PlayerConnectionException;
import io.github.syncnuke.player.PlayerFactory;
import io.github.syncnuke.player.PlayerRuntime;
import io.github.syncnuke.player.VideoPlayer;
import io.github.syncnuke.session.PlayerRuntimeFactory;
import io.github.syncnuke.session.SessionException;
import io.github.syncnuke.session.State;
import io.github.syncnuke.session.SyncManagerFactory;
import lombok.extern.slf4j.Slf4j;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.apache.commons.lang3.StringUtils.isNotEmpty;

/**
 * Coordinates the video-player lifecycle with the corresponding sync session.
 * Each state check schedules its successor only after it completes, preventing
 * overlapping connection attempts.
 */
@Slf4j
final class SessionManager implements AutoCloseable {

    private static final long HEALTH_CHECK_INTERVAL_MILLIS = 1000;
    private static final long RETRY_DELAY_MILLIS = 3000;

    private final Environment environment;
    private final ScheduledExecutorService executor;
    private final PlayerRuntimeFactory playerRuntimeFactory;
    private final SyncManagerFactory syncManagerFactory;
    private final CompletableFuture<Void> termination = new CompletableFuture<>();

    private ScheduledFuture<?> nextUpdate;
    private PlayerRuntime playerRuntime;
    private SyncManager syncManager;
    private State state = State.CREATED;

    SessionManager(Environment environment) {
        this(environment, null, null, null);
    }

    SessionManager(
            Environment environment,
            ScheduledExecutorService executor,
            PlayerRuntimeFactory playerRuntimeFactory,
            SyncManagerFactory syncManagerFactory
    ) {
        this.environment = Objects.requireNonNull(environment, "environment");
        if (executor == null) {
            executor = initExecutor();
        }
        if (playerRuntimeFactory == null) {
            playerRuntimeFactory = initRuntimeFactory(environment);
        }
        if (syncManagerFactory == null) {
            syncManagerFactory = initSyncFactory(environment);
        }
        this.executor = Objects.requireNonNull(executor, "executor");
        this.playerRuntimeFactory = Objects.requireNonNull(playerRuntimeFactory, "playerRuntimeFactory");
        this.syncManagerFactory = Objects.requireNonNull(syncManagerFactory, "syncManagerFactory");
    }

    private ScheduledExecutorService initExecutor() {
        ScheduledExecutorService executor;
        executor = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "syncnuke-session");
            thread.setDaemon(true);
            return thread;
        });
        return executor;
    }

    private PlayerRuntimeFactory initRuntimeFactory(Environment environment) {
        PlayerRuntimeFactory playerRuntimeFactory;
        playerRuntimeFactory = () -> PlayerFactory.create(
                environment.getPlayer(),
                environment.getPlayerHost(),
                environment.getPlayerExecutable()
        );
        return playerRuntimeFactory;
    }

    private SyncManagerFactory initSyncFactory(Environment environment) {
        return player ->
        {
            if (environment.getPollingRate() == null) {
                return SyncManager.getInstance(player);
            }
            return SyncManager.getInstance(player, environment.getPollingRate());
        };
    }

    synchronized void start() {
        if (state != State.CREATED) {
            throw new IllegalStateException("Session manager has already been started");
        }
        state = State.LOADING_PLAYER;
        scheduleNextUpdate(0);
    }

    void awaitTermination() throws InterruptedException {
        try {
            termination.get();
        } catch (ExecutionException exception) {
            throw new SessionException(exception.getCause());
        }
    }

    private synchronized void update() {
        if (state == State.CLOSED) {
            return;
        }

        long nextDelay = HEALTH_CHECK_INTERVAL_MILLIS;
        try {
            ensurePlayerRuntime();
            ensureSyncSession();
        } catch (NoVideoLoadedException exception) {
            if (syncManager != null) {
                syncManager.stop();
            }
            if (state != State.LOADING_VIDEO) {
                log.info("No video is currently loaded; waiting for a video to be loaded");
            }
            state = State.LOADING_VIDEO;
            nextDelay = RETRY_DELAY_MILLIS;
        } catch (PlayerConnectionException exception) {
            if (playerRuntime == null) {
                log.info("Video player is not available; retrying in {} seconds", RETRY_DELAY_MILLIS / 1000);
            } else {
                log.info("Video player disconnected");
            }
            log.debug("Video player connection failure", exception);
            closePlayerRuntime();
            state = State.LOADING_PLAYER;
            nextDelay = RETRY_DELAY_MILLIS;
        } catch (RuntimeException | Error error) {
            termination.completeExceptionally(error);
            close();
            return;
        }

        scheduleNextUpdate(nextDelay);
    }

    private void ensurePlayerRuntime() {
        if (playerRuntime != null) {
            return;
        }

        playerRuntime = playerRuntimeFactory.create();
        if (isNotEmpty(environment.getFilePath())) {
            playerRuntime.getPlayer().load(environment.getFilePath());
        }
    }

    private void ensureSyncSession() {
        VideoPlayer player = playerRuntime.getPlayer();
        player.getStatus();

        if (syncManager == null) {
            syncManager = syncManagerFactory.create(player);
            playerRuntime.addShutdownTrigger(syncManager::close);
        }

        syncManager.start(
                environment.getProtocol(),
                environment.getSyncHost(),
                environment.getSyncPort(),
                environment.getUser(),
                environment.getRoom(),
                environment.getPassword()
        );

        if (state == State.LOADING_VIDEO) {
            log.info("Video loaded; reconnecting to the sync server");
        }
        state = State.SYNCING;
    }

    private void scheduleNextUpdate(long delayMillis) {
        if (state != State.CLOSED) {
            nextUpdate = executor.schedule(this::update, delayMillis, TimeUnit.MILLISECONDS);
        }
    }

    private void closePlayerRuntime() {
        PlayerRuntime runtime = playerRuntime;
        playerRuntime = null;
        syncManager = null;
        if (runtime != null) {
            runtime.close();
        }
    }

    @Override
    public void close() {
        synchronized (this) {
            if (state == State.CLOSED) {
                return;
            }
            state = State.CLOSED;
            if (nextUpdate != null) {
                nextUpdate.cancel(false);
            }
            executor.shutdownNow();
            closePlayerRuntime();
        }
        termination.complete(null);
    }

}

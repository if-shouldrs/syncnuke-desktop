package io.github.syncnuke;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.NoVideoLoadedException;
import io.github.syncnuke.player.PlayerConnectionException;
import io.github.syncnuke.player.PlayerRuntime;
import io.github.syncnuke.player.VideoPlayer;
import io.github.syncnuke.session.PlayerRuntimeFactory;
import io.github.syncnuke.session.SessionException;
import io.github.syncnuke.session.SyncManagerFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SessionManagerTest {

    private final List<Runnable> scheduledUpdates = new ArrayList<>();
    private final List<Long> scheduledDelays = new ArrayList<>();

    private Environment environment;
    private ScheduledExecutorService executor;
    private ScheduledFuture<?> scheduledFuture;
    private PlayerRuntimeFactory playerRuntimeFactory;
    private SyncManagerFactory syncManagerFactory;
    private PlayerRuntime playerRuntime;
    private VideoPlayer player;
    private SyncManager syncManager;
    private SessionManager sessionManager;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() throws Exception {
        environment = new Environment();
        environment.setProtocol("datasaver");
        environment.setSyncHost("sync.example.com");
        environment.setSyncPort(1234);
        environment.setUser("user");
        environment.setRoom("room");

        executor = mock(ScheduledExecutorService.class);
        scheduledFuture = mock(ScheduledFuture.class);
        when(executor.schedule(any(Runnable.class), anyLong(), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    scheduledUpdates.add(invocation.getArgument(0));
                    scheduledDelays.add(invocation.getArgument(1));
                    return scheduledFuture;
                });

        playerRuntimeFactory = mock(PlayerRuntimeFactory.class);
        syncManagerFactory = mock(SyncManagerFactory.class);
        playerRuntime = mock(PlayerRuntime.class);
        player = mock(VideoPlayer.class);
        syncManager = mock(SyncManager.class);

        when(playerRuntimeFactory.create()).thenReturn(playerRuntime);
        when(playerRuntime.getPlayer()).thenReturn(player);
        when(syncManagerFactory.create(player)).thenReturn(syncManager);

        sessionManager = new SessionManager(
                environment,
                executor,
                playerRuntimeFactory,
                syncManagerFactory
        );
    }

    @AfterEach
    void tearDown() {
        sessionManager.close();
    }

    @Test
    void startConnectsPlayerAndStartsSyncSession() throws Exception {
        sessionManager.start();
        runNextUpdate();

        verify(playerRuntimeFactory).create();
        verify(player).getStatus();
        verify(syncManagerFactory).create(player);
        verify(playerRuntime).addShutdownTrigger(any());
        verify(syncManager).start("datasaver", "sync.example.com", 1234, "user", "room", null);
        assertEquals(List.of(0L, 1000L), scheduledDelays);
    }

    @Test
    void configuredVideoLoadsBeforeSyncSessionStarts() throws Exception {
        environment.setFilePath("video.mkv");

        sessionManager.start();
        runNextUpdate();

        InOrder order = inOrder(player, syncManagerFactory);
        order.verify(player).load("video.mkv");
        order.verify(player).getStatus();
        order.verify(syncManagerFactory).create(player);
    }

    @Test
    void missingVideoRetainsPlayerAndRetriesLater() throws Exception {
        when(player.getStatus()).thenThrow(new NoVideoLoadedException());

        sessionManager.start();
        runNextUpdate();

        verify(syncManagerFactory, never()).create(any());
        verify(playerRuntime, never()).close();
        assertEquals(List.of(0L, 3000L), scheduledDelays);
    }

    @Test
    void unavailablePlayerRetriesLater() {
        when(playerRuntimeFactory.create())
                .thenThrow(new PlayerConnectionException("unavailable"));

        sessionManager.start();
        runNextUpdate();

        verify(syncManagerFactory, never()).create(any());
        assertEquals(List.of(0L, 3000L), scheduledDelays);
    }

    @Test
    void removedVideoStopsSyncWithoutClosingPlayer() throws Exception {
        sessionManager.start();
        runNextUpdate();
        when(player.getStatus()).thenThrow(new NoVideoLoadedException());

        runNextUpdate();

        verify(syncManager).stop();
        verify(playerRuntime, never()).close();
        assertEquals(List.of(0L, 1000L, 3000L), scheduledDelays);
    }

    @Test
    void loadedVideoRestartsSyncOnExistingPlayer() throws Exception {
        sessionManager.start();
        runNextUpdate();
        when(player.getStatus())
                .thenThrow(new NoVideoLoadedException())
                .thenReturn(null);

        runNextUpdate();
        runNextUpdate();

        verify(playerRuntimeFactory).create();
        verify(syncManagerFactory).create(player);
        verify(syncManager, times(2)).start(
                "datasaver",
                "sync.example.com",
                1234,
                "user",
                "room",
                null
        );
        assertEquals(List.of(0L, 1000L, 3000L, 1000L), scheduledDelays);
    }

    @Test
    void connectionFailureClosesPlayerAndRetriesLater() throws Exception {
        sessionManager.start();
        runNextUpdate();
        when(player.getStatus()).thenThrow(new PlayerConnectionException("disconnected"));

        runNextUpdate();

        verify(playerRuntime).close();
        assertEquals(List.of(0L, 1000L, 3000L), scheduledDelays);
    }

    @Test
    void closeCancelsUpdateAndReleasesWaiter() {
        sessionManager.start();

        sessionManager.close();

        verify(scheduledFuture).cancel(false);
        verify(executor).shutdownNow();
        assertDoesNotThrow(sessionManager::awaitTermination);
    }

    @Test
    void unexpectedFailureTerminatesSessionManager() throws Exception {
        IllegalStateException failure = new IllegalStateException("unexpected");
        when(player.getStatus()).thenThrow(failure);
        sessionManager.start();

        runNextUpdate();

        SessionException thrown = assertThrows(
                SessionException.class,
                sessionManager::awaitTermination
        );
        assertEquals(failure, thrown.getCause());
    }

    private void runNextUpdate() {
        scheduledUpdates.remove(0).run();
    }
}

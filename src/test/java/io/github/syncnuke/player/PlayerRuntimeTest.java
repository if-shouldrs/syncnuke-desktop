package io.github.syncnuke.player;

import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class PlayerRuntimeTest {

    @Test
    void ownedProcessExitRunsShutdownStepsAndReleasesWaiters() throws Exception {
        VideoPlayer player = mock(VideoPlayer.class);
        Process ownedProcess = mock(Process.class);
        Runnable shutdownStep = mock(Runnable.class);
        CompletableFuture<Process> processExit = new CompletableFuture<>();
        when(ownedProcess.onExit()).thenReturn(processExit);
        PlayerRuntime runtime = new PlayerRuntime(player, ownedProcess);
        runtime.addShutdownTrigger(shutdownStep);

        processExit.complete(ownedProcess);
        runtime.awaitTermination();

        verify(shutdownStep).run();
    }

    @Test
    void closeRunsShutdownStepsBeforeDestroyingOwnedProcess() {
        VideoPlayer player = mock(VideoPlayer.class);
        Process ownedProcess = mock(Process.class);
        Runnable shutdownStep = mock(Runnable.class);
        when(ownedProcess.onExit()).thenReturn(new CompletableFuture<>());
        when(ownedProcess.isAlive()).thenReturn(true);
        PlayerRuntime runtime = new PlayerRuntime(player, ownedProcess);
        runtime.addShutdownTrigger(shutdownStep);

        runtime.close();

        InOrder shutdownOrder = inOrder(shutdownStep, ownedProcess);
        shutdownOrder.verify(shutdownStep).run();
        shutdownOrder.verify(ownedProcess).destroy();
    }

    @Test
    void shutdownStepsRunOnlyOnce() {
        VideoPlayer player = mock(VideoPlayer.class);
        Process ownedProcess = mock(Process.class);
        Runnable shutdownStep = mock(Runnable.class);
        when(ownedProcess.onExit()).thenReturn(new CompletableFuture<>());
        when(ownedProcess.isAlive()).thenReturn(true);
        PlayerRuntime runtime = new PlayerRuntime(player, ownedProcess);
        runtime.addShutdownTrigger(shutdownStep);

        runtime.close();
        runtime.close();

        verify(shutdownStep, times(1)).run();
        assertThrows(IllegalStateException.class, () -> runtime.addShutdownTrigger(() -> { }));
    }

}

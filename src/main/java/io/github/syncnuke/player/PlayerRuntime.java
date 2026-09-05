package io.github.syncnuke.player;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;

@Slf4j
public final class PlayerRuntime implements AutoCloseable {

    @Getter
    private final VideoPlayer player;
    private final Process ownedProcess;
    private final CountDownLatch termination = new CountDownLatch(1);
    private final List<Runnable> shutdownSteps = new ArrayList<>();
    private volatile boolean shuttingDown;

    PlayerRuntime(VideoPlayer player, Process ownedProcess) {
        this.player = player;
        this.ownedProcess = ownedProcess;

        if (ownedProcess != null) {
            ownedProcess.onExit().thenAccept(ignored -> shutdown());
        }
    }

    public synchronized void addShutdownTrigger(Runnable shutdownStep) {
        Objects.requireNonNull(shutdownStep, "shutdownStep");
        if (shuttingDown) {
            throw new IllegalStateException("Player runtime is already shutting down");
        }
        shutdownSteps.add(shutdownStep);
    }

    public void awaitTermination() throws InterruptedException {
        termination.await();
    }

    @Override
    public void close() {
        shutdown();
        if (ownedProcess != null && ownedProcess.isAlive()) {
            ownedProcess.destroy();
        }
    }

    private synchronized void shutdown() {
        shuttingDown = true;
        try {
            for (Runnable shutdownStep : shutdownSteps) {
                try {
                    shutdownStep.run();
                } catch (RuntimeException exception) {
                    log.error("Shutdown step failed", exception);
                }
            }
        } finally {
            shutdownSteps.clear();
            termination.countDown();
        }
    }

}

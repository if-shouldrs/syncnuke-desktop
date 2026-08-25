package io.github.syncnuke.player;

import lombok.Getter;

import java.util.concurrent.CountDownLatch;

public final class PlayerRuntime implements AutoCloseable {

    @Getter
    private final VideoPlayer player;
    private final Process ownedProcess;
    private final CountDownLatch termination = new CountDownLatch(1);

    PlayerRuntime(VideoPlayer player, Process ownedProcess) {
        this.player = player;
        this.ownedProcess = ownedProcess;

        if (ownedProcess != null) {
            ownedProcess.onExit().thenRun(termination::countDown);
        }
    }

    public void awaitTermination() throws InterruptedException {
        termination.await();
    }

    @Override
    public void close() {
        termination.countDown();
        if (ownedProcess != null && ownedProcess.isAlive()) {
            ownedProcess.destroy();
        }
    }

}

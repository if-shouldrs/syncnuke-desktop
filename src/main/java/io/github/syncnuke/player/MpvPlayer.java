package io.github.syncnuke.player;

import io.github.syncnuke.player.data.PlaybackState;
import io.github.syncnuke.player.data.PlayerState;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.*;

@Slf4j
public final class MpvPlayer implements VideoPlayer {

    private final MpvIpcClient ipcClient;

    private final ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mpv-keep-alive");
        t.setDaemon(true);
        return t;
    });

    public MpvPlayer(@NonNull String ipcPath) throws IOException {
        this.ipcClient = MpvIpcClient.connect(ipcPath);

        startKeepAlivePings();
        log.info("Connected to MPV IPC at {}", ipcPath);
    }

    @Override
    public void play() {
        ipcClient.setProperty("pause", false);
    }

    @Override
    public void pause() {
        ipcClient.setProperty("pause", true);
    }

    @Override
    public void seek(double position) {
        ipcClient.setProperty("time-pos", position);
    }

    @Override
    public PlayerState getStatus() {
        PlayerState status = new PlayerState();
        status.setPlaybackState(isPaused() ? PlaybackState.PAUSED : PlaybackState.PLAYING);
        status.setPosition(getPosition());
        status.setPlaybackSpeed(getPlaybackSpeed());
        status.setLastUpdateTime(System.currentTimeMillis());
        return status;
    }

    private double getPosition() {
        return ipcClient.getProperty("time-pos", Double.class);
    }

    private double getPlaybackSpeed() {
        return ipcClient.getProperty("speed", Double.class);
    }

    private boolean isPaused() {
        return ipcClient.getProperty("pause", Boolean.class);
    }

    @Override
    public void load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("File does not exist: {}", filePath);
            return;
        }
        ipcClient.command("loadfile", filePath);
    }

    private void startKeepAlivePings() {
        keepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                ipcClient.command("get_property", "pause");
            } catch (Exception e) {
                log.warn("Keep-alive ping failed: {}", e.getMessage());
            }
        }, 1, 3, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        keepAliveExecutor.shutdownNow();
        ipcClient.close();
    }

}

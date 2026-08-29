package io.github.syncnuke.player.integration.mpv;

import io.github.syncnuke.player.NoVideoLoadedException;
import io.github.syncnuke.player.VideoPlayer;
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

    MpvPlayer(@NonNull String ipcPath) throws IOException {
        this.ipcClient = MpvIpcClient.connect(ipcPath);

        startKeepAlivePings();
        log.info("Connected to MPV IPC at {}", ipcPath);
    }

    @Override
    public void play() {
        ensureVideoLoaded();
        ipcClient.setProperty("pause", false);
    }

    @Override
    public void pause() {
        ensureVideoLoaded();
        ipcClient.setProperty("pause", true);
    }

    @Override
    public void seek(double position) {
        if (!Double.isFinite(position) || position < 0) {
            throw new IllegalArgumentException("Position must be a non-negative finite number.");
        }
        ensureVideoLoaded();
        ipcClient.setProperty("time-pos", position);
    }

    @Override
    public void setPlaybackSpeed(double playbackSpeed) {
        if (!Double.isFinite(playbackSpeed) || playbackSpeed <= 0) {
            throw new IllegalArgumentException("Playback speed must be a positive finite number.");
        }
        ensureVideoLoaded();
        ipcClient.setProperty("speed", playbackSpeed);
    }

    @Override
    public PlayerState getStatus() {
        ensureVideoLoaded();
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
        if (filePath == null || filePath.isEmpty()) {
            throw new IllegalArgumentException("File must not be empty.");
        }
        File file = new File(filePath);
        if (!file.exists()) {
            throw new IllegalArgumentException("File does not exist: " + filePath);
        }
        ipcClient.command("loadfile", filePath);
    }

    private void ensureVideoLoaded() {
        if (ipcClient.getProperty("idle-active", Boolean.class)) {
            throw new NoVideoLoadedException();
        }
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

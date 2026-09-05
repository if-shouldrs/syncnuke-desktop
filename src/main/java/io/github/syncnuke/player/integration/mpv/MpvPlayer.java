package io.github.syncnuke.player.integration.mpv;

import io.github.syncnuke.player.NoVideoLoadedException;
import io.github.syncnuke.player.VideoPlayer;
import io.github.syncnuke.player.data.PlaybackState;
import io.github.syncnuke.player.data.PlayerState;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;

@Slf4j
public final class MpvPlayer implements VideoPlayer {

    private final MpvIpcClient ipcClient;

    MpvPlayer(@NonNull String ipcPath) throws IOException {
        this.ipcClient = MpvIpcClient.connect(ipcPath);
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
        try {
            ensureVideoLoaded();
            PlayerState status = new PlayerState();
            status.setPlaybackState(isPaused() ? PlaybackState.PAUSED : PlaybackState.PLAYING);
            status.setPosition(getPosition());
            status.setPlaybackSpeed(getPlaybackSpeed());
            status.setLastUpdateTime(System.currentTimeMillis());
            return status;
        } catch (MpvIpcClient.MpvPropertyUnavailableException exception) {
            throw new NoVideoLoadedException();
        }
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

    @Override
    public void close() {
        ipcClient.close();
    }

}

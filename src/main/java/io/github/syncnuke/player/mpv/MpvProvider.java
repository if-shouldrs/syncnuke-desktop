package io.github.syncnuke.player.mpv;

import io.github.syncnuke.player.PlayerProvider;
import io.github.syncnuke.player.VideoPlayer;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public final class MpvProvider implements PlayerProvider {

    @Override
    public VideoPlayer connect(String host) throws IOException {
        return new MpvPlayer(host);
    }

    @Override
    public Process launch(String host) throws IOException {
        log.info("Starting MPV with IPC endpoint {}", host);
        return new ProcessBuilder(
                "mpv",
                "--idle=yes",
                "--force-window=yes",
                "--input-ipc-server=" + host
        ).inheritIO().start();
    }

}

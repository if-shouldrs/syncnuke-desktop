package io.github.syncnuke;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.MpvPlayer;
import io.github.syncnuke.player.PlayerManager;
import io.github.syncnuke.player.VideoPlayer;
import org.apache.commons.cli.*;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;

import static org.slf4j.LoggerFactory.getLogger;

public class Main {

    private static final Logger logger = getLogger(Main.class);
    private static final String IPC_PATH_PROPERTY = "syncnuke.mpv.ipc";
    private static final String LAUNCH_MPV_PROPERTY = "syncnuke.mpv.launch";

    public static void main(String[] args) {
        Options options = parseArguments(args);
        CountDownLatch latch = new CountDownLatch(1);
        Process mpvProcess = null;

        try {
            String ipcPath = getMpvIpcPath();
            if (Boolean.getBoolean(LAUNCH_MPV_PROPERTY)) {
                mpvProcess = launchMpv(ipcPath);
                mpvProcess.onExit().thenRun(latch::countDown);
            }

            VideoPlayer mpvPlayer = new MpvPlayer(ipcPath);
            try (PlayerManager videoPlayer = getVideoPlayer(mpvPlayer)) {
                if (options.getFilePath() != null && !options.getFilePath().isBlank()) {
                    mpvPlayer.load(options.getFilePath());
                } else {
                    logger.info("No --file argument supplied; using the media already loaded in MPV");
                }
                startSyncClient(options, videoPlayer);

                // Wait for MPV or the client to close before terminating.
                latch.await();
            }
        } catch (IOException exception) {
            logger.error("Error initializing MPV player", exception);
        } catch (Exception e) {
            logger.error("An unexpected error occurred", e);
        } finally {
            latch.countDown();
            if (mpvProcess != null && mpvProcess.isAlive()) {
                mpvProcess.destroy();
            }
        }
    }

    private static Process launchMpv(String ipcPath) throws IOException {
        logger.info("Starting MPV with IPC endpoint {}", ipcPath);
        return new ProcessBuilder(
                "mpv",
                "--idle=yes",
                "--force-window=yes",
                "--input-ipc-server=" + ipcPath
        ).inheritIO().start();
    }

    private static String getMpvIpcPath() {
        String configuredPath = System.getProperty(IPC_PATH_PROPERTY);
        if (configuredPath != null && !configuredPath.isBlank()) {
            return configuredPath;
        }
        if (System.getProperty("os.name", "").toLowerCase().startsWith("windows")) {
            return "\\\\.\\pipe\\syncnuke-mpv";
        }
        return Path.of(System.getProperty("user.home"), ".mpv-ipc", "mpvsocket").toString();
    }

    private static void startSyncClient(Options options, PlayerManager videoPlayer) {
        SyncManager syncManager = SyncManager.getInstance(videoPlayer);
        syncManager.start(
                options.getProtocol(),
                options.getHost(),
                options.getPort(),
                "user",
                "room"
        );
    }

    private static PlayerManager getVideoPlayer(VideoPlayer player) {
        PlayerManager playerManager = PlayerManager.getInstance();
        playerManager.start(player);
        return playerManager;
    }

    private static Options parseArguments(String[] args) {
        CommandLine cmd;
        org.apache.commons.cli.Options options = getOptions();
        CommandLineParser parser = new DefaultParser();

        Options config = new Options();
        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("host")) {
                config.setHost(cmd.getOptionValue("host"));
            }
            if (cmd.hasOption("port")) {
                config.setPort(Integer.parseInt(cmd.getOptionValue("port")));
            }
            if (cmd.hasOption("protocol")) {
                config.setProtocol(cmd.getOptionValue("protocol"));
            }
            config.setFilePath(cmd.getOptionValue("file"));
        } catch (ParseException e) {
            logger.error("Failed to parse command line arguments: {}", e.getMessage());
            System.exit(1);
        }
        return config;
    }

    private static org.apache.commons.cli.Options getOptions() {
        org.apache.commons.cli.Options options = new org.apache.commons.cli.Options();
        options.addOption(Option.builder()
                .longOpt("host")
                .hasArg()
                .desc("Server host (default: localhost)")
                .build());
        options.addOption(Option.builder()
                .longOpt("port")
                .hasArg()
                .desc("Server port (default: 8999)")
                .type(Number.class)
                .build());
        options.addOption(Option.builder()
                .longOpt("file")
                .hasArg()
                .desc("File path for the media to load")
                .build());
        options.addOption(Option.builder()
                .longOpt("protocol")
                .hasArg()
                .desc("Protocol to use (default: datasaver)")
                .build());
        return options;
    }

}

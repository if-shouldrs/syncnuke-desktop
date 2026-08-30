package io.github.syncnuke;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.PlayerFactory;
import io.github.syncnuke.player.PlayerRuntime;
import io.github.syncnuke.player.VideoPlayer;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.slf4j.Logger;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.slf4j.LoggerFactory.getLogger;

public class Main {

    private static final Logger logger = getLogger(Main.class);

    public static void main(String[] args) {
        Environment env = parseArguments(args);

        try (PlayerRuntime runtime = PlayerFactory.create(
                env.getPlayer(),
                env.getPlayerHost(),
                env.isLaunchPlayer()
        )) {

            VideoPlayer player = runtime.getPlayer();
            try (SyncManager syncManager = getSyncManager(player, env.getPollingRate())) {
                if (!isEmpty(env.getFilePath())) {
                    player.load(env.getFilePath());
                } else {
                    logger.info("No --file argument supplied; using the media already loaded in the selected player");
                }

                startSyncClient(env, syncManager);
                runtime.awaitTermination();
            }
        } catch (IOException exception) {
            logger.error("Error initializing video player", exception);
        } catch (Exception exception) {
            logger.error("An unexpected error occurred", exception);
        }
    }

    private static void startSyncClient(Environment env, SyncManager syncManager) {
        syncManager.start(
                env.getProtocol(),
                env.getSyncHost(),
                env.getSyncPort(),
                env.getUser(),
                env.getRoom()
        );
    }

    private static SyncManager getSyncManager(VideoPlayer player, Long pollingRate) throws InterruptedException {
        int retries = 0;
        while (true) {
            try {
                return pollingRate == null ? SyncManager.getInstance(player) : SyncManager.getInstance(player, pollingRate);
            } catch (RuntimeException exception) {
                if (++retries > 10) {
                    throw exception;
                }
                logger.warn("Failed to initialize video player; retrying in 3 seconds ({}/10)", retries);
                Thread.sleep(3000);
            }
        }
    }

    private static Environment parseArguments(String[] args) {
        CommandLine cmd;
        Options options = getOptions();
        CommandLineParser parser = new DefaultParser();

        Environment config = new Environment();
        try {
            cmd = parser.parse(options, args);
            if (cmd.hasOption("player")) {
                config.setPlayer(cmd.getOptionValue("player"));
            }
            if (cmd.hasOption("player-host")) {
                config.setPlayerHost(cmd.getOptionValue("player-host"));
            }
            if (cmd.hasOption("launch-player")) {
                config.setLaunchPlayer(true);
            }
            if (cmd.hasOption("polling-rate")) {
                config.setPollingRate(Long.parseLong(cmd.getOptionValue("polling-rate")));
            }
            if (cmd.hasOption("host")) {
                config.setSyncHost(cmd.getOptionValue("host"));
            }
            if (cmd.hasOption("port")) {
                config.setSyncPort(Integer.parseInt(cmd.getOptionValue("port")));
            }
            if (cmd.hasOption("protocol")) {
                config.setProtocol(cmd.getOptionValue("protocol"));
            }
            if (cmd.hasOption("user")) {
                config.setUser(cmd.getOptionValue("user"));
            }
            if (cmd.hasOption("room")) {
                config.setRoom(cmd.getOptionValue("room"));
            }
            config.setFilePath(cmd.getOptionValue("file"));
        } catch (ParseException exception) {
            logger.error("Failed to parse command line arguments: {}", exception.getMessage());
            System.exit(1);
        }
        return config;
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(Option.builder()
                .longOpt("player")
                .hasArg()
                .desc("Video player to use (default: mpv)")
                .build());
        options.addOption(Option.builder()
                .longOpt("player-host")
                .hasArg()
                .desc("Video player IPC, pipe, or network host")
                .build());
        options.addOption(Option.builder()
                .longOpt("launch-player")
                .desc("Launch the selected video player")
                .build());
        options.addOption(Option.builder()
                .longOpt("polling-rate")
                .hasArg()
                .desc("Player polling interval in milliseconds")
                .type(Number.class)
                .build());
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
        options.addOption(Option.builder()
                .longOpt("user")
                .hasArg()
                .required()
                .desc("Username to use")
                .build());
        options.addOption(Option.builder()
                .longOpt("room")
                .hasArg()
                .required()
                .desc("Room to join")
                .build());
        return options;
    }

}

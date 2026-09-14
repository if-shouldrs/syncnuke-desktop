package io.github.syncnuke;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.NoVideoLoadedException;
import io.github.syncnuke.player.PlayerFactory;
import io.github.syncnuke.player.PlayerRuntime;
import io.github.syncnuke.player.VideoPlayer;
import io.github.syncnuke.player.cli.PlayerArguments;
import io.github.syncnuke.player.cli.PlayerCli;
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

    private static final long RETRY_DELAY_MILLIS = 3000;
    private static final Logger logger = getLogger(Main.class);

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        try {
            Environment env = parseArguments(args);
            configurePlayer(env);
            validateSyncArguments(env);

            while (true) {
                try {
                    runPlayerSession(env);
                    logger.info("Video player disconnected");
                } catch (IOException exception) {
                    logger.info("Video player is not available; retrying in {} seconds", RETRY_DELAY_MILLIS / 1000);
                    logger.debug("Video player is not available", exception);
                } catch (IllegalStateException exception) {
                    if (!isConnectionFailure(exception)) {
                        throw exception;
                    }
                    logger.info("Video player disconnected");
                    logger.debug("Video player connection failure", exception);
                }
                waitBeforeRetry();
            }
        } catch (IllegalArgumentException exception) {
            logger.error("Invalid configuration: {}", exception.getMessage());
        } catch (Exception exception) {
            logger.error("An unexpected error occurred", exception);
        }
        return 1;
    }

    private static void runPlayerSession(Environment env) throws IOException, InterruptedException {
        try (PlayerRuntime runtime = PlayerFactory.create(
                env.getPlayer(),
                env.getPlayerHost(),
                env.getPlayerExecutable()
        )) {
            VideoPlayer player = runtime.getPlayer();
            Thread shutdownHook = getShutdownHook(runtime);
            Runtime.getRuntime().addShutdownHook(shutdownHook);
            try {
                loadConfiguredVideo(env, player);
                try (SyncManager syncManager = waitForSyncManager(player, env.getPollingRate())) {
                    runtime.addShutdownTrigger(syncManager::close);
                    runSyncSession(env, runtime, syncManager);
                }
            } finally {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            }
        }
    }

    private static void loadConfiguredVideo(Environment env, VideoPlayer player) {
        if (!isEmpty(env.getFilePath())) {
            player.load(env.getFilePath());
        } else {
            logger.debug("No --file argument supplied; using the media already loaded in the selected player");
        }
    }

    private static void runSyncSession(
            Environment env,
            PlayerRuntime runtime,
            SyncManager syncManager
    ) throws InterruptedException {
        while (true) {
            try {
                startSyncClient(env, syncManager);
                runtime.awaitTermination();
                return;
            } catch (NoVideoLoadedException exception) {
                syncManager.stop();
                waitForVideo(runtime.getPlayer());
            }
        }
    }

    private static Thread getShutdownHook(PlayerRuntime runtime) {
        return new Thread(() -> {
            logger.info("Closing player runtime...");
            runtime.close();
        }, "syncnuke-shutdown");
    }

    private static void configurePlayer(Environment env) throws IOException {
        PlayerArguments arguments = PlayerCli.configure(new PlayerArguments(
                env.getPlayer(),
                env.getPlayerHost(),
                env.getPlayerExecutable()
        ));

        env.setPlayer(arguments.player());
        env.setPlayerHost(arguments.host());
        env.setPlayerExecutable(arguments.executable());
    }

    private static void validateSyncArguments(Environment env) {
        StringBuilder missingArguments = new StringBuilder();
        if (isEmpty(env.getUser())) {
            missingArguments.append("--user");
        }
        if (isEmpty(env.getRoom())) {
            if (!missingArguments.isEmpty()) {
                missingArguments.append(", ");
            }
            missingArguments.append("--room");
        }
        if (!missingArguments.isEmpty()) {
            throw new IllegalArgumentException("Missing required option(s): " + missingArguments);
        }
    }

    private static void startSyncClient(Environment env, SyncManager syncManager) {
        syncManager.start(
                env.getProtocol(),
                env.getSyncHost(),
                env.getSyncPort(),
                env.getUser(),
                env.getRoom(),
                env.getPassword()
        );
    }

    private static SyncManager waitForSyncManager(VideoPlayer player, Long pollingRate) throws InterruptedException {
        while (true) {
            try {
                return pollingRate == null
                        ? SyncManager.getInstance(player)
                        : SyncManager.getInstance(player, pollingRate);
            } catch (NoVideoLoadedException exception) {
                waitForVideo(player);
            }
        }
    }

    private static void waitBeforeRetry() throws InterruptedException {
        Thread.sleep(RETRY_DELAY_MILLIS);
    }

    private static void waitForVideo(VideoPlayer player) throws InterruptedException {
        logger.info("No video is currently loaded; waiting for a video to be loaded");
        while (true) {
            try {
                player.getStatus();
                logger.info("Video loaded; reconnecting to the sync server");
                return;
            } catch (NoVideoLoadedException exception) {
                waitBeforeRetry();
            }
        }
    }

    private static boolean isConnectionFailure(IllegalStateException exception) {
        return exception.getCause() instanceof IOException;
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
            if (cmd.hasOption("player-executable")) {
                config.setPlayerExecutable(cmd.getOptionValue("player-executable"));
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
            if (cmd.hasOption("password")) {
                config.setPassword(cmd.getOptionValue("password"));
            }
            config.setFilePath(cmd.getOptionValue("file"));
        } catch (ParseException exception) {
            throw new IllegalArgumentException(
                    "Failed to parse command line arguments: " + exception.getMessage(),
                    exception
            );
        }
        return config;
    }

    private static Options getOptions() {
        Options options = new Options();
        options.addOption(Option.builder()
                .longOpt("player")
                .hasArg()
                .desc("Video player to use (prompts when omitted)")
                .build());
        options.addOption(Option.builder()
                .longOpt("player-host")
                .hasArg()
                .desc("Video player IPC, pipe, or network host")
                .build());
        options.addOption(Option.builder()
                .longOpt("player-executable")
                .hasArg()
                .desc("Path to the video player executable")
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
                .desc("Server host (default: master.syncnuke.com)")
                .build());
        options.addOption(Option.builder()
                .longOpt("port")
                .hasArg()
                .desc("Server port (default: 65344)")
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
                .desc("Username to use")
                .build());
        options.addOption(Option.builder()
                .longOpt("room")
                .hasArg()
                .desc("Room to join")
                .build());
        options.addOption(Option.builder()
                .longOpt("password")
                .hasArg()
                .desc("Password for the room")
                .build());
        return options;
    }

}

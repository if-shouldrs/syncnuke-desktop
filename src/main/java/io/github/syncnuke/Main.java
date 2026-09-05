package io.github.syncnuke;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.PlayerFactory;
import io.github.syncnuke.player.NoVideoLoadedException;
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

    private static final Logger logger = getLogger(Main.class);

    public static void main(String[] args) {
        System.exit(run(args));
    }

    private static int run(String[] args) {
        try {
            Environment env = parseArguments(args);
            configurePlayer(env);
            validateSyncArguments(env);

            try (PlayerRuntime runtime = PlayerFactory.create(
                    env.getPlayer(),
                    env.getPlayerHost(),
                    env.getPlayerExecutable()
            )) {
                VideoPlayer player = runtime.getPlayer();
                try (SyncManager syncManager = getSyncManager(player, env.getPollingRate())) {
                    runtime.addShutdownTrigger(syncManager::close);
                    Thread shutdownHook = getShutdownHook(runtime);
                    Runtime.getRuntime().addShutdownHook(shutdownHook);
                    try {
                        if (!isEmpty(env.getFilePath())) {
                            player.load(env.getFilePath());
                        } else {
                            logger.info("No --file argument supplied; using the media already loaded in the selected player");
                        }

                        startSyncClient(env, syncManager);
                        runtime.awaitTermination();
                    } finally {
                        Runtime.getRuntime().removeShutdownHook(shutdownHook);
                    }
                }
            }
            return 0;
        } catch (IllegalArgumentException exception) {
            logger.error("Invalid configuration: {}", exception.getMessage());
        } catch (IOException exception) {
            logger.error("Error initializing video player", exception);
        } catch (IllegalStateException exception) {
            if (exception.getCause() instanceof IOException) {
                logger.info("Video player disconnected");
            } else {
                logger.error("An unexpected error occurred", exception);
            }
        } catch (Exception exception) {
            logger.error("An unexpected error occurred", exception);
        }
        return 1;
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

    private static SyncManager getSyncManager(VideoPlayer player, Long pollingRate) throws InterruptedException {
        int retries = 0;
        while (true) {
            try {
                return pollingRate == null ? SyncManager.getInstance(player) : SyncManager.getInstance(player, pollingRate);
            } catch (NoVideoLoadedException exception) {
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

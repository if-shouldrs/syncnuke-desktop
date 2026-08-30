package io.github.syncnuke.player;

import io.github.syncnuke.player.integration.mpv.MpvProvider;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public final class PlayerFactory {

    private static final String PLAYER_HOST_PROPERTY = "syncnuke.player.host";
    private static final String LAUNCH_PLAYER_PROPERTY = "syncnuke.player.launch";

    private PlayerFactory() {
    }

    public static PlayerRuntime create(
            String player,
            String host,
            boolean launch,
            String executable
    ) throws IOException {
        PlayerProvider provider = getProvider(player, executable);
        return createRuntime(
                provider,
                resolvePlayerHost(host),
                launch || Boolean.getBoolean(LAUNCH_PLAYER_PROPERTY)
        );
    }

    private static PlayerProvider getProvider(String player, String executable) {
        if (isNotEmpty(player) && player.equalsIgnoreCase("mpv")) {
            return new MpvProvider(executable);
        }
        throw new IllegalArgumentException("Unsupported video player: " + player);
    }

    private static PlayerRuntime createRuntime(PlayerProvider provider, String host, boolean launch) throws IOException {
        Process process = null;
        try {
            return new PlayerRuntime(provider.connect(host), process);
        } catch (IOException exception) {
            if (!launch) {
                destroy(process);
                throw exception;
            }
            process = provider.launch(host);
        }
        return new PlayerRuntime(provider.connect(host), process);
    }

    private static void destroy(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
        }
    }

    private static String resolvePlayerHost(String configuredHost) {
        String host = configuredHost;
        if (isEmpty(host)) {
            host = System.getProperty(PLAYER_HOST_PROPERTY);
        }
        if (isEmpty(host)) {
            throw new IllegalArgumentException("Player host must be configured");
        }
        return host;
    }

}

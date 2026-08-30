package io.github.syncnuke.player;

import io.github.syncnuke.player.integration.mpv.MpvProvider;

import java.io.IOException;

import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.isNotEmpty;

public final class PlayerFactory {

    private static final String PLAYER_HOST_PROPERTY = "syncnuke.player.host";

    private PlayerFactory() {
    }

    public static PlayerRuntime create(
            String player,
            String host,
            String executable
    ) throws IOException {
        PlayerProvider provider = getProvider(player, executable);
        return createRuntime(provider, resolvePlayerHost(host));
    }

    private static PlayerProvider getProvider(String player, String executable) {
        if (isNotEmpty(player) && player.equalsIgnoreCase("mpv")) {
            return new MpvProvider(executable);
        }
        throw new IllegalArgumentException("Unsupported video player: " + player);
    }

    private static PlayerRuntime createRuntime(PlayerProvider provider, String host) throws IOException {
        Process process = null;
        try {
            return new PlayerRuntime(provider.connect(host), process);
        } catch (IOException exception) {
            process = provider.launch(host);
        }
        return new PlayerRuntime(provider.connect(host), process);
    }

    private static String resolvePlayerHost(String configuredHost) {
        String host = configuredHost;
        if (isEmpty(host)) {
            host = System.getProperty(PLAYER_HOST_PROPERTY);
        }
        return host;
    }

}

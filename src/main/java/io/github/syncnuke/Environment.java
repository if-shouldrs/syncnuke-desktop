package io.github.syncnuke;

import lombok.Data;

@Data
final class Environment {
    private String player = "mpv";
    private String playerHost;
    private String filePath;
    private boolean launchPlayer;
    private String syncHost = "localhost";
    private int syncPort = 8999;
    private String protocol = "datasaver";
}

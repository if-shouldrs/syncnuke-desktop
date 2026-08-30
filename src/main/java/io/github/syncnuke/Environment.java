package io.github.syncnuke;

import lombok.Data;

@Data
final class Environment {
    // Video Player Settings
    private String player;
    private String playerHost;
    private String playerExecutable;
    private String filePath;
    private boolean launchPlayer;
    private Long pollingRate;
    // Sync Settings
    private String syncHost = "localhost";
    private int syncPort = 8999;
    private String protocol = "datasaver";
    private String user;
    private String room;
}

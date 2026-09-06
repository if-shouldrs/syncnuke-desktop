package io.github.syncnuke;

import lombok.Data;

@Data
final class Environment {
    // Video Player Settings
    private String player;
    private String playerHost;
    private String playerExecutable;
    private String filePath;
    private Long pollingRate;
    // Sync Settings
    private String syncHost = "master.syncnuke.com";
    private int syncPort = 65344;
    private String protocol = "datasaver";
    private String user;
    private String room;
    private String password;
}

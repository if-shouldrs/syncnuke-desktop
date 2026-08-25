package io.github.syncnuke;

import lombok.Data;

@Data
final class Options {
    private String host = "localhost";
    private int port = 8999;
    private String filePath;
    private String protocol = "datasaver";
}

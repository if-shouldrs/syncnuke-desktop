package io.github.syncnuke.player;

import java.io.IOException;

public interface PlayerProvider {
    VideoPlayer connect(String host) throws IOException;
    Process launch(String host) throws IOException;
}

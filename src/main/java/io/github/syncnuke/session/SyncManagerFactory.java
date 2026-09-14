package io.github.syncnuke.session;

import io.github.syncnuke.client.SyncManager;
import io.github.syncnuke.player.VideoPlayer;

@FunctionalInterface
public interface SyncManagerFactory {
    SyncManager create(VideoPlayer player);
}

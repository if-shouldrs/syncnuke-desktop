package io.github.syncnuke.session;

import io.github.syncnuke.player.PlayerRuntime;

@FunctionalInterface
public interface PlayerRuntimeFactory {
    PlayerRuntime create();
}

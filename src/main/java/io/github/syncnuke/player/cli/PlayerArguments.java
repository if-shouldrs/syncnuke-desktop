package io.github.syncnuke.player.cli;

public record PlayerArguments(
        String player,
        String host,
        boolean launch,
        String executable
) {
}

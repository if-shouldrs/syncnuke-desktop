package io.github.syncnuke.player.cli;

import java.io.IOException;

interface PlayerCliConfigurator {

    PlayerArguments configure(PlayerArguments arguments) throws IOException;

}

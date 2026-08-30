package io.github.syncnuke.player.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class PlayerCli {

    private PlayerCli() {
    }

    public static PlayerArguments configure(PlayerArguments arguments) throws IOException {
        BufferedReader input = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        return configure(arguments, input, System.out);
    }

    static PlayerArguments configure(
            PlayerArguments arguments,
            BufferedReader input,
            PrintStream output
    ) throws IOException {
        boolean interactiveSelection = isEmpty(arguments.player());
        String player = interactiveSelection ? selectPlayer(input, output) : arguments.player();
        PlayerArguments selectedArguments = new PlayerArguments(
                player,
                arguments.host(),
                arguments.launch() || interactiveSelection,
                arguments.executable()
        );

        return getConfigurator(player, input, output).configure(selectedArguments);
    }

    private static String selectPlayer(BufferedReader input, PrintStream output) throws IOException {
        output.println("Select a video player:");
        output.println("1) MPV");
        output.print("Selection: ");
        output.flush();

        String selection = input.readLine();
        if ("1".equals(selection)) {
            return "mpv";
        }
        throw new IllegalArgumentException("Invalid player selection: " + selection);
    }

    private static PlayerCliConfigurator getConfigurator(
            String player,
            BufferedReader input,
            PrintStream output
    ) {
        if ("mpv".equals(player.toLowerCase(Locale.ROOT))) {
            return new MpvCli(input, output);
        }
        throw new IllegalArgumentException("Unsupported video player: " + player);
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

}

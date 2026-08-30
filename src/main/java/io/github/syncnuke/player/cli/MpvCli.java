package io.github.syncnuke.player.cli;

import io.github.syncnuke.player.integration.mpv.MpvProvider;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

final class MpvCli implements PlayerCliConfigurator {

    private static final String CONFIG_DIRECTORY_PROPERTY = "syncnuke.config.directory";

    private final BufferedReader input;
    private final PrintStream output;

    MpvCli(BufferedReader input, PrintStream output) {
        this.input = input;
        this.output = output;
    }

    @Override
    public PlayerArguments configure(PlayerArguments arguments) throws IOException {
        if (!isEmpty(arguments.executable())) {
            return arguments;
        }

        MpvProvider provider = new MpvProvider(null);
        if (provider.findExecutable().isPresent() || provider.canConnect(arguments.host())) {
            return arguments;
        }

        Optional<String> savedExecutable = readSavedExecutable();
        if (savedExecutable.isPresent()) {
            Optional<String> resolvedExecutable = new MpvProvider(savedExecutable.get()).findExecutable();
            if (resolvedExecutable.isPresent()) {
                return withExecutable(arguments, resolvedExecutable.get());
            }
            clearSavedExecutable();
        }

        String executable = promptForExecutable();
        if (isEmpty(executable)) {
            throw new IOException("An MPV executable path is required");
        }

        String resolvedExecutable = new MpvProvider(executable).findExecutable()
                .orElseThrow(() -> new IOException("MPV executable not found: " + executable));
        saveExecutable(resolvedExecutable);
        return withExecutable(arguments, resolvedExecutable);
    }

    private PlayerArguments withExecutable(PlayerArguments arguments, String executable) {
        return new PlayerArguments(
                arguments.player(),
                arguments.host(),
                executable
        );
    }

    private Optional<String> readSavedExecutable() throws IOException {
        Path executableFile = executableFile();
        if (!Files.isRegularFile(executableFile)) {
            return Optional.empty();
        }

        String executable = Files.readString(executableFile, StandardCharsets.UTF_8).strip();
        return executable.isEmpty() ? Optional.empty() : Optional.of(executable);
    }

    private void clearSavedExecutable() throws IOException {
        Files.deleteIfExists(executableFile());
    }

    private String promptForExecutable() throws IOException {
        output.print("MPV was not found on PATH. Enter the path to the MPV executable: ");
        output.flush();

        String executable = input.readLine();
        return executable == null ? null : executable.strip();
    }

    private void saveExecutable(String executable) throws IOException {
        Path executableFile = executableFile();
        Files.createDirectories(executableFile.getParent());
        Files.writeString(
                executableFile,
                executable + System.lineSeparator(),
                StandardCharsets.UTF_8
        );
    }

    private Path executableFile() {
        String configuredDirectory = System.getProperty(CONFIG_DIRECTORY_PROPERTY);
        Path configDirectory = isEmpty(configuredDirectory)
                ? Path.of(System.getProperty("user.home"), ".syncnuke")
                : Path.of(configuredDirectory);
        return configDirectory.resolve("players").resolve("mpv").resolve("executable");
    }

    private boolean isEmpty(String value) {
        return value == null || value.isBlank();
    }

}

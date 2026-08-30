package io.github.syncnuke.player.integration.mpv;

import io.github.syncnuke.player.PlayerProvider;
import io.github.syncnuke.player.VideoPlayer;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.apache.commons.lang3.StringUtils.defaultIfEmpty;
import static org.apache.commons.lang3.StringUtils.isEmpty;

@Slf4j
public final class MpvProvider implements PlayerProvider {

    private final MpvLauncher launcher;

    public MpvProvider(String executable) {
        launcher = new MpvLauncher(executable);
    }

    public Optional<String> findExecutable() throws IOException {
        return launcher.findExecutable();
    }

    public boolean canConnect(String host) {
        try (MpvPlayer ignored = connectPlayer(host)) {
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    @Override
    public VideoPlayer connect(String host) throws IOException {
        return connectPlayer(host);
    }

    private MpvPlayer connectPlayer(String host) throws IOException {
        if (isEmpty(host)) {
            host = getDefaultHost();
        }
        return new MpvPlayer(host);
    }

    @Override
    public Process launch(String host) throws IOException {
        if (isEmpty(host)) {
            host = getDefaultHost();
        }
        log.info("Starting MPV with IPC endpoint {}", host);
        return launcher.launch(host);
    }

    private String getDefaultHost() throws IOException {
        if (isWindows()) {
            return "\\\\.\\pipe\\mpvsocket";
        }
        return runtimeDirectory().resolve("mpvsocket").toString();
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    private static Path runtimeDirectory() throws IOException {
        Path directory = Path.of(System.getProperty("user.home"), ".mpv-ipc");
        Files.createDirectories(directory);
        return directory;
    }

    private static final class MpvLauncher {

        private static final String MPV_EXECUTABLE_PROPERTY = "syncnuke.mpv.executable";

        private final String executable;

        private MpvLauncher(String executable) {
            this.executable = defaultIfEmpty(
                    executable,
                    System.getProperty(MPV_EXECUTABLE_PROPERTY, "mpv")
            );
        }

        private Optional<String> findExecutable() throws IOException {
            String normalizedExecutable = normalizeExecutable(executable);

            try {
                Path executablePath = Path.of(normalizedExecutable);
                if (Files.isDirectory(executablePath)) {
                    return findInDirectory(executablePath);
                }
                if (isExecutable(executablePath)) {
                    return Optional.of(executablePath.toRealPath().toString());
                }
                if (executablePath.isAbsolute() || executablePath.getParent() != null) {
                    return Optional.empty();
                }
            } catch (InvalidPathException exception) {
                throw new IOException("Invalid MPV executable path: " + executable, exception);
            }

            return findOnPath(normalizedExecutable);
        }

        private Process launch(String host) throws IOException {
            String launchExecutable = findExecutable()
                    .orElseThrow(() -> new IOException("MPV executable not found: " + executable));
            // Let MPV log to its own file instead of mixing its output into application logs.
            Path logFile = runtimeDirectory().resolve("mpv.log");
            return new ProcessBuilder(
                    launchExecutable,
                    "--idle=yes",
                    "--force-window=yes",
                    "--input-ipc-server=" + host,
                    "--log-file=" + logFile
            ).redirectOutput(ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
            .start();
        }

        private Optional<String> findInDirectory(Path directory) throws IOException {
            for (String executableName : executableNames()) {
                Path candidate = directory.resolve(executableName);
                if (isExecutable(candidate)) {
                    return Optional.of(candidate.toRealPath().toString());
                }
            }
            return Optional.empty();
        }

        private Optional<String> findOnPath(String executableName) throws IOException {
            String path = System.getenv("PATH");
            if (path == null || path.isBlank()) {
                return Optional.empty();
            }

            List<String> candidates = commandNames(executableName);
            for (String directory : path.split(Pattern.quote(File.pathSeparator))) {
                for (String candidateName : candidates) {
                    Path candidate = Path.of(directory.isEmpty() ? "." : directory).resolve(candidateName);
                    if (isExecutable(candidate)) {
                        return Optional.of(candidate.toRealPath().toString());
                    }
                }
            }
            return Optional.empty();
        }

        private List<String> commandNames(String executableName) {
            if (!isWindows()) {
                return List.of(executableName);
            }
            if ("mpv".equalsIgnoreCase(executableName)) {
                return executableNames();
            }
            return executableName.toLowerCase().endsWith(".exe")
                    ? List.of(executableName)
                    : List.of(executableName, executableName + ".exe");
        }

        private List<String> executableNames() {
            return isWindows() ? List.of("mpv.exe", "mpvnet.exe") : List.of("mpv");
        }

        private boolean isExecutable(Path path) {
            return Files.isRegularFile(path) && (isWindows() || Files.isExecutable(path));
        }

        private String normalizeExecutable(String value) {
            String normalizedValue = value.strip();
            if (normalizedValue.length() >= 2
                    && normalizedValue.startsWith("\"")
                    && normalizedValue.endsWith("\"")) {
                normalizedValue = normalizedValue.substring(1, normalizedValue.length() - 1);
            }

            if (normalizedValue.equals("~")) {
                return System.getProperty("user.home");
            }
            if (normalizedValue.startsWith("~/") || normalizedValue.startsWith("~\\")) {
                return Path.of(
                        System.getProperty("user.home"),
                        normalizedValue.substring(2)
                ).toString();
            }
            return normalizedValue;
        }

    }

}

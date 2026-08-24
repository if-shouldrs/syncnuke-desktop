package io.github.syncnuke.player;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.syncnuke.player.data.PlaybackState;
import io.github.syncnuke.player.data.PlayerState;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public final class MpvPlayer implements VideoPlayer {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger();

    private final Closeable connection;
    private final BufferedWriter writer;
    private final BufferedReader reader;
    private final ExecutorService ioExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "mpv-ipc-listener");
        t.setDaemon(true);
        return t;
    });

    private final ScheduledExecutorService keepAliveExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "mpv-keep-alive");
        t.setDaemon(true);
        return t;
    });

    public MpvPlayer(@NonNull String ipcPath) throws IOException {
        IpcConnection ipcConnection = openIpcConnection(ipcPath);
        this.connection = ipcConnection;
        this.writer = new BufferedWriter(new OutputStreamWriter(ipcConnection.output, StandardCharsets.UTF_8));
        this.reader = new BufferedReader(new InputStreamReader(ipcConnection.input, StandardCharsets.UTF_8));

        startResponsePump();
        startKeepAlivePings();
        log.info("Connected to MPV IPC at {}", ipcPath);
    }

    private static IpcConnection openIpcConnection(String ipcPath) throws IOException {
        long deadline = System.nanoTime() + CONNECT_TIMEOUT.toNanos();
        IOException lastException = null;

        do {
            try {
                return isWindows() ? openWindowsNamedPipe(ipcPath) : openUnixSocket(ipcPath);
            } catch (IOException exception) {
                lastException = exception;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for MPV IPC", interruptedException);
                }
            }
        } while (System.nanoTime() < deadline);

        throw new IOException("Could not connect to MPV IPC at '" + ipcPath + "' within " +
                CONNECT_TIMEOUT.toSeconds() + " seconds", lastException);
    }

    private static IpcConnection openUnixSocket(String ipcPath) throws IOException {
        File socketFile = new File(ipcPath);
        if (!socketFile.exists()) {
            throw new FileNotFoundException("MPV socket does not exist: " + ipcPath);
        }

        AFUNIXSocket socket = AFUNIXSocket.newInstance();
        try {
            socket.connect(AFUNIXSocketAddress.of(socketFile));
            socket.setSoTimeout((int) READ_TIMEOUT.toMillis());
            return new IpcConnection(socket, socket.getInputStream(), socket.getOutputStream());
        } catch (IOException exception) {
            socket.close();
            throw exception;
        }
    }

    private static IpcConnection openWindowsNamedPipe(String ipcPath) throws IOException {
        RandomAccessFile pipe = new RandomAccessFile(ipcPath, "rw");
        try {
            FileDescriptor descriptor = pipe.getFD();
            InputStream input = new NonClosingInputStream(new FileInputStream(descriptor));
            OutputStream output = new NonClosingOutputStream(new FileOutputStream(descriptor));
            return new IpcConnection(pipe, input, output);
        } catch (IOException exception) {
            pipe.close();
            throw exception;
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }

    @Override
    public void play() {
        setProperty("pause", false);
    }

    @Override
    public void pause() {
        setProperty("pause", true);
    }

    @Override
    public void seek(double position) {
        sendCommand(MAPPER.createArrayNode()
                .add("set_property")
                .add("time-pos")
                .add(position));
    }

    @Override
    public PlayerState getStatus() {
        PlayerState status = new PlayerState();
        status.setPlaybackState(isPaused() ? PlaybackState.PAUSED : PlaybackState.PLAYING);
        status.setPosition(getPosition());
        status.setPlaybackSpeed(getPlaybackSpeed());
        status.setLastUpdateTime(System.currentTimeMillis());
        return status;
    }

    private double getPosition() {
        JsonNode value = getProperty("time-pos");
        if (value == null || !value.isNumber()) {
            throw new IllegalStateException("MPV returned an invalid time-pos value");
        }
        return value.asDouble();
    }

    private double getPlaybackSpeed() {
        JsonNode value = getProperty("speed");
        if (value == null || !value.isNumber()) {
            throw new IllegalStateException("MPV returned an invalid speed value");
        }
        return value.asDouble();
    }

    private boolean isPaused() {
        JsonNode value = getProperty("pause");
        if (value == null || !value.isBoolean()) {
            throw new IllegalStateException("MPV returned an invalid pause value");
        }
        return value.asBoolean();
    }

    @Override
    public void load(String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("File does not exist: {}", filePath);
            return;
        }
        sendCommand(MAPPER.createArrayNode()
                .add("loadfile")
                .add(filePath));
    }

    /* ---------------- internal helpers ------------- */

    private void setProperty(String name, Object value) {
        sendCommand(MAPPER.createArrayNode()
                .add("set_property")
                .add(name)
                .addPOJO(value));
    }

    private JsonNode getProperty(String name) {
        return sendCommandForResult(MAPPER.createArrayNode()
                .add("get_property")
                .add(name));
    }

    private JsonNode sendCommandForResult(ArrayNode command) {
        int reqId = REQUEST_COUNTER.incrementAndGet();
        ObjectNode msg = MAPPER.createObjectNode()
                .put("request_id", reqId)
                .set("command", command);

        CompletableFuture<JsonNode> answer = new CompletableFuture<>();
        pendingReplies.put(reqId, answer);

        sendRaw(msg);

        try {
            return answer.get(READ_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException | TimeoutException | ExecutionException e) {
            log.error("IPC request {} failed: {}", reqId, e.toString());
            return null;
        } finally {
            pendingReplies.remove(reqId);
        }
    }

    private void sendCommand(ArrayNode command) {
        ObjectNode msg = MAPPER.createObjectNode().set("command", command);
        sendRaw(msg);
    }

    private synchronized void sendRaw(JsonNode obj) {
        try {
            String json = MAPPER.writeValueAsString(obj);
            writer.write(json);
            writer.write('\n');
            writer.flush();
        } catch (IOException e) {
            log.error("Failed to write to MPV socket: {}", e.toString());
        }
    }

    /* -------- asynchronous response pump -------------- */

    private final ConcurrentMap<Integer, CompletableFuture<JsonNode>> pendingReplies = new ConcurrentHashMap<>();

    private void startResponsePump() {
        ioExecutor.submit(() -> {
            String line;
            try {
                while ((line = reader.readLine()) != null) {
                    if (line.isBlank()) continue;
                    log.debug("Received raw response from MPV: {}", line);
                    JsonNode node = MAPPER.readTree(line);

                    if (node.has("request_id")) {
                        int id = node.path("request_id").asInt();
                        CompletableFuture<JsonNode> cf = pendingReplies.get(id);
                        if (cf != null) {
                            if (node.has("error") && !"success".equals(node.get("error").asText())) {
                                cf.completeExceptionally(new IOException("MPV error: " + node.get("error").asText()));
                            } else {
                                cf.complete(node.get("data"));
                            }
                            continue;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Response pump stopped due to IOException: {}", e.getMessage(), e);
            } catch (Exception e) {
                log.error("Unexpected error in response pump: {}", e.getMessage(), e);
            }
        });
    }

    private void startKeepAlivePings() {
        keepAliveExecutor.scheduleAtFixedRate(() -> {
            try {
                getProperty("pause");
            } catch (Exception e) {
                log.warn("Keep-alive ping failed: {}", e.getMessage());
            }
        }, 1, 3, TimeUnit.SECONDS);
    }

    @Override
    public void close() {
        keepAliveExecutor.shutdownNow();
        ioExecutor.shutdownNow();

        try {
            writer.close();
        } catch (IOException e) {
            log.error("Failed to close MPV IPC writer: {}", e.toString());
        }
        try {
            reader.close();
        } catch (IOException e) {
            log.error("Failed to close MPV IPC reader: {}", e.toString());
        }
        try {
            connection.close();
        } catch (IOException e) {
            log.error("Failed to close MPV IPC connection: {}", e.toString());
        }
    }

    private static final class IpcConnection implements Closeable {
        private final Closeable handle;
        private final InputStream input;
        private final OutputStream output;

        private IpcConnection(Closeable handle, InputStream input, OutputStream output) {
            this.handle = handle;
            this.input = input;
            this.output = output;
        }

        @Override
        public void close() throws IOException {
            handle.close();
        }
    }

    private static final class NonClosingInputStream extends FilterInputStream {
        private NonClosingInputStream(InputStream input) {
            super(input);
        }

        @Override
        public void close() {
            // The RandomAccessFile owns the shared Windows named-pipe handle.
        }
    }

    private static final class NonClosingOutputStream extends FilterOutputStream {
        private NonClosingOutputStream(OutputStream output) {
            super(output);
        }

        @Override
        public void close() throws IOException {
            flush();
        }
    }

}

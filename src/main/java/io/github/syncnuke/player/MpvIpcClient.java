package io.github.syncnuke.player;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.syncnuke.player.ipc.IpcConnection;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
final class MpvIpcClient implements AutoCloseable {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final AtomicInteger REQUEST_COUNTER = new AtomicInteger();

    private final IpcConnection connection;
    private final BufferedWriter writer;
    private final BufferedReader reader;

    private MpvIpcClient(IpcConnection connection) {
        this.connection = connection;
        this.writer = new BufferedWriter(new OutputStreamWriter(
                connection.getOutput(),
                StandardCharsets.UTF_8
        ));
        this.reader = new BufferedReader(new InputStreamReader(
                connection.getInput(),
                StandardCharsets.UTF_8
        ));
    }

    static MpvIpcClient connect(String ipcPath) throws IOException {
        return new MpvIpcClient(IpcConnection.open(ipcPath));
    }

    void command(String name, Object... arguments) {
        sendCommandForResult(createCommand(name, arguments));
    }

    void setProperty(String name, Object value) {
        command("set_property", name, value);
    }

    <T> T getProperty(String name, Class<T> type) {
        JsonNode value = sendCommandForResult(createCommand("get_property", name));
        if (value == null || !isExpectedType(value, type)) {
            throw new IllegalStateException("MPV returned an invalid " + name + " value");
        }

        try {
            return MAPPER.treeToValue(value, type);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("MPV returned an invalid " + name + " value", exception);
        }
    }

    private static boolean isExpectedType(JsonNode value, Class<?> type) {
        if (type == Double.class) {
            return value.isNumber();
        }
        if (type == Boolean.class) {
            return value.isBoolean();
        }
        return !value.isNull();
    }

    private static ArrayNode createCommand(String name, Object... arguments) {
        ArrayNode command = MAPPER.createArrayNode().add(name);
        for (Object argument : arguments) {
            command.addPOJO(argument);
        }
        return command;
    }

    private synchronized JsonNode sendCommandForResult(ArrayNode command) {
        int reqId = REQUEST_COUNTER.incrementAndGet();
        ObjectNode msg = MAPPER.createObjectNode()
                .put("request_id", reqId)
                .set("command", command);

        try {
            String json = MAPPER.writeValueAsString(msg);
            writer.write(json);
            writer.write('\n');
            writer.flush();

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.isBlank()) continue;
                log.debug("Received raw response from MPV: {}", line);
                JsonNode node = MAPPER.readTree(line);

                if (node.path("request_id").asInt() == reqId) {
                    if (node.has("error") && !"success".equals(node.get("error").asText())) {
                        throw new IOException("MPV error: " + node.get("error").asText());
                    }
                    return node.get("data");
                }
            }
            throw new EOFException("MPV closed the IPC connection");
        } catch (IOException e) {
            log.error("IPC request {} failed: {}", reqId, e.toString());
            return null;
        }
    }

    @Override
    public void close() {
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

}

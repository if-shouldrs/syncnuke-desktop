package io.github.syncnuke.player.ipc;

import lombok.Getter;
import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

import java.io.Closeable;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.time.Duration;

public final class IpcConnection implements Closeable {

    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);

    private final Closeable handle;
    @Getter
    private final InputStream input;
    @Getter
    private final OutputStream output;

    private IpcConnection(Closeable handle, InputStream input, OutputStream output) {
        this.handle = handle;
        this.input = input;
        this.output = output;
    }

    public static IpcConnection open(String ipcPath) throws IOException {
        long deadline = System.nanoTime() + CONNECT_TIMEOUT.toNanos();
        IOException lastException;

        do {
            try {
                return isWindows() ? openWindowsNamedPipe(ipcPath) : openUnixSocket(ipcPath);
            } catch (IOException exception) {
                lastException = exception;
                try {
                    Thread.sleep(100);
                } catch (InterruptedException interruptedException) {
                    Thread.currentThread().interrupt();
                    throw new IOException("Interrupted while waiting for IPC endpoint", interruptedException);
                }
            }
        } while (System.nanoTime() < deadline);

        throw new IOException("Could not connect to IPC endpoint at '" + ipcPath + "' within " +
                CONNECT_TIMEOUT.toSeconds() + " seconds", lastException);
    }

    private static IpcConnection openUnixSocket(String ipcPath) throws IOException {
        File socketFile = new File(ipcPath);
        if (!socketFile.exists()) {
            throw new FileNotFoundException("IPC socket does not exist: " + ipcPath);
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
    public void close() throws IOException {
        handle.close();
    }

}

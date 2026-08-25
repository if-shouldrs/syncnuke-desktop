package io.github.syncnuke.player.ipc;

import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

final class NonClosingOutputStream extends FilterOutputStream {

    NonClosingOutputStream(OutputStream output) {
        super(output);
    }

    @Override
    public void close() throws IOException {
        flush();
    }

}

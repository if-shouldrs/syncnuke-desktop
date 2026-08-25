package io.github.syncnuke.player.ipc;

import java.io.FilterInputStream;
import java.io.InputStream;

final class NonClosingInputStream extends FilterInputStream {

    NonClosingInputStream(InputStream input) {
        super(input);
    }

    @Override
    public void close() {
        // The owner of the wrapped stream is responsible for closing it.
    }

}

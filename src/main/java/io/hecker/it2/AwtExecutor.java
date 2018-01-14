package io.hecker.it2;

import java.awt.*;
import java.util.concurrent.Executor;

class AwtExecutor implements Executor {
    private static final AwtExecutor INSTANCE = new AwtExecutor();

    static AwtExecutor instance() {
        return INSTANCE;
    }

    public void execute(Runnable r) {
        EventQueue.invokeLater(r);
    }
}

package com.roommatch.ui;

import java.util.concurrent.Callable;
import java.util.function.Consumer;
import javax.swing.SwingWorker;

/**
 * Runs slow work (AI calls) off the Swing event thread so the window never freezes.
 * Tests use {@link #immediate()}, which runs everything on the calling thread.
 */
public interface GuiTasks {
    <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError);

    static GuiTasks background() {
        return new GuiTasks() {
            @Override
            public <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
                new SwingWorker<T, Void>() {
                    @Override protected T doInBackground() throws Exception {
                        return work.call();
                    }

                    @Override protected void done() {
                        try {
                            onSuccess.accept(get());
                        } catch (java.util.concurrent.ExecutionException e) {
                            onError.accept(e.getCause());
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            onError.accept(e);
                        }
                    }
                }.execute();
            }
        };
    }

    static GuiTasks immediate() {
        return new GuiTasks() {
            @Override
            public <T> void run(Callable<T> work, Consumer<T> onSuccess, Consumer<Throwable> onError) {
                T result;
                try {
                    result = work.call();
                } catch (Throwable t) {
                    onError.accept(t);
                    return;
                }
                onSuccess.accept(result);
            }
        };
    }
}

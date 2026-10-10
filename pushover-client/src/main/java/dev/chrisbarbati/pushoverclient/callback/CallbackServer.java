package dev.chrisbarbati.pushoverclient.callback;

import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

import java.net.URI;
import java.util.function.Consumer;

/**
 * Pluggable HTTP callback server used to receive Pushover emergency callback POSTs.
 *
 * @since 0.3.0
 * @author Christian Barbati
 */
public interface CallbackServer extends AutoCloseable {
    /**
     * Starts the callback server when it is not already running.
     *
     * @param callbackHandler consumer invoked for each parsed callback payload
     * @throws Exception when the callback server fails to start
     * @since 0.3.0
     */
    void start(Consumer<ReceiptCallbackResponse> callbackHandler) throws Exception;

    /**
     * Stops the callback server.
     *
     * @throws Exception when shutdown fails
     * @since 0.3.0
     */
    void stop() throws Exception;

    /**
     * @return true when the server is running and able to receive callbacks
     * @since 0.3.0
     */
    boolean isRunning();

    /**
     * @return the effective callback URI handled by this server
     * @since 0.3.0
     */
    URI callbackUri();

    @Override
    default void close() throws Exception {
        stop();
    }
}



package org.chrisbarbati.pushoverclient.callback.jetty;

import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for Jetty callback server lifecycle and constructor validation.
 *
 * @since 0.5.0
 * @author Christian Barbati
 */
class JettyCallbackServerTest {

    /**
     * Verifies the callback server requires a ThreadPoolExecutor-backed executor.
     *
     * @since 0.5.0
     */
    @Test
    void constructor_rejectsExecutorServiceThatIsNotThreadPoolExecutorBacked() {
        ExecutorService executorService = Executors.newSingleThreadExecutor();

        try {
            assertThrows(
                    PushoverClientException.class,
                    () -> new JettyCallbackServer(URI.create("http://127.0.0.1:0/pushover/callback"), executorService)
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies bind URI validation rejects unsupported schemes and missing hosts.
     *
     * @since 0.5.0
     */
    @Test
    void constructor_rejectsInvalidBindUri() {
        ExecutorService executorService = Executors.newCachedThreadPool();

        try {
            assertThrows(
                    PushoverClientException.class,
                    () -> new JettyCallbackServer(URI.create("ftp://127.0.0.1:21/pushover/callback"), executorService)
            );
            assertThrows(
                    PushoverClientException.class,
                    () -> new JettyCallbackServer(URI.create("http:/pushover/callback"), executorService)
            );
        } finally {
            executorService.shutdownNow();
        }
    }

    /**
     * Verifies start/stop remain idempotent while callbackUri reflects the bound port only while running.
     *
     * @since 0.5.0
     */
    @Test
    void startAndStop_areIdempotent_andCallbackUriResetsAfterStop() throws Exception {
        URI bindUri = URI.create("http://127.0.0.1:0/pushover/callback");
        ExecutorService executorService = Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(bindUri, executorService);

        try {
            assertEquals(bindUri, callbackServer.callbackUri());
            assertFalse(callbackServer.isRunning());

            callbackServer.start(callback -> {
            });
            URI runningCallbackUri = callbackServer.callbackUri();

            assertTrue(callbackServer.isRunning());
            assertTrue(runningCallbackUri.getPort() > 0);
            assertEquals(bindUri.getHost(), runningCallbackUri.getHost());
            assertEquals(bindUri.getPath(), runningCallbackUri.getPath());

            assertDoesNotThrow(() -> callbackServer.start(callback -> {
            }));
            assertEquals(runningCallbackUri, callbackServer.callbackUri());

            callbackServer.stop();
            assertFalse(callbackServer.isRunning());
            assertEquals(bindUri, callbackServer.callbackUri());

            assertDoesNotThrow(callbackServer::stop);
            assertFalse(callbackServer.isRunning());
            assertEquals(bindUri, callbackServer.callbackUri());
        } finally {
            callbackServer.stop();
            executorService.shutdownNow();
        }
    }
}


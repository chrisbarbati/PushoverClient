package dev.chrisbarbati.pushoverclient.callback.jetty;

import dev.chrisbarbati.pushoverclient.callback.CallbackServer;
import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;

import java.net.URI;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.function.Consumer;

/**
 * Jetty-backed callback server implementation for receiving emergency callbacks.
 *
 * @since 0.3.0
 * @author Christian Barbati
 */
public final class JettyCallbackServer implements CallbackServer {
    private final URI bindUri;
    private final ThreadPoolExecutor threadPoolExecutor;

    private volatile Consumer<ReceiptCallbackResponse> callbackHandler;
    private volatile Server server;
    private volatile URI effectiveCallbackUri;

    /**
     * Creates a Jetty callback server.
     *
     * @param bindUri local URI to bind, for example {@code http://0.0.0.0:8080/pushover/callback}
     * @param executorService executor used by Jetty so thread ownership remains caller-controlled
     * @since 0.3.0
     */
    public JettyCallbackServer(URI bindUri, ExecutorService executorService) {
        this.bindUri = validateBindUri(bindUri);
        ExecutorService normalizedExecutorService = Objects.requireNonNull(executorService, "executorService is required");
        if (!(normalizedExecutorService instanceof ThreadPoolExecutor executor)) {
            throw new PushoverClientException("JettyCallbackServer requires an ExecutorService backed by ThreadPoolExecutor");
        }
        this.threadPoolExecutor = executor;
        this.effectiveCallbackUri = this.bindUri;
    }

    @Override
    public synchronized void start(Consumer<ReceiptCallbackResponse> callbackHandler) throws Exception {
        this.callbackHandler = Objects.requireNonNull(callbackHandler, "callbackHandler is required");
        if (isRunning()) {
            return;
        }

        Server newServer = new Server(new ExecutorThreadPool(threadPoolExecutor));
        ServerConnector connector = new ServerConnector(newServer);
        connector.setHost(bindUri.getHost());
        connector.setPort(Math.max(bindUri.getPort(), 0));
        newServer.addConnector(connector);

        newServer.setHandler(JettyCallbackHandlers.create(bindUri, this.callbackHandler));
        newServer.start();

        this.server = newServer;
        this.effectiveCallbackUri = rebuildUri(bindUri, connector.getLocalPort());
    }

    @Override
    public synchronized void stop() throws Exception {
        if (server != null) {
            server.stop();
            server = null;
        }
        effectiveCallbackUri = bindUri;
    }

    @Override
    public boolean isRunning() {
        return server != null && server.isRunning();
    }

    @Override
    public URI callbackUri() {
        return effectiveCallbackUri;
    }

    private static URI validateBindUri(URI bindUri) {
        Objects.requireNonNull(bindUri, "bindUri is required");
        String scheme = bindUri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new PushoverClientException("bindUri must use http or https");
        }
        if (bindUri.getHost() == null || bindUri.getHost().isBlank()) {
            throw new PushoverClientException("bindUri host is required");
        }
        return bindUri;
    }

    private static URI rebuildUri(URI original, int localPort) {
        return URI.create(String.format(
                "%s://%s:%d%s",
                original.getScheme(),
                original.getHost(),
                localPort,
                JettyCallbackHandlers.normalizePath(original.getPath())
        ));
    }
}





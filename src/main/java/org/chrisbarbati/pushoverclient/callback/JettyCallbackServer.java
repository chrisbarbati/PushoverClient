package org.chrisbarbati.pushoverclient.callback;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.thread.ExecutorThreadPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
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
    private static final Logger LOGGER = LoggerFactory.getLogger(JettyCallbackServer.class);

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
     * @author Christian Barbati
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

        newServer.setHandler(new CallbackHandler());
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

    private final class CallbackHandler extends AbstractHandler {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
            baseRequest.setHandled(true);

            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Method Not Allowed");
                return;
            }

            if (!normalizePath(bindUri.getPath()).equals(target)) {
                LOGGER.warn("Received callback request for unexpected path: expected={}, actual={}", normalizePath(bindUri.getPath()), target);
                response.setStatus(HttpServletResponse.SC_NOT_FOUND);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Not Found");
                return;
            }

            try {
                String body = new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                Map<String, String> fields = parseForm(body);
                LOGGER.info("Received Jetty callback POST: path={}, fieldNames={}", target, fields.keySet());
                ReceiptCallbackResponse callbackResponse = ReceiptCallbackResponse.fromFields(fields);
                LOGGER.info("Parsed Jetty callback receipt={}", callbackResponse.receipt());
                callbackHandler.accept(callbackResponse);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("OK");
            } catch (PushoverClientException ex) {
                LOGGER.warn("Rejecting Jetty callback POST: {}", ex.getMessage());
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(ex.getMessage());
            }
        }
    }

    private static Map<String, String> parseForm(String body) {
        Map<String, String> values = new LinkedHashMap<>();
        if (body == null || body.isBlank()) {
            return values;
        }

        for (String pair : body.split("&")) {
            if (pair.isBlank()) {
                continue;
            }
            String[] parts = pair.split("=", 2);
            String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
            String value = parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            values.put(key, value);
        }
        return values;
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

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private static URI rebuildUri(URI original, int localPort) {
        return URI.create(String.format(
                "%s://%s:%d%s",
                original.getScheme(),
                original.getHost(),
                localPort,
                normalizePath(original.getPath())
        ));
    }
}




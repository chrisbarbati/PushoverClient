package dev.chrisbarbati.pushoverclient.callback.tomcat;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;
import dev.chrisbarbati.pushoverclient.callback.CallbackPayloadParser;
import dev.chrisbarbati.pushoverclient.callback.CallbackServer;
import dev.chrisbarbati.pushoverclient.exception.PushoverClientException;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Tomcat-backed callback server implementation for receiving emergency callbacks.
 *
 * @since 0.5.0
 * @author Christian Barbati
 */
public final class TomcatCallbackServer implements CallbackServer {
    private static final String CONTEXT_PATH = "";
    private static final String CALLBACK_SERVLET_NAME = "pushoverCallbackServlet";

    private final URI bindUri;
    private final ExecutorService executorService;

    private volatile Consumer<ReceiptCallbackResponse> callbackHandler;
    private volatile Tomcat tomcat;
    private volatile Connector connector;
    private volatile URI effectiveCallbackUri;
    private volatile Path baseDir;
    private volatile Path docBase;

    /**
     * Creates a Tomcat callback server.
     *
     * @param bindUri local URI to bind, for example {@code http://0.0.0.0:8080/pushover/callback}
     * @param executorService executor used by Tomcat so thread ownership remains caller-controlled
     * @since 0.5.0
     */
    public TomcatCallbackServer(URI bindUri, ExecutorService executorService) {
        this.bindUri = validateBindUri(bindUri);
        this.executorService = Objects.requireNonNull(executorService, "executorService is required");
        this.effectiveCallbackUri = this.bindUri;
    }

    @Override
    public synchronized void start(Consumer<ReceiptCallbackResponse> callbackHandler) throws Exception {
        this.callbackHandler = Objects.requireNonNull(callbackHandler, "callbackHandler is required");
        if (isRunning()) {
            return;
        }

        Path newBaseDir = Files.createTempDirectory("pushover-client-tomcat-base-");
        Path newDocBase = Files.createTempDirectory("pushover-client-tomcat-docbase-");

        try {
            Tomcat newTomcat = new Tomcat();
            newTomcat.setBaseDir(newBaseDir.toString());

            Connector newConnector = new Connector();
            newConnector.setThrowOnFailure(true);
            newConnector.setPort(Math.max(bindUri.getPort(), 0));
            newConnector.setProperty("address", bindUri.getHost());
            configureExecutor(newConnector, executorService);
            newTomcat.setConnector(newConnector);

            Context context = newTomcat.addContext(CONTEXT_PATH, newDocBase.toString());
            Tomcat.addServlet(context, CALLBACK_SERVLET_NAME, new CallbackServlet(this.callbackHandler));
            context.addServletMappingDecoded(normalizePath(bindUri.getPath()), CALLBACK_SERVLET_NAME);

            newTomcat.start();

            this.tomcat = newTomcat;
            this.connector = newConnector;
            this.baseDir = newBaseDir;
            this.docBase = newDocBase;
            this.effectiveCallbackUri = rebuildUri(bindUri, newConnector.getLocalPort());
        } catch (Exception ex) {
            deleteDirectoryIfExists(newDocBase);
            deleteDirectoryIfExists(newBaseDir);
            throw ex;
        }
    }

    @Override
    public synchronized void stop() throws Exception {
        Exception stopFailure = null;
        if (tomcat != null) {
            try {
                tomcat.stop();
            } catch (Exception ex) {
                stopFailure = ex;
            } finally {
                try {
                    tomcat.destroy();
                } catch (Exception ex) {
                    if (stopFailure == null) {
                        stopFailure = ex;
                    } else {
                        stopFailure.addSuppressed(ex);
                    }
                }
                tomcat = null;
                connector = null;
            }
        }

        deleteDirectoryIfExists(docBase);
        deleteDirectoryIfExists(baseDir);
        docBase = null;
        baseDir = null;
        effectiveCallbackUri = bindUri;

        if (stopFailure != null) {
            throw stopFailure;
        }
    }

    @Override
    public boolean isRunning() {
        Connector currentConnector = this.connector;
        return currentConnector != null && currentConnector.getState().isAvailable();
    }

    @Override
    public URI callbackUri() {
        return effectiveCallbackUri;
    }

    private static void configureExecutor(Connector connector, ExecutorService executorService) {
        if (!(connector.getProtocolHandler() instanceof AbstractProtocol<?> protocolHandler)) {
            throw new PushoverClientException("TomcatCallbackServer requires an AbstractProtocol-based connector");
        }
        protocolHandler.setExecutor(executorService);
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
                normalizePath(original.getPath())
        ));
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private static void deleteDirectoryIfExists(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to delete " + path, ex);
                        }
                    });
        } catch (IllegalStateException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }

    private static final class CallbackServlet extends HttpServlet {
        private final Consumer<ReceiptCallbackResponse> callbackHandler;

        private CallbackServlet(Consumer<ReceiptCallbackResponse> callbackHandler) {
            this.callbackHandler = Objects.requireNonNull(callbackHandler, "callbackHandler is required");
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            try {
                ReceiptCallbackResponse callbackResponse = CallbackPayloadParser.parseFormEncodedBody(
                        new String(request.getInputStream().readAllBytes(), StandardCharsets.UTF_8)
                );
                callbackHandler.accept(callbackResponse);
                response.setStatus(HttpServletResponse.SC_OK);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("OK");
            } catch (PushoverClientException ex) {
                response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write(ex.getMessage());
            }
        }

        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            response.setContentType("text/plain;charset=UTF-8");
            response.getWriter().write("Method Not Allowed");
        }
    }
}


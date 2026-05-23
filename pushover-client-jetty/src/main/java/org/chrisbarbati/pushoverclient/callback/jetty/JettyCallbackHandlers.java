package org.chrisbarbati.pushoverclient.callback.jetty;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.callback.CallbackPayloadParser;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Factory for Jetty-native callback handlers that can be mounted into an existing Jetty handler tree.
 *
 * @since 0.4.0
 * @author Christian Barbati
 */
public final class JettyCallbackHandlers {
    private JettyCallbackHandlers() {
    }

    /**
     * Creates a Jetty handler for the supplied callback path.
     *
     * @param callbackPath request path that should accept callback POSTs
     * @param callbackHandler consumer invoked for each successfully parsed callback payload
     * @return Jetty handler suitable for host-managed lifecycle usage
     * @since 0.4.0
     */
    public static Handler create(String callbackPath, Consumer<ReceiptCallbackResponse> callbackHandler) {
        return new CallbackHandler(normalizePath(callbackPath), callbackHandler);
    }

    /**
     * Creates a Jetty handler for the supplied callback path that routes parsed callbacks back into the provided client.
     *
     * @param callbackPath request path that should accept callback POSTs
     * @param client client whose pending callback futures should be completed
     * @return Jetty handler suitable for host-managed lifecycle usage
     * @since 0.4.0
     */
    public static Handler create(String callbackPath, PushoverClient client) {
        Objects.requireNonNull(client, "client is required");
        return create(callbackPath, client::acceptCallbackReceipt);
    }

    /**
     * Creates a Jetty handler using the path portion of the supplied callback URI.
     *
     * @param callbackUri callback URI whose path should accept callback POSTs
     * @param callbackHandler consumer invoked for each successfully parsed callback payload
     * @return Jetty handler suitable for host-managed lifecycle usage
     * @since 0.4.0
     */
    public static Handler create(URI callbackUri, Consumer<ReceiptCallbackResponse> callbackHandler) {
        Objects.requireNonNull(callbackUri, "callbackUri is required");
        return create(callbackUri.getPath(), callbackHandler);
    }

    /**
     * Creates a Jetty handler using the path portion of the supplied callback URI and routes callbacks back into the provided client.
     *
     * @param callbackUri callback URI whose path should accept callback POSTs
     * @param client client whose pending callback futures should be completed
     * @return Jetty handler suitable for host-managed lifecycle usage
     * @since 0.4.0
     */
    public static Handler create(URI callbackUri, PushoverClient client) {
        Objects.requireNonNull(client, "client is required");
        return create(callbackUri, client::acceptCallbackReceipt);
    }

    static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private static final class CallbackHandler extends AbstractHandler {
        private final String callbackPath;
        private final Consumer<ReceiptCallbackResponse> callbackHandler;

        private CallbackHandler(String callbackPath, Consumer<ReceiptCallbackResponse> callbackHandler) {
            this.callbackPath = callbackPath;
            this.callbackHandler = Objects.requireNonNull(callbackHandler, "callbackHandler is required");
        }

        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException {
            if (!callbackPath.equals(target)) {
                return;
            }
            baseRequest.setHandled(true);

            if (!"POST".equalsIgnoreCase(request.getMethod())) {
                response.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                response.setContentType("text/plain;charset=UTF-8");
                response.getWriter().write("Method Not Allowed");
                return;
            }

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
    }
}




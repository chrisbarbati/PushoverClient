package org.chrisbarbati.pushoverclient.callback.jetty;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.callback.CallbackPayloadParser;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.io.Content;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Response;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.util.Callback;

import java.net.URI;
import java.nio.ByteBuffer;
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
    private static final String PLAIN_TEXT = "text/plain;charset=UTF-8";

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
        public boolean handle(Request request, Response response, Callback callback) {
            if (!callbackPath.equals(request.getHttpURI().getPath())) {
                return false;
            }

            try {
                if (!"POST".equalsIgnoreCase(request.getMethod())) {
                    write(response, callback, HttpStatus.METHOD_NOT_ALLOWED_405, "Method Not Allowed");
                    return true;
                }

                ByteBuffer body = Content.Source.asByteBuffer(request);
                ReceiptCallbackResponse callbackResponse = CallbackPayloadParser.parseFormEncodedBody(
                        StandardCharsets.UTF_8.decode(body).toString()
                );
                callbackHandler.accept(callbackResponse);
                write(response, callback, HttpStatus.OK_200, "OK");
                return true;
            } catch (PushoverClientException ex) {
                write(response, callback, HttpStatus.BAD_REQUEST_400, ex.getMessage());
                return true;
            } catch (Exception ex) {
                callback.failed(ex);
                return true;
            }
        }

        private static void write(Response response, Callback callback, int status, String body) {
            response.setStatus(status);
            response.getHeaders().put("Content-Type", PLAIN_TEXT);
            response.write(true, ByteBuffer.wrap(body.getBytes(StandardCharsets.UTF_8)), callback);
        }
    }
}




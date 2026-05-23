package org.chrisbarbati.pushoverclient.callback.servlet;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.callback.CallbackPayloadParser;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * Factory for generic servlet-based Pushover callback adapters in host-managed servlet containers.
 *
 * @since 0.4.0
 * @author Christian Barbati
 */
public final class CallbackServlets {
    private CallbackServlets() {
    }

    /**
     * Creates a servlet that accepts Pushover emergency callback POST bodies and forwards them to the provided consumer.
     *
     * @param callbackHandler consumer invoked for each successfully parsed callback payload
     * @return servlet suitable for mounting in an existing servlet container
     * @since 0.4.0
     * @author Christian Barbati
     */
    public static HttpServlet create(Consumer<ReceiptCallbackResponse> callbackHandler) {
        return new PushoverCallbackServlet(callbackHandler);
    }

    /**
     * Creates a servlet that routes parsed callbacks back into the provided client.
     *
     * @param client client whose pending callback futures should be completed
     * @return servlet suitable for mounting in an existing servlet container
     * @since 0.4.0
     * @author Christian Barbati
     */
    public static HttpServlet create(PushoverClient client) {
        Objects.requireNonNull(client, "client is required");
        return create(client::acceptCallbackReceipt);
    }

    private static final class PushoverCallbackServlet extends HttpServlet {
        private final Consumer<ReceiptCallbackResponse> callbackHandler;

        private PushoverCallbackServlet(Consumer<ReceiptCallbackResponse> callbackHandler) {
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


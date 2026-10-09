package org.chrisbarbati.pushoverclient;

import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.message.MessageResponse;

import java.io.IOException;

/**
 * Internal sender callback used by the async receipt lifecycle to submit
 * emergency messages without coupling to a concrete client type.
 *
 * @since 0.7.0
 */
@FunctionalInterface
interface MessageSender {
    /**
     * Sends one message request.
     *
     * @param request message request
     * @return parsed success response
     * @throws IOException when the HTTP call fails
     * @throws InterruptedException when the call is interrupted
     */
    MessageResponse send(MessageRequest request) throws IOException, InterruptedException;
}
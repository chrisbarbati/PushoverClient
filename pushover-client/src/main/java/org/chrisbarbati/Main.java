package org.chrisbarbati;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;

/**
 * Tiny command-line runner that demonstrates sending one Pushover message.
 *
 * @apiNote This sample exits quietly when credentials are not present.
 * @since 0.1.0
 * @author Christian Barbati
 */
public class Main {
    /**
     * Sends a message to Pushover using environment variables for credentials.
     *
     * @param args optional message text; defaults to a sample message when empty
     * @since 0.1.0
     */
    public static void main(String[] args) {
        // Keep credentials out of source by loading from environment.
        String token = System.getenv("PUSHOVER_APP_TOKEN");
        String user = System.getenv("PUSHOVER_USER_KEY");
        String encryptionKey = System.getenv("PUSHOVER_ENCRYPTION_KEY");

        if (token == null || token.isBlank() || user == null || user.isBlank()) {
            System.out.println("Set PUSHOVER_APP_TOKEN and PUSHOVER_USER_KEY to run this example.");
            return;
        }

        String messageBody = args.length > 0 ? String.join(" ", args) : "Hello from PushoverClient example";
        // Build a normal-priority message request with minimal required fields.
        MessageRequest request = MessageRequest.builder(user, messageBody)
                .title("PushoverClient")
                .priority(MessagePriority.NORMAL)
                .build();

        try {
            PushoverClient client = new PushoverClient(token, encryptionKey);
            var response = client.sendMessage(request);
            System.out.println("Message sent. Request ID: " + response.request());
        } catch (Exception ex) {
            System.err.println("Failed to send message: " + ex.getMessage());
        }
    }
}
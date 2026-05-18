# PushoverClient

Java client for the Pushover REST API.

## Current scope

- Send messages via `/1/messages.json`, including optional fields and parameters
- Support attachments as multipart binary (`attachment`) and Base64 (`attachment_base64`)
- Support end-to-end encryption for `message`, `title`, `url`, and `url_title`
- Query emergency receipts via `/1/receipts/{receipt}.json`
- Cancel emergency receipts directly or by tag
- Retrieve available sounds via `/1/sounds.json`
- Retrieve usage limits via `/1/apps/limits.json`

See [`CHANGELOG.md`](CHANGELOG.md) for version-by-version API coverage.

## Quick run

Set credentials, then run the small harness in `Main`.

```bash
export PUSHOVER_APP_TOKEN="your_app_token"
export PUSHOVER_USER_KEY="your_user_key"
export PUSHOVER_ENCRYPTION_KEY="64_char_hex_key_optional"
mvn -q -DskipTests exec:java -Dexec.mainClass=org.chrisbarbati.Main -Dexec.args="Hello from CLI"
```

If `PUSHOVER_APP_TOKEN` or `PUSHOVER_USER_KEY` is missing, the example exits without error.

## Tests

Run unit tests:

```bash
mvn -q test
```

Run integration tests (optional):

1. Fill `src/test/resources/pushover-integration.properties`, or set system properties/env vars.
2. For attachment integration tests, provide one or two real image files and set:
   - `pushover.multipartAttachment.path`
   - `pushover.multipartAttachment.contentType`
   - `pushover.base64Attachment.path`
   - `pushover.base64Attachment.contentType`
3. For optional field coverage tests, optionally set:
   - `pushover.device`
   - `pushover.sound`
4. For the combined emergency receipt lifecycle live test (opt-in), set:
   - `pushover.enableEmergencyTests=true`
   - `pushover.callbackUrl` (optional; use a real publicly reachable URL to include callback submission in the lifecycle flow)
   - `pushover.tags` (optional single base tag; defaults to `pushoverclient-it`)
   - `pushover.strictEmergencyTest=true` to fail build when API rejects callback/tags (default behavior is skip)
5. For intentional live failure testing, optionally set:
   - `pushover.invalidUserKey` (30 alphanumeric chars)
   - `pushover.invalidAppToken`
6. Run:

```bash
mvn -q verify
```

Integration tests log a warning and skip when credentials are missing, so builds do not fail for missing secrets.

Current integration coverage includes:

- `getSounds` and `getLimits`
- standard message send
- encrypted message send
- device/sound/timestamp optional fields
- multipart attachment send
- base64 attachment send
- emergency receipt lookup and cancellation lifecycle (opt-in)
- intentional live failures (invalid user and invalid token)

## Programmatic usage

```java
import java.io.IOException;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.message.MessageResponse;

public final class SendMessageExample {
    private SendMessageExample() {
    }

    public static MessageResponse send(String appToken, String userKey, String encryptionHexKey)
            throws IOException, InterruptedException {
        PushoverClient client = new PushoverClient(appToken, encryptionHexKey);

        MessageRequest request = MessageRequest.builder(userKey, "Backup completed")
                .title("DB Job")
                .priority(MessagePriority.NORMAL)
                .build();

        return client.sendMessage(request);
    }
}
```

Receipt follow-up for emergency notifications:

```java
import java.io.IOException;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.message.MessageResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCancellationResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;

public final class EmergencyReceiptExample {
    private EmergencyReceiptExample() {
    }

    public static ReceiptStatusResponse sendAndManageReceipt(
            String appToken,
            String userKey,
            String encryptionHexKey
    ) throws IOException, InterruptedException {
        PushoverClient client = new PushoverClient(appToken, encryptionHexKey);

        MessageResponse response = client.sendMessage(MessageRequest.builder(userKey, "Database unavailable")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .callback("https://example.com/pushover/callback")
                .tags("ops-db")
                .build());

        ReceiptStatusResponse receipt = client.getReceipt(response.receipt());
        ReceiptCancellationResponse cancelByReceipt = client.cancelReceipt(response.receipt());
        ReceiptCancellationResponse cancelByTag = client.cancelByTag("ops-db");

        System.out.println(cancelByReceipt.request());
        System.out.println(cancelByTag.request());
        return receipt;
    }
}
```


# PushoverClient

Java client for the Pushover REST API.

## Current scope

- Send messages via `/1/messages.json`, including optional fields and parameters
- Support attachments as multipart binary (`attachment`) and Base64 (`attachment_base64`)
- Support end-to-end encryption for `message`, `title`, `url`, and `url_title`
- Retrieve available sounds via `/1/sounds.json`
- Retrieve usage limits via `/1/apps/limits.json`

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
4. For emergency callback/tags live test (opt-in), set:
   - `pushover.enableEmergencyTests=true`
   - `pushover.callbackUrl` (publicly reachable URL, including scheme like `https://...`)
   - `pushover.tags` (optional; defaults to `pushoverclient-it`)
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
- emergency callback/tags (opt-in)
- intentional live failures (invalid user and invalid token)

## Programmatic usage

```java
PushoverClient client = new PushoverClient(appToken, encryptionHexKey);

MessageRequest request = MessageRequest.builder(userKey, "Backup completed")
        .title("DB Job")
        .priority(MessagePriority.NORMAL)
        .build();

MessageResponse response = client.sendMessage(request);
```


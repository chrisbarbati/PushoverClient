# PushoverClient

A minimal, low-dependency Java client for the Pushover REST API.

## Overview

PushoverClient sends Pushover notifications from scripts, cron jobs, CI pipelines,
and monitoring/alerting tools without dragging in a heavyweight HTTP client or
framework. The core jar depends only on `jackson-databind` and `slf4j-api`, uses the
JDK's `java.net.http.HttpClient`, and exposes immutable request/response models behind
a single `PushoverClient` facade.

Beyond basic messages it covers the higher-value Pushover workflows:

- **Emergency alerts with receipts** — send a priority-2 message, then poll for
  acknowledgement or receive a callback when an operator acknowledges it.
- **Key/group validation** before storing or sending to a target user.
- **Attachments** (multipart and Base64) and **end-to-end encrypted** messages.

### Design goals

- **Low footprint** - two runtime dependencies in the core module.
- **Modern Java 17** - `java.net.http`, records, and fluent builders, no framework.
- **One facade** - all operations live on the single `PushoverClient` class.
- **Fail fast** - documented Pushover constraints are validated before any HTTP call.
- **Optional web-container modules** - servlet/Jetty/Tomcat callback support ships in
  separate artifacts so the core stays lightweight.

## Modules

- `pushover-client` - core client, async orchestration, shared callback parsing, and the `CallbackServer` abstraction
- `pushover-client-servlet` - optional generic servlet callback adapter for host-managed servlet containers
- `pushover-client-jetty` - optional Jetty-native callback `Handler` support plus the Jetty-backed `CallbackServer` implementation for self-managed lifecycle
- `pushover-client-tomcat` - optional Tomcat-backed `CallbackServer` implementation for self-managed callback lifecycle

## Current scope

- Send messages via `/1/messages.json`, including optional fields and parameters
- Support attachments as multipart binary (`attachment`) and Base64 (`attachment_base64`)
- Support end-to-end encryption for `message`, `title`, `url`, and `url_title`
- Validate user and group keys via `/1/users/validate.json`, with optional device-specific validation
- Query emergency receipts via `/1/receipts/{receipt}.json`
- Cancel emergency receipts directly or by tag
- Retrieve available sounds via `/1/sounds.json`
- Retrieve usage limits via `/1/apps/limits.json`

See [`CHANGELOG.md`](CHANGELOG.md) for version-by-version API coverage.

## Releases

- Shared artifact versioning uses the root Maven `revision` property in [`pom.xml`](pom.xml). That value becomes the version for every module in the reactor.
- Version numbers follow a semantic `major.minor.patch` scheme:
  - `major`: incompatible public API changes or breaking behavior changes, or other major release milestones
  - `minor`: backward-compatible features, endpoint coverage additions, or meaningful new capabilities
  - `patch`: backward-compatible fixes, polish, or small behavioral corrections that are worth publishing
- Bump `revision` when you intend to publish a new stable artifact version, typically for user-facing API changes, behavior changes, dependency/runtime changes worth releasing, or other intentional release cuts.
- Do **not** bump `revision` for test-only changes, local-only workflow notes, or other repository maintenance that does not change the published artifacts' intended semantic version.
- Successful pushes to `main` publish a stable GitHub release tagged as `v<revision>`, using the root Maven `revision` from [`pom.xml`](pom.xml).
- If `main` is rebuilt without changing `revision`, the existing versioned release is refreshed in place and its assets are overwritten.
- Successful pushes to `main` also update a separate moving prerelease feed tagged `rolling-main-prerelease` with the latest tested reactor JARs.

In practice, this gives the repository two release tracks:

- Stable, version-labeled releases: `v<revision>`
- Moving prerelease feed: `rolling-main-prerelease`

If you only add or improve tests, prefer keeping `revision` unchanged unless you are intentionally cutting a new stable release for some other reason.

## Quick run

Set your credentials, then use the inline `SendMessageExample` shown in [Programmatic usage](#programmatic-usage) below to send a first notification:

```bash
export PUSHOVER_APP_TOKEN="your_app_token"
export PUSHOVER_USER_KEY="your_user_key"
export PUSHOVER_ENCRYPTION_KEY="64_char_hex_key_optional"
```

The example wires `send(...)` with these values and sends a normal-priority notification.

## Tests

Run unit tests:

```bash
./mvnw -q test
```

Run integration tests (optional):

1. Copy `pushover-client/src/test/resources/pushover-integration.properties.example` to `pushover-integration.properties` in the same directory and fill in real values, or set system properties/env vars instead. The real properties file is gitignored, so credentials never enter the repository.
2. For attachment integration tests, provide one or two real image files and set:
   - `pushover.multipartAttachment.path`
   - `pushover.multipartAttachment.contentType`
   - `pushover.base64Attachment.path`
   - `pushover.base64Attachment.contentType`
3. For optional field coverage tests, optionally set:
   - `pushover.device`
   - `pushover.sound`
   - `pushover.groupKey` (optional, enables live group validation coverage)
4. For the combined emergency receipt lifecycle live test (opt-in), set:
   - `pushover.enableEmergencyTests=true`
   - `pushover.callbackUrl` (optional; use a real publicly reachable URL to include callback submission in the lifecycle flow)
   - `pushover.tags` (optional single base tag; defaults to `pushoverclient-it`)
   - `pushover.strictEmergencyTest=true` to fail build when API rejects callback/tags (default behavior is skip)
5. For async callback acknowledgement integration tests in `pushover-client-servlet`, `pushover-client-jetty`, and `pushover-client-tomcat` (opt-in), set:
   - `pushover.enableAsyncEmergencyTests=true`
   - `pushover.asyncCallbackBindUri` (local bind URI for the embedded test server)
   - `pushover.asyncCallbackUrl` (public callback URL that forwards to the bind URI above)
6. For intentional live failure testing, optionally set:
   - `pushover.invalidUserKey` (30 alphanumeric chars)
   - `pushover.invalidAppToken`
7. Run:

```bash
./mvnw -q verify
```

Integration tests log a warning and skip when credentials are missing, so builds do not fail for missing secrets.

Current integration coverage includes:

- `validateUserOrGroup` for configured user keys, plus optional configured group keys
- `getSounds` and `getLimits`
- standard message send
- encrypted message send
- device/sound/timestamp optional fields
- multipart attachment send
- base64 attachment send
- emergency receipt lookup and cancellation lifecycle (opt-in)
- async servlet, Jetty, and Tomcat callback acknowledgement flows (opt-in)
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

User/group validation before storing or sending to a target key:

```java
import java.io.IOException;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.model.validation.UserGroupValidationResponse;

public final class ValidateUserExample {
    private ValidateUserExample() {
    }

    public static UserGroupValidationResponse validate(
            String appToken,
            String userOrGroupKey,
            String optionalDevice
    ) throws IOException, InterruptedException {
        PushoverClient client = new PushoverClient(appToken);

        if (optionalDevice == null || optionalDevice.isBlank()) {
            return client.validateUserOrGroup(userOrGroupKey);
        }
        return client.validateUserOrGroup(userOrGroupKey, optionalDevice);
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

Async polling for emergency acknowledgements:

```java
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;

public final class EmergencyPollingAsyncExample {
    private EmergencyPollingAsyncExample() {
    }

    public static CompletableFuture<ReceiptStatusResponse> sendAndAwaitAcknowledgement(
            String appToken,
            String userKey
    ) {
        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .pollInterval(Duration.ofSeconds(5))
                .pollFutureExpiration(Duration.ofMinutes(10))
                .build();

        PushoverClient client = new PushoverClient(
                appToken,
                null,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                null
        );

        ExecutorService executorService = Executors.newCachedThreadPool();
        MessageRequest request = MessageRequest.builder(userKey, "Database unavailable")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();

        return client.sendEmergencyMessageAndAwaitAcknowledgement(
                request,
                executorService,
                Duration.ofMinutes(6)
        );
    }
}
```

Async callback flow uses the same core client behavior in every lifecycle model:

- `sendEmergencyMessageAndAwaitCallback(...)` still submits the emergency message and completes a future when a callback payload is delivered back into the client.
- In host-managed lifecycle, you mount a callback endpoint into your existing web container/server and configure `request.callback(...)` or `AsyncReceiptProperties.defaultCallbackUrl(...)`.
- In self-managed lifecycle, provide a `CallbackServer`; the client starts it on demand and can derive the callback URL from `callbackServer.callbackUri()`.

Host-managed callback lifecycle with the optional `pushover-client-servlet` module:

```java
import jakarta.servlet.http.HttpServlet;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.servlet.CallbackServlets;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

public final class ServletManagedCallbackExample {
    private ServletManagedCallbackExample() {
    }

    public static CompletableFuture<ReceiptCallbackResponse> sendAndAwaitCallback(
            String appToken,
            String userKey
    ) {
        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .defaultCallbackUrl("https://example.com/pushover/callback")
                .callbackFutureExpiration(Duration.ofMinutes(10))
                .warnOnConflictingCallback(true)
                .build();

        PushoverClient client = new PushoverClient(
                appToken,
                null,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                null
        );

        HttpServlet callbackServlet = CallbackServlets.create(client);
        // Mount callbackServlet at /pushover/callback in your existing servlet container.

        ExecutorService executorService = Executors.newCachedThreadPool();
        MessageRequest request = MessageRequest.builder(userKey, "Database unavailable")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();

        return client.sendEmergencyMessageAndAwaitCallback(
                request,
                executorService,
                Duration.ofMinutes(6)
        );
    }
}
```

Jetty host-managed callback lifecycle for an existing Jetty server:

```java
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.jetty.JettyCallbackHandlers;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

public final class ExistingJettyCallbackExample {
    private ExistingJettyCallbackExample() {
    }

    public static CompletableFuture<ReceiptCallbackResponse> sendAndAwaitCallback(
            String appToken,
            String userKey,
            HandlerCollection existingHandlers
    ) {
        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .defaultCallbackUrl("https://example.com/pushover/callback")
                .callbackFutureExpiration(Duration.ofMinutes(10))
                .build();

        PushoverClient client = new PushoverClient(
                appToken,
                null,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                null
        );

        existingHandlers.addHandler(JettyCallbackHandlers.create("/pushover/callback", client));

        ExecutorService executorService = Executors.newCachedThreadPool();
        MessageRequest request = MessageRequest.builder(userKey, "Database unavailable")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();

        return client.sendEmergencyMessageAndAwaitCallback(request, executorService);
    }
}
```

Jetty self-managed callback lifecycle:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.jetty.JettyCallbackServer;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

public final class SelfManagedJettyCallbackExample {
    private SelfManagedJettyCallbackExample() {
    }

    public static CompletableFuture<ReceiptCallbackResponse> sendAndAwaitCallback(
            String appToken,
            String userKey
    ) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        JettyCallbackServer callbackServer = new JettyCallbackServer(
                URI.create("http://127.0.0.1:8080/pushover/callback"),
                executorService
        );

        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .callbackFutureExpiration(Duration.ofMinutes(10))
                .warnOnConflictingCallback(true)
                .build();

        PushoverClient client = new PushoverClient(
                appToken,
                null,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                callbackServer
        );

        MessageRequest request = MessageRequest.builder(userKey, "Database unavailable")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();

        return client.sendEmergencyMessageAndAwaitCallback(
                request,
                executorService,
                Duration.ofMinutes(6)
        );
    }
}
```

Tomcat self-managed callback lifecycle:

```java
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.chrisbarbati.pushoverclient.PushoverClient;
import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.tomcat.TomcatCallbackServer;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

public final class SelfManagedTomcatCallbackExample {
    private SelfManagedTomcatCallbackExample() {
    }

    public static CompletableFuture<ReceiptCallbackResponse> sendAndAwaitCallback(
            String appToken,
            String userKey
    ) {
        ExecutorService executorService = Executors.newCachedThreadPool();
        TomcatCallbackServer callbackServer = new TomcatCallbackServer(
                URI.create("http://127.0.0.1:8080/pushover/callback"),
                executorService
        );

        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .callbackFutureExpiration(Duration.ofMinutes(10))
                .warnOnConflictingCallback(true)
                .build();

        PushoverClient client = new PushoverClient(
                appToken,
                null,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                callbackServer
        );

        MessageRequest request = MessageRequest.builder(userKey, "Database unavailable")
                .priority(MessagePriority.EMERGENCY)
                .retry(30)
                .expire(300)
                .build();

        return client.sendEmergencyMessageAndAwaitCallback(
                request,
                executorService,
                Duration.ofMinutes(6)
        );
    }
}
```

Notes:

- Choose host-managed lifecycle when your application already runs a servlet container or Jetty server and you want to mount the callback endpoint into that existing runtime.
- Choose self-managed lifecycle when you want the library to own the callback listener lifecycle via `CallbackServer`.
- The optional `pushover-client-servlet` artifact provides `CallbackServlets` for generic servlet containers.
- The optional `pushover-client-jetty` artifact adds `JettyCallbackHandlers` for existing Jetty servers and `JettyCallbackServer` for self-managed Jetty lifecycle.
- Polling interval, default callback URL, conflict-warning behavior, and default future expirations are configured via `AsyncReceiptProperties`.
- Both async APIs also provide `Duration` overloads for per-call expiration overrides.
- `sendEmergencyMessageAndAwaitCallback(...)` uses the request callback when present. If it conflicts with the configured default callback URL, the request value wins and a WARN log is emitted unless `warnOnConflictingCallback(false)` is configured.
- `JettyCallbackServer` requires a caller-supplied `ExecutorService` backed by `ThreadPoolExecutor`, so the library does not hide its callback-server thread usage.

### Async resource usage

- The polling flow (`sendEmergencyMessageAndAwaitAcknowledgement`) intentionally occupies **one executor thread per pending receipt** while it blocks in a sleep-poll loop until the receipt is acknowledged, expires, or the future expires (default 10 minutes).
- The callback flow (`sendEmergencyMessageAndAwaitCallback`) is event-driven for the callback itself, but each pending future also runs a lightweight monitor thread that wakes periodically until the callback arrives or the future expires (default 10 minutes).
- Synchronous methods (`sendMessage`, `getReceipt`, `cancelReceipt`, and friends) only block the calling thread for the duration of the HTTP call itself.
- Size your caller-supplied `ExecutorService` to comfortably cover the number of concurrent pending receipts, since each one holds a thread for up to the future expiration. A cached thread pool (as in the async examples above) is the simplest fit.
- **Virtual threads (Java 21+)**: the polling and host-managed callback flows accept any `ExecutorService`, so on Java 21+ you can pass `Executors.newVirtualThreadPerTaskExecutor()`; the blocking sleep-poll loops park a carrier thread instead of occupying a platform thread, which is exactly the workload virtual threads make cheap. The library itself remains compiled for Java 17. Two exceptions: `JettyCallbackServer` requires an `ExecutorService` backed by `ThreadPoolExecutor`, so virtual-thread executors cannot back the Jetty self-managed callback server; and for `TomcatCallbackServer`, prefer a platform-thread pool since handing a virtual-thread executor to a web container is atypical.

### Security notes

- All requests default to HTTPS (`https://api.pushover.net`) via the base URL.
- POST endpoints send the app token in the request body as a form field, per the Pushover API.
- For the GET endpoints (`getSounds`, `getLimits`, `getReceipt`), the Pushover API takes the token as a `token=` query parameter. Query strings can appear in server access logs, proxy/gateway logs, and browser or HTTP-client history, so keep the base URL on HTTPS and be cautious when routing traffic through shared network appliances.
- The app token (and the optional end-to-end encryption key) are held in memory as `String` fields for the lifetime of the `PushoverClient` instance; supply them from environment variables or a secrets store rather than hard-coding.


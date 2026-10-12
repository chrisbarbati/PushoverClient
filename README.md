# PushoverClient

[![CI](https://github.com/chrisbarbati/PushoverClient/actions/workflows/ci.yml/badge.svg)](https://github.com/chrisbarbati/PushoverClient/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

A low-dependency, full-featured Java client for the Pushover REST API, with async acknowledgement support.

## Overview

PushoverClient is designed to let you send Pushover notifications from your Java applications without dragging in a heavyweight
HTTP client or framework. The core jar depends only on `jackson-databind` and `slf4j-api`, uses the
JDK's `java.net.http.HttpClient`, and exposes immutable request/response models behind
a single `PushoverClient` facade.

The client is designed to cover the full application-facing Pushover API, not just the send path:

- **Messages** - every documented field, including title, url/url_title, devices, sounds, timestamps, HTML/monospace rendering, priority, ttl, and emergency `retry`/`expire`/`callback`/`tags`.
- **Attachments** - both encodings: multipart binary (`attachment`) and Base64 (`attachment_base64`).
- **End-to-end encryption** - per Pushover's scheme (gzip + AES-256-CBC + HMAC) for `message`, `title`, `url`, and `url_title`.
- **Emergency receipts** - query receipt status via `/1/receipts/{receipt}.json`, and cancel by receipt or by tag.
- **Async acknowledgement** - send an emergency message and complete a future when it is acknowledged, by polling the receipt or fielding a callback from an optional embedded (`JettyCallbackServer`/`TomcatCallbackServer`) or host-managed (servlet / `JettyCallbackHandlers`) callback server.
- **Accounts** - available sounds (`/1/sounds.json`), usage limits (`/1/apps/limits.json`), and user/group key validation (`/1/users/validate.json`).
- **Glances** - update widget data (Apple Watch) via `/1/glances.json`. Please note that this feature has not been integration tested at this time, since I don't have an Apple Watch.

Out of current scope:

 - **Groups administration**
 - **Subscription**

This client targets server-side applications that send Pushover notifications, and administration is deliberately omitted at this time.

See [`CHANGELOG.md`](CHANGELOG.md) for version-by-version API coverage.

### Design goals

I designed this client because I wanted the following:

- **Full-featured** - intended to cover the entire documented Pushover API surface (excluding some administration functions, although this may change in the future).
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

## Quick run

Add the core dependency to your build:

```xml
<dependency>
    <groupId>dev.chrisbarbati</groupId>
    <artifactId>pushover-client</artifactId>
    <version>0.9.0</version>
</dependency>
```

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

Optional live integration tests exercise the real Pushover API. They require credentials in
`pushover-client/src/test/resources/pushover-integration.properties` (copy the
[`pushover-integration.properties.example`](pushover-client/src/test/resources/pushover-integration.properties.example)
template) or as system properties, and skip with a warning when credentials are missing:

```bash
./mvnw -q verify
```
- intentional live failures (invalid user and invalid token)

## Programmatic usage

```java
import java.io.IOException;

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.message.MessageResponse;

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

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.model.validation.UserGroupValidationResponse;

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

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.message.MessageResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCancellationResponse;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;

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

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;

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

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.callback.servlet.CallbackServlets;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

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
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.callback.jetty.JettyCallbackHandlers;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

public final class ExistingJettyCallbackExample {
    private ExistingJettyCallbackExample() {
    }

    public static CompletableFuture<ReceiptCallbackResponse> sendAndAwaitCallback(
            String appToken,
            String userKey,
            Server server,
            Handler existingHandler
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

        Handler callbackHandler = JettyCallbackHandlers.create("/pushover/callback", client);

        // Mount the callback handler alongside your existing handlers in a Jetty 12 Handler.Sequence.
        server.setHandler(new Handler.Sequence(callbackHandler, existingHandler));

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

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.callback.jetty.JettyCallbackServer;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

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

import dev.chrisbarbati.pushoverclient.PushoverClient;
import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.callback.tomcat.TomcatCallbackServer;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;

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
- Synchronous methods (`sendMessage`, `getReceipt`, `cancelReceipt`, and similar) only block the calling thread for the duration of the HTTP call itself.
- Size your caller-supplied `ExecutorService` to comfortably cover the number of concurrent pending receipts, since each one holds a thread for up to the future expiration. A cached thread pool (as in the async examples above) is the simplest fit.
- **Virtual threads (Java 21+)**: the polling and host-managed callback flows accept any `ExecutorService`, so on Java 21+ you can pass `Executors.newVirtualThreadPerTaskExecutor()`; the blocking sleep-poll loops park a carrier thread instead of occupying a platform thread. The library itself remains compiled for Java 17. Two exceptions: `JettyCallbackServer` requires an `ExecutorService` backed by `ThreadPoolExecutor`, so virtual-thread executors cannot back the Jetty self-managed callback server; and for `TomcatCallbackServer`, prefer a platform-thread pool since handing a virtual-thread executor to a web container is atypical.

### Security notes

- All requests default to HTTPS (`https://api.pushover.net`) via the base URL.
- POST endpoints send the app token in the request body as a form field, per the Pushover API.
- For the GET endpoints (`getSounds`, `getLimits`, `getReceipt`), the Pushover API takes the token as a `token=` query parameter. Query strings can appear in server access logs, proxy/gateway logs, and browser or HTTP-client history, so keep the base URL on HTTPS and be cautious when routing traffic through shared network appliances.
- The app token (and the optional end-to-end encryption key) are held in memory as `String` fields for the lifetime of the `PushoverClient` instance; supply them from environment variables or a secrets store rather than hard-coding.

## License

[MIT](LICENSE) © [Christian Barbati](https://github.com/chrisbarbati)


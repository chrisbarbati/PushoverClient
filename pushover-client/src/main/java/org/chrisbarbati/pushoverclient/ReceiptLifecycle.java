package org.chrisbarbati.pushoverclient;

import org.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import org.chrisbarbati.pushoverclient.callback.CallbackServer;
import org.chrisbarbati.pushoverclient.exception.PushoverClientException;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.chrisbarbati.pushoverclient.model.message.MessageResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.chrisbarbati.pushoverclient.model.receipt.ReceiptStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

/**
 * Internal lifecycle for asynchronous emergency receipt workflows.
 *
 * <p>Owns the pending polling/callback future registries and the blocking
 * poll loops and expiration monitors that drive them. This class is
 * package-private: all async entry points remain on {@link PushoverClient}.</p>
 *
 * @since 0.7.0
 */
final class ReceiptLifecycle {

    private static final Logger LOGGER = LoggerFactory.getLogger(ReceiptLifecycle.class);

    private final HttpTransport transport;
    private final RequestValidator validator;
    private final AsyncReceiptProperties asyncReceiptProperties;
    private final CallbackServer callbackServer;
    private final ConcurrentMap<String, PendingReceiptFuture<ReceiptStatusResponse>> pendingPollFutures = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, PendingReceiptFuture<ReceiptCallbackResponse>> pendingCallbackFutures = new ConcurrentHashMap<>();

    /**
     * Creates a receipt lifecycle for one client's transport and async configuration.
     *
     * @param transport HTTP transport used for receipt polling
     * @param validator request validator used for identifier checks
     * @param asyncReceiptProperties async polling/callback defaults
     * @param callbackServer optional callback server implementation
     */
    ReceiptLifecycle(
            HttpTransport transport,
            RequestValidator validator,
            AsyncReceiptProperties asyncReceiptProperties,
            CallbackServer callbackServer
    ) {
        this.transport = transport;
        this.validator = validator;
        this.asyncReceiptProperties = asyncReceiptProperties;
        this.callbackServer = callbackServer;
    }

    /**
     * Polls the receipts endpoint until acknowledgement after sending an emergency message.
     *
     * @param request emergency message request (already validated)
     * @param executor executor used for async work
     * @param futureExpiration expiration for the returned future
     * @param sender callback that sends the emergency message
     * @return future completing with the acknowledged receipt payload
     */
    CompletableFuture<ReceiptStatusResponse> awaitPollAcknowledgement(
            MessageRequest request,
            ExecutorService executor,
            Duration futureExpiration,
            MessageSender sender
    ) {
        CompletableFuture<ReceiptStatusResponse> future = new CompletableFuture<>();
        submitAsyncTask(executor, future, () -> {
            MessageResponse response = sender.send(request);
            String receiptId = requireAsyncReceipt(response);
            registerPendingFuture(pendingPollFutures, receiptId, future, parseTags(request.tags()));
            pollReceiptUntilTerminal(receiptId, future, futureExpiration);
        });
        return future;
    }

    /**
     * Completes the returned future when a configured callback endpoint delivers Pushover's POST back.
     *
     * @param request emergency message request (already validated)
     * @param executor executor used for async work
     * @param futureExpiration expiration for the returned future
     * @param sender callback that sends the emergency message
     * @return future completing with the callback payload
     */
    CompletableFuture<ReceiptCallbackResponse> awaitCallbackAcknowledgement(
            MessageRequest request,
            ExecutorService executor,
            Duration futureExpiration,
            MessageSender sender
    ) {
        CompletableFuture<ReceiptCallbackResponse> future = new CompletableFuture<>();
        submitAsyncTask(executor, future, () -> {
            if (callbackServer != null) {
                ensureCallbackServerStarted();
            }
            MessageRequest callbackRequest = withResolvedCallback(request);
            MessageResponse response = sender.send(callbackRequest);
            String receiptId = requireAsyncReceipt(response);
            registerPendingFuture(pendingCallbackFutures, receiptId, future, parseTags(callbackRequest.tags()));
            monitorFutureExpiration(receiptId, future, futureExpiration, executor, pendingCallbackFutures, "callback");
        });
        return future;
    }

    /**
     * Accepts a parsed callback payload and completes any matching callback-based futures.
     *
     * @param callbackResponse parsed callback payload delivered by a host-managed adapter
     */
    void handleCallbackReceipt(ReceiptCallbackResponse callbackResponse) {
        String receiptId = validator.requireReceiptId(callbackResponse.receipt());
        PendingReceiptFuture<ReceiptCallbackResponse> pending = pendingCallbackFutures.get(receiptId);
        if (pending == null) {
            LOGGER.debug("Ignoring callback for receipt {} because no pending callback future is registered", receiptId);
            return;
        }
        pending.future().complete(callbackResponse);
    }

    /**
     * Cancels all pending futures registered for a receipt id.
     *
     * @param receiptId emergency receipt id
     */
    void cancelReceipt(String receiptId) {
        cancelPendingFuture(pendingPollFutures.remove(receiptId));
        cancelPendingFuture(pendingCallbackFutures.remove(receiptId));
    }

    /**
     * Cancels all pending futures registered for a tag.
     *
     * @param tag message tag
     */
    void cancelByTag(String tag) {
        cancelPendingReceiptsByTag(pendingPollFutures, tag);
        cancelPendingReceiptsByTag(pendingCallbackFutures, tag);
    }

    /**
     * Resolves a future expiration from an override or the configured default.
     *
     * @param override per-call override, or null to use the default
     * @param configuredDefault configured default
     * @param propertyName property name for error reporting
     * @return resolved positive expiration duration
     * @throws PushoverClientException when the resolved duration is not positive
     */
    static Duration resolveFutureExpiration(Duration override, Duration configuredDefault, String propertyName) {
        Duration resolved = override == null ? configuredDefault : override;
        if (resolved == null || resolved.isZero() || resolved.isNegative()) {
            throw new PushoverClientException(propertyName + " must be a positive duration");
        }
        return resolved;
    }

    private void submitAsyncTask(ExecutorService executor, CompletableFuture<?> future, InterruptibleTask task) {
        try {
            executor.submit(() -> {
                try {
                    task.run();
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    future.completeExceptionally(ex);
                } catch (CancellationException ex) {
                    future.cancel(false);
                } catch (Exception ex) {
                    future.completeExceptionally(ex);
                }
            });
        } catch (RejectedExecutionException ex) {
            future.completeExceptionally(ex);
        }
    }

    private void pollReceiptUntilTerminal(
            String receiptId,
            CompletableFuture<ReceiptStatusResponse> future,
            Duration futureExpiration
    ) throws IOException, InterruptedException {
        long startedAt = System.nanoTime();
        while (!future.isDone()) {
            if (hasExpired(startedAt, futureExpiration)) {
                future.completeExceptionally(new TimeoutException("Polling future expired before acknowledgement for receipt " + receiptId));
                return;
            }

            ReceiptStatusResponse response = transport.getJson(
                    PushoverEndpoints.RECEIPTS + "/" + PushoverStrings.encodePathSegment(receiptId) + ".json",
                    ReceiptStatusResponse.class
            );
            if (response.acknowledged() == 1) {
                future.complete(response);
                return;
            }
            if (response.expired() == 1) {
                future.completeExceptionally(new PushoverClientException("Receipt expired before acknowledgement for receipt " + receiptId));
                return;
            }

            sleep(asyncReceiptProperties.pollInterval());
        }
    }

    private void monitorFutureExpiration(
            String receiptId,
            CompletableFuture<?> future,
            Duration futureExpiration,
            ExecutorService executor,
            ConcurrentMap<String, ? extends PendingReceiptFuture<?>> pendingFutures,
            String futureType
    ) {
        submitAsyncTask(executor, future, () -> {
            long startedAt = System.nanoTime();
            while (!future.isDone()) {
                if (hasExpired(startedAt, futureExpiration)) {
                    pendingFutures.remove(receiptId);
                    future.completeExceptionally(new TimeoutException(
                            "The " + futureType + " future expired before completion for receipt " + receiptId
                    ));
                    return;
                }
                sleep(asyncReceiptProperties.pollInterval());
            }
        });
    }

    private synchronized void ensureCallbackServerStarted() throws Exception {
        if (callbackServer == null) {
            throw new PushoverClientException("A callbackServer is required for callback-based asynchronous receipt handling");
        }
        if (!callbackServer.isRunning()) {
            callbackServer.start(this::handleCallbackReceipt);
        }
    }

    private MessageRequest withResolvedCallback(MessageRequest request) {
        String defaultCallback = asyncReceiptProperties.defaultCallbackUrl();
        String providedCallback = PushoverStrings.normalizeBlank(request.callback());
        String effectiveCallback;

        if (!PushoverStrings.isBlank(providedCallback)) {
            if (!PushoverStrings.isBlank(defaultCallback)
                    && !providedCallback.equals(defaultCallback)
                    && asyncReceiptProperties.warnOnConflictingCallback()) {
                LOGGER.warn(
                        "Provided callback {} conflicts with default callback {}. Using the provided callback. This warning log can be disabled via property {}",
                        providedCallback,
                        defaultCallback,
                        AsyncReceiptProperties.WARN_ON_CONFLICTING_CALLBACK_PROPERTY
                );
            }
            effectiveCallback = providedCallback;
        } else if (!PushoverStrings.isBlank(defaultCallback)) {
            effectiveCallback = defaultCallback;
        } else if (callbackServer != null && callbackServer.callbackUri() != null) {
            effectiveCallback = callbackServer.callbackUri().toString();
        } else {
            throw new PushoverClientException(
                    "A callback URL is required for callback-based asynchronous receipt handling; configure "
                            + AsyncReceiptProperties.DEFAULT_CALLBACK_URL_PROPERTY
                            + ", provide request.callback(), or configure a callbackServer"
            );
        }

        if (effectiveCallback.equals(PushoverStrings.normalizeBlank(request.callback()))) {
            return request;
        }
        return request.toBuilder().callback(effectiveCallback).build();
    }

    private String requireAsyncReceipt(MessageResponse response) {
        if (response == null || PushoverStrings.isBlank(response.receipt())) {
            throw new PushoverClientException("Emergency notification response did not include a receipt");
        }
        return validator.requireReceiptId(response.receipt());
    }

    private static <T> void registerPendingFuture(
            ConcurrentMap<String, PendingReceiptFuture<T>> pendingFutures,
            String receiptId,
            CompletableFuture<T> future,
            Set<String> tags
    ) {
        PendingReceiptFuture<T> pendingFuture = new PendingReceiptFuture<>(future, tags);
        future.whenComplete((ignored, throwable) -> pendingFutures.remove(receiptId, pendingFuture));
        pendingFutures.put(receiptId, pendingFuture);
    }

    private static void cancelPendingFuture(PendingReceiptFuture<?> pendingFuture) {
        if (pendingFuture != null) {
            pendingFuture.future().cancel(false);
        }
    }

    private static void cancelPendingReceiptsByTag(
            ConcurrentMap<String, ? extends PendingReceiptFuture<?>> pendingFutures,
            String tag
    ) {
        for (var entry : pendingFutures.entrySet()) {
            if (entry.getValue().tags().contains(tag) && pendingFutures.remove(entry.getKey(), entry.getValue())) {
                entry.getValue().future().cancel(false);
            }
        }
    }

    private static Set<String> parseTags(String tags) {
        if (PushoverStrings.isBlank(tags)) {
            return Collections.emptySet();
        }
        Set<String> values = new HashSet<>();
        for (String tag : tags.split(",")) {
            String normalized = PushoverStrings.normalizeBlank(tag);
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return Collections.unmodifiableSet(values);
    }

    private static boolean hasExpired(long startedAt, Duration expiration) {
        return System.nanoTime() - startedAt >= expiration.toNanos();
    }

    private static void sleep(Duration duration) throws InterruptedException {
        long millis = duration.toMillis();
        int nanos = duration.toNanosPart() % 1_000_000;
        Thread.sleep(millis, nanos);
    }

    @FunctionalInterface
    private interface InterruptibleTask {
        void run() throws Exception;
    }

    private record PendingReceiptFuture<T>(CompletableFuture<T> future, Set<String> tags) {
    }
}
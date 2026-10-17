package dev.chrisbarbati.pushoverclient;

import jakarta.servlet.http.HttpServlet;
import dev.chrisbarbati.pushoverclient.async.AsyncReceiptProperties;
import dev.chrisbarbati.pushoverclient.callback.servlet.CallbackServlets;
import dev.chrisbarbati.pushoverclient.model.message.MessagePriority;
import dev.chrisbarbati.pushoverclient.model.message.MessageRequest;
import dev.chrisbarbati.pushoverclient.model.receipt.ReceiptCallbackResponse;
import org.apache.catalina.Context;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.startup.Tomcat;
import org.apache.coyote.AbstractProtocol;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Comparator;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Live integration tests for the optional servlet callback adapter hosted on Tomcat.
 *
 * @apiNote These tests are opt-in and intended for interactive runs because they require the user to acknowledge the delivered notification.
 * @since 0.4.0
 * @author Christian Barbati
 */
class PushoverClientServletTomcatAsyncIT {
    private static final Logger LOGGER = LoggerFactory.getLogger(PushoverClientServletTomcatAsyncIT.class);
    private static final Properties CONFIG = loadConfig();
    private static final Duration USER_ACK_TIMEOUT = Duration.ofMinutes(1);
    private static final String CALLBACK_SERVLET_NAME = "pushoverCallbackServlet";

    @Test
    void live_asyncTomcatServletCallbackAcknowledgement_whenEnabled() throws Exception {
        requireAsyncEmergencyTestsEnabled();

        String token = required("pushover.appToken");
        String user = required("pushover.userKey");
        String encryptionKey = optional("pushover.encryptionKey");
        URI callbackBindUri = requiredUri("pushover.asyncCallbackBindUri");
        URI callbackPublicUri = requiredUri("pushover.asyncCallbackUrl");
        validateServletCallbackReachability(callbackBindUri, callbackPublicUri);
        String tag = uniqueTag();

        AsyncReceiptProperties asyncProperties = AsyncReceiptProperties.builder()
                .defaultCallbackUrl(callbackPublicUri.toString())
                .pollInterval(Duration.ofSeconds(2))
                .callbackFutureExpiration(USER_ACK_TIMEOUT)
                .build();
        PushoverClient client = new PushoverClient(
                token,
                encryptionKey,
                HttpClient.newHttpClient(),
                "https://api.pushover.net",
                asyncProperties,
                null
        );

        ExecutorService executorService = Executors.newCachedThreadPool();
        Path baseDir = Files.createTempDirectory("pushover-client-servlet-tomcat-base-");
        Path docBase = Files.createTempDirectory("pushover-client-servlet-tomcat-docbase-");
        Tomcat tomcat = new Tomcat();
        boolean started = false;
        try {
            tomcat.setBaseDir(baseDir.toString());

            Connector connector = new Connector();
            connector.setThrowOnFailure(true);
            connector.setPort(Math.max(callbackBindUri.getPort(), 0));
            connector.setProperty("address", callbackBindUri.getHost());
            if (connector.getProtocolHandler() instanceof AbstractProtocol<?> protocolHandler) {
                protocolHandler.setExecutor(executorService);
            }
            tomcat.setConnector(connector);

            Context context = tomcat.addContext("", docBase.toString());
            HttpServlet callbackServlet = CallbackServlets.create(client);
            Tomcat.addServlet(context, CALLBACK_SERVLET_NAME, callbackServlet);
            context.addServletMappingDecoded(normalizePath(callbackBindUri.getPath()), CALLBACK_SERVLET_NAME);

            tomcat.start();
            started = true;

            System.out.printf(
                    "Waiting for Tomcat servlet callback acknowledgement. Local bind=%s, public callback=%s%n",
                    callbackBindUri,
                    callbackPublicUri
            );
            MessageRequest request = MessageRequest.builder(
                            user,
                            "[Async Tomcat Servlet Callback IT] Please acknowledge this notification within 1 minute so the Tomcat-hosted servlet callback async integration test can pass."
                    )
                    .title("PushoverClient Async Tomcat Servlet Callback IT")
                    .priority(MessagePriority.EMERGENCY)
                    .retry(30)
                    .expire(300)
                    .tags(tag)
                    .build();

            ReceiptCallbackResponse response = client.sendEmergencyMessageAndAwaitCallback(
                    request,
                    executorService,
                    USER_ACK_TIMEOUT
            ).get(USER_ACK_TIMEOUT.toSeconds() + 10, TimeUnit.SECONDS);

            assertNotNull(response);
            assertFalse(isBlank(response.receipt()));
            assertEquals(1, response.acknowledged());
            System.out.printf(
                    "SUCCESS: Async Tomcat servlet callback IT acknowledged by %s on device %s for receipt %s%n",
                    valueOrPlaceholder(response.acknowledgedBy()),
                    valueOrPlaceholder(response.acknowledgedByDevice()),
                    response.receipt()
            );
        } finally {
            cancelByTagQuietly(client, tag);
            if (started) {
                tomcat.stop();
                tomcat.destroy();
            }
            executorService.shutdownNow();
            deleteDirectoryIfExists(docBase);
            deleteDirectoryIfExists(baseDir);
        }
    }

    private static void requireAsyncEmergencyTestsEnabled() {
        String enabled = optional("pushover.enableAsyncEmergencyTests");
        if (!"true".equalsIgnoreCase(enabled)) {
            LOGGER.warn("Skipping async servlet integration tests: set pushover.enableAsyncEmergencyTests=true to enable interactive acknowledgement tests");
        }
        Assumptions.assumeTrue("true".equalsIgnoreCase(enabled), "async emergency tests disabled");
    }

    private static void cancelByTagQuietly(PushoverClient client, String tag) {
        try {
            client.cancelByTag(tag);
        } catch (Exception ex) {
            LOGGER.warn("Could not cancel async emergency receipt by tag {} during cleanup", tag, ex);
        }
    }

    private static String uniqueTag() {
        return "pushoverclient-async-servlet-tomcat-" + (System.currentTimeMillis() / 1000L);
    }

    private static String valueOrPlaceholder(String value) {
        return isBlank(value) ? "<unknown>" : value;
    }

    private static URI requiredUri(String key) {
        String value = required(key);
        try {
            return new URI(value.trim());
        } catch (URISyntaxException ex) {
            throw new org.opentest4j.TestAbortedException("invalid URI for " + key + ": " + value, ex);
        }
    }

    private static void validateServletCallbackReachability(URI bindUri, URI publicUri) {
        Assumptions.assumeTrue("http".equalsIgnoreCase(bindUri.getScheme()), "servlet integration test bind URI must use http");

        String bindHost = bindUri.getHost();
        String publicHost = publicUri.getHost();

        boolean bindLoopback = isLoopbackHost(bindHost);
        boolean publicLoopback = isLoopbackHost(publicHost);

        if (bindLoopback && !publicLoopback) {
            throw new org.opentest4j.TestAbortedException(
                    "Servlet callback server is bound to loopback address " + bindUri
                            + " but the public callback URL is " + publicUri
                            + ". Incoming internet traffic cannot reach a server bound only to 127.0.0.1/localhost. "
                            + "Use 0.0.0.0 or the machine's LAN IP for pushover.asyncCallbackBindUri when relying on port forwarding."
            );
        }
    }

    private static boolean isLoopbackHost(String host) {
        return "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "::1".equals(host);
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        return path;
    }

    private static String required(String key) {
        String value = optional(key);
        if (isBlank(value)) {
            LOGGER.warn("Skipping async servlet integration tests: missing {}", key);
            Assumptions.assumeTrue(!isBlank(value), "missing " + key);
            throw new IllegalStateException("unreachable");
        }
        return value;
    }

    private static String optional(String key) {
        String sys = System.getProperty(key);
        if (!isBlank(sys)) {
            return sys;
        }

        String envName = key.toUpperCase().replace('.', '_');
        String env = System.getenv(envName);
        if (!isBlank(env)) {
            return env;
        }
        return CONFIG.getProperty(key);
    }

    private static Properties loadConfig() {
        Properties props = new Properties();
        try (InputStream in = PushoverClientServletTomcatAsyncIT.class.getClassLoader().getResourceAsStream("pushover-integration.properties")) {
            if (in != null) {
                props.load(in);
            } else {
                LOGGER.warn("No pushover-integration.properties found; async servlet integration tests may be skipped");
            }
        } catch (IOException ex) {
            LOGGER.warn("Could not load async servlet integration properties", ex);
        }
        return props;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void deleteDirectoryIfExists(Path directory) throws IOException {
        if (directory == null || Files.notExists(directory)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(directory)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException ex) {
                            throw new IllegalStateException("Failed to delete " + path, ex);
                        }
                    });
        } catch (IllegalStateException ex) {
            if (ex.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw ex;
        }
    }
}

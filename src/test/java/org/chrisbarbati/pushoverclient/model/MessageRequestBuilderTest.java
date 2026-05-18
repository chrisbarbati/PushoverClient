package org.chrisbarbati.pushoverclient.model;

import org.chrisbarbati.pushoverclient.model.attachment.Attachment;
import org.chrisbarbati.pushoverclient.model.attachment.AttachmentEncoding;
import org.chrisbarbati.pushoverclient.model.message.MessagePriority;
import org.chrisbarbati.pushoverclient.model.message.MessageRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests that exercise optional MessageRequest builder methods.
 *
 * @since 0.1.0
 * @author Christian Barbati
 */
class MessageRequestBuilderTest {

    /**
     * Verifies all optional builder methods populate the immutable request model.
     *
     * @since 0.1.0
     * @author Christian Barbati
     */
    @Test
    void builder_setsAllOptionalFields() {
        // A single attachment instance lets the test verify nested value propagation too.
        Attachment attachment = new Attachment(
                "sample.jpg",
                new byte[]{1, 2, 3},
                "image/jpeg",
                AttachmentEncoding.BASE64
        );

        MessageRequest request = MessageRequest.builder("uQiRzpo4DXghDmr9QzzfQu27cmVRsG", "hello")
                .device("droid4")
                .title("title")
                .url("https://example.com")
                .urlTitle("Example")
                .html(true)
                .priority(MessagePriority.EMERGENCY)
                .sound("bike")
                .timestamp(1_700_000_000L)
                .ttl(60)
                .retry(30)
                .expire(300)
                .callback("https://example.com/callback")
                .tags("tag1,tag2")
                .attachment(attachment)
                .encrypted(true)
                .build();

        // This test intentionally checks many getters to guard the builder-to-model handoff.
        assertEquals("uQiRzpo4DXghDmr9QzzfQu27cmVRsG", request.user());
        assertEquals("hello", request.message());
        assertEquals("droid4", request.device());
        assertEquals("title", request.title());
        assertEquals("https://example.com", request.url());
        assertEquals("Example", request.urlTitle());
        assertEquals(1, request.html());
        assertEquals(MessagePriority.EMERGENCY, request.priority());
        assertEquals("bike", request.sound());
        assertEquals(1_700_000_000L, request.timestamp());
        assertEquals(60, request.ttl());
        assertEquals(30, request.retry());
        assertEquals(300, request.expire());
        assertEquals("https://example.com/callback", request.callback());
        assertEquals("tag1,tag2", request.tags());
        assertEquals("sample.jpg", request.attachment().fileName());
        assertArrayEquals(new byte[]{1, 2, 3}, request.attachment().data());
        assertTrue(request.encrypted());
    }
}


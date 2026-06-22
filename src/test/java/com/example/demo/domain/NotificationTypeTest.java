package com.example.demo.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NotificationTypeTest {

    @Test
    void parsesCaseInsensitively() {
        assertEquals(NotificationType.EMAIL, NotificationType.fromValue("email"));
        assertEquals(NotificationType.SMS, NotificationType.fromValue("SMS"));
    }

    @Test
    void rejectsUnknown() {
        assertThrows(IllegalArgumentException.class, () -> NotificationType.fromValue("fax"));
    }
}

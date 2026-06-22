package com.example.demo.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum NotificationType {
    EMAIL("email"), SMS("sms");

    private final String value;

    NotificationType(String value) {
        this.value = value;
    }

    @JsonValue
    public String getValue() {
        return value;
    }

    @JsonCreator
    public static NotificationType fromValue(String raw) {
        if (raw == null) {
            throw new IllegalArgumentException("type is required");
        }
        for (NotificationType t : values()) {
            if (t.value.equalsIgnoreCase(raw.trim())) {
                return t;
            }
        }
        throw new IllegalArgumentException("unknown notification type: " + raw);
    }
}

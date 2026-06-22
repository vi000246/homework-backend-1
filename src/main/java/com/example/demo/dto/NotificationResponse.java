package com.example.demo.dto;

import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;

import java.time.Instant;

public class NotificationResponse {
    public Long id;
    public NotificationType type;
    public String recipient;
    public String subject;
    public String content;
    public Instant createdAt;
    public Instant updatedAt;

    public static NotificationResponse from(Notification n) {
        NotificationResponse r = new NotificationResponse();
        r.id = n.getId();
        r.type = n.getType();
        r.recipient = n.getRecipient();
        r.subject = n.getSubject();
        r.content = n.getContent();
        r.createdAt = n.getCreatedAt();
        r.updatedAt = n.getUpdatedAt();
        return r;
    }
}

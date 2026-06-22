package com.example.demo.dto;

import com.example.demo.domain.Notification;

public class NotificationMessage {
    public Long id;
    public String type;
    public String recipient;
    public String subject;
    public String content;

    public static NotificationMessage from(Notification n) {
        NotificationMessage m = new NotificationMessage();
        m.id = n.getId();
        m.type = n.getType().getValue();
        m.recipient = n.getRecipient();
        m.subject = n.getSubject();
        m.content = n.getContent();
        return m;
    }

    public Long getId() {
        return id;
    }
}

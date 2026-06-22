package com.example.demo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class CreateNotificationRequest {

    @NotBlank(message = "type is required")
    private String type;                       // validated against enum in service

    @NotBlank(message = "recipient is required")
    @Size(max = 255, message = "recipient too long")
    private String recipient;                  // format checked per-type in service (email vs phone)

    @Size(max = 255, message = "subject too long")   // matches VARCHAR(255) — fail fast, not on DB error
    private String subject;

    private String content;                    // TEXT column — unbounded

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }
}

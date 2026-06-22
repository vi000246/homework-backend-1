package com.example.demo.dto;

import jakarta.validation.constraints.Size;

public class UpdateNotificationRequest {

    @Size(max = 255, message = "subject too long")
    private String subject;

    private String content;

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

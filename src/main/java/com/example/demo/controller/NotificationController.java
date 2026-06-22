package com.example.demo.controller;

import com.example.demo.dto.CreateNotificationRequest;
import com.example.demo.dto.NotificationResponse;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.service.NotificationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NotificationResponse create(@Valid @RequestBody CreateNotificationRequest req) {
        return NotificationResponse.from(service.create(req));
    }

    @GetMapping("/{id}")
    public NotificationResponse getById(@PathVariable Long id) {
        return NotificationResponse.from(service.getById(id));
    }

    @GetMapping("/recent")
    public List<NotificationResponse> recent() {
        return service.recent().stream().map(NotificationResponse::from).toList();
    }

    @PutMapping("/{id}")
    public NotificationResponse update(@PathVariable Long id, @Valid @RequestBody UpdateNotificationRequest req) {
        return NotificationResponse.from(service.update(id, req));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}

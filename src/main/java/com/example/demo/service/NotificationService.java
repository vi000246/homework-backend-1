package com.example.demo.service;

import com.example.demo.cache.NotificationCacheService;
import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;
import com.example.demo.dto.CreateNotificationRequest;
import com.example.demo.dto.NotificationMessage;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.exception.NotFoundException;
import com.example.demo.messaging.NotificationProducer;
import com.example.demo.repository.NotificationRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.regex.Pattern;

@Service
public class NotificationService {

    private static final int RECENT_LIMIT = 10;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    private static final Pattern PHONE = Pattern.compile("^\\+?[0-9]{7,15}$");

    private final NotificationRepository repo;
    private final NotificationCacheService cache;
    private final NotificationProducer producer;

    public NotificationService(NotificationRepository repo, NotificationCacheService cache, NotificationProducer producer) {
        this.repo = repo;
        this.cache = cache;
        this.producer = producer;
    }

    public Notification create(CreateNotificationRequest req) {
        NotificationType type = NotificationType.fromValue(req.getType());   // throws IllegalArgumentException → 400
        validateRecipient(type, req.getRecipient());
        Notification n = new Notification();
        n.setType(type);
        n.setRecipient(req.getRecipient());
        n.setSubject(req.getSubject());
        n.setContent(req.getContent());
        repo.insert(n);                                          // 1) DB is source of truth (committed first)
        cache.putById(n);                                       // 2) warm by-id cache
        cache.pushRecent(n);                                    // 3) prepend to recent list
        producer.send(NotificationMessage.from(n));            // 4) best-effort publish (log-and-continue)
        return n;
    }

    public Notification getById(Long id) {
        return cache.getById(id).orElseGet(() -> {
            Notification n = repo.findById(id)
                    .orElseThrow(() -> new NotFoundException("notification " + id + " not found"));
            cache.putById(n);
            return n;
        });
    }

    public List<Notification> recent() {
        List<Notification> cached = cache.getRecent();
        if (!cached.isEmpty()) {
            return cached;
        }
        List<Notification> fromDb = repo.findRecent(RECENT_LIMIT);   // rebuild on miss / cold start
        cache.replaceRecent(fromDb);
        return fromDb;
    }

    public Notification update(Long id, UpdateNotificationRequest req) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("notification " + id + " not found");
        }
        repo.updateSubjectContent(id, req.getSubject(), req.getContent());
        Notification fresh = repo.findById(id)
                .orElseThrow(() -> new NotFoundException("notification " + id + " not found"));
        cache.putById(fresh);        // refresh by-id
        cache.evictRecent();         // recent list may hold a stale copy → invalidate, rebuild on next read
        return fresh;
    }

    public void delete(Long id) {
        if (!repo.existsById(id)) {
            throw new NotFoundException("notification " + id + " not found");
        }
        repo.deleteById(id);
        cache.evict(id);             // remove by-id
        cache.evictRecent();         // deleted row may be in recent list → invalidate, rebuild on next read
    }

    private void validateRecipient(NotificationType type, String recipient) {
        boolean ok = switch (type) {
            case EMAIL -> EMAIL.matcher(recipient).matches();
            case SMS -> PHONE.matcher(recipient).matches();
        };
        if (!ok) {
            throw new IllegalArgumentException(
                    "recipient '" + recipient + "' is not a valid " + type.getValue() + " address");
        }
    }
}

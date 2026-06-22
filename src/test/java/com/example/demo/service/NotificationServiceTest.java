package com.example.demo.service;

import com.example.demo.cache.NotificationCacheService;
import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;
import com.example.demo.dto.CreateNotificationRequest;
import com.example.demo.dto.UpdateNotificationRequest;
import com.example.demo.exception.NotFoundException;
import com.example.demo.messaging.NotificationProducer;
import com.example.demo.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    NotificationRepository repo;
    @Mock
    NotificationCacheService cache;
    @Mock
    NotificationProducer producer;
    @InjectMocks
    NotificationService service;

    private Notification sample(long id) {
        Notification n = new Notification();
        n.setId(id);
        n.setType(NotificationType.EMAIL);
        n.setRecipient("u@e.com");
        n.setSubject("s");
        n.setContent("c");
        n.setCreatedAt(Instant.now());
        n.setUpdatedAt(Instant.now());
        return n;
    }

    @Test
    void create_persists_publishes_caches() {
        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setType("email");
        req.setRecipient("u@e.com");
        req.setSubject("s");
        req.setContent("c");
        when(repo.insert(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(1L);
            return n;
        });

        Notification out = service.create(req);

        assertEquals(1L, out.getId());
        verify(repo).insert(any());
        verify(producer).send(any());
        verify(cache).pushRecent(any());
        verify(cache).putById(any());
    }

    @Test
    void create_invalidType_throws() {
        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setType("fax");
        req.setRecipient("u@e.com");
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void create_emailTypeWithPhoneRecipient_throws() {   // per-type recipient validation
        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setType("email");
        req.setRecipient("+15551234567");
        assertThrows(IllegalArgumentException.class, () -> service.create(req));
    }

    @Test
    void create_smsTypeWithPhoneRecipient_ok() {
        CreateNotificationRequest req = new CreateNotificationRequest();
        req.setType("sms");
        req.setRecipient("+15551234567");
        req.setSubject("s");
        req.setContent("c");
        when(repo.insert(any())).thenAnswer(inv -> {
            Notification n = inv.getArgument(0);
            n.setId(2L);
            return n;
        });
        assertEquals(2L, service.create(req).getId());
    }

    @Test
    void getById_cacheMiss_backfills() {                 // AC-3
        when(cache.getById(5L)).thenReturn(Optional.empty());
        when(repo.findById(5L)).thenReturn(Optional.of(sample(5L)));
        Notification out = service.getById(5L);
        assertEquals(5L, out.getId());
        verify(cache).putById(any());
    }

    @Test
    void getById_missing_throws404() {                   // AC-4
        when(cache.getById(9L)).thenReturn(Optional.empty());
        when(repo.findById(9L)).thenReturn(Optional.empty());
        assertThrows(NotFoundException.class, () -> service.getById(9L));
    }

    @Test
    void update_refreshesCacheAndInvalidatesRecent() {   // AC-6 (+ recent staleness fix)
        when(repo.existsById(5L)).thenReturn(true);
        when(repo.findById(5L)).thenReturn(Optional.of(sample(5L)));
        UpdateNotificationRequest req = new UpdateNotificationRequest();
        req.setSubject("s2");
        req.setContent("c2");
        service.update(5L, req);
        verify(repo).updateSubjectContent(5L, "s2", "c2");
        verify(cache).putById(any());
        verify(cache).evictRecent();                     // recent list must not serve stale subject
    }

    @Test
    void update_missing_throws404() {                    // AC-7
        when(repo.existsById(9L)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> service.update(9L, new UpdateNotificationRequest()));
    }

    @Test
    void delete_evictsByIdAndRecent() {                  // AC-8 (+ recent staleness fix)
        when(repo.existsById(5L)).thenReturn(true);
        service.delete(5L);
        verify(repo).deleteById(5L);
        verify(cache).evict(5L);
        verify(cache).evictRecent();                     // deleted row must not linger in recent
    }

    @Test
    void delete_missing_throws404() {                    // AC-9
        when(repo.existsById(9L)).thenReturn(false);
        assertThrows(NotFoundException.class, () -> service.delete(9L));
    }

    @Test
    void recent_cacheMiss_rebuildsFromDb() {             // AC-5 (rebuild path)
        when(cache.getRecent()).thenReturn(List.of());
        when(repo.findRecent(10)).thenReturn(List.of(sample(2L), sample(1L)));
        var out = service.recent();
        assertEquals(2, out.size());
        verify(cache).replaceRecent(anyList());
    }
}

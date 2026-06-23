package com.example.demo.repository;

import com.example.demo.domain.Notification;
import com.example.demo.domain.NotificationType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test for the persistence layer against a REAL MySQL (Testcontainers).
 * Proves the JdbcTemplate SQL — generated-key retrieval, ORDER BY/LIMIT, column mapping —
 * which the mocked unit tests cannot cover. Runs under `mvn verify` (failsafe), needs Docker.
 */
@Testcontainers
class NotificationRepositoryIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("taskdb")
            .withUsername("taskuser")
            .withPassword("taskpass")
            .withInitScript("schema.sql");

    static NotificationRepository repo;

    @BeforeAll
    static void setup() {
        DriverManagerDataSource ds = new DriverManagerDataSource(
                mysql.getJdbcUrl(), mysql.getUsername(), mysql.getPassword());
        ds.setDriverClassName(mysql.getDriverClassName());
        repo = new NotificationRepository(new JdbcTemplate(ds));
    }

    private Notification newNotification(String recipient, String subject) {
        Notification n = new Notification();
        n.setType(NotificationType.EMAIL);
        n.setRecipient(recipient);
        n.setSubject(subject);
        n.setContent("body");
        return n;
    }

    @Test
    void insert_assignsIdAndTimestamps_andFindByIdReturnsIt() {
        Notification saved = repo.insert(newNotification("a@e.com", "s1"));
        assertNotNull(saved.getId());
        assertNotNull(saved.getCreatedAt());

        Optional<Notification> found = repo.findById(saved.getId());
        assertTrue(found.isPresent());
        assertEquals("a@e.com", found.get().getRecipient());
        assertEquals(NotificationType.EMAIL, found.get().getType());
    }

    @Test
    void findById_missing_returnsEmpty() {
        assertTrue(repo.findById(99999999L).isEmpty());
    }

    @Test
    void existsById_reflectsRowPresence() {
        Notification saved = repo.insert(newNotification("b@e.com", "s2"));
        assertTrue(repo.existsById(saved.getId()));
        assertFalse(repo.existsById(99999999L));
    }

    @Test
    void findRecent_ordersNewestFirst_andRespectsLimit() {
        for (int i = 0; i < 5; i++) {
            repo.insert(newNotification("r" + i + "@e.com", "subj" + i));
        }
        List<Notification> recent = repo.findRecent(3);
        assertEquals(3, recent.size());
        // newest-first: created_at DESC, id DESC — last inserted id should lead
        assertTrue(recent.get(0).getId() > recent.get(1).getId());
        assertTrue(recent.get(1).getId() > recent.get(2).getId());
    }

    @Test
    void update_persistsChanges_andBumpsVersion() {
        Notification saved = repo.insert(newNotification("c@e.com", "old"));
        assertEquals(0L, saved.getVersion());
        int rows = repo.update(saved.getId(), "newSubject", "newContent", null);
        assertEquals(1, rows);

        Notification reloaded = repo.findById(saved.getId()).orElseThrow();
        assertEquals("newSubject", reloaded.getSubject());
        assertEquals("newContent", reloaded.getContent());
        assertEquals(1L, reloaded.getVersion());   // version incremented
    }

    @Test
    void update_withStaleVersion_updatesNoRow() {
        Notification saved = repo.insert(newNotification("e@e.com", "v0"));   // version 0
        // Pretend another writer already bumped it: expecting version 5 must match nothing.
        int rows = repo.update(saved.getId(), "x", "y", 5L);
        assertEquals(0, rows);                                                 // optimistic-lock guard
        // unchanged
        assertEquals("v0", repo.findById(saved.getId()).orElseThrow().getSubject());
    }

    @Test
    void deleteById_removesRow() {
        Notification saved = repo.insert(newNotification("d@e.com", "x"));
        assertEquals(1, repo.deleteById(saved.getId()));
        assertTrue(repo.findById(saved.getId()).isEmpty());
    }
}

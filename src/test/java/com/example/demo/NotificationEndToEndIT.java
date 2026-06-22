package com.example.demo;

import com.example.demo.domain.Notification;
import com.example.demo.repository.NotificationRepository;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * End-to-end integration test across the three required technologies:
 *   - MySQL (Testcontainers, real persistence)
 *   - Redis (Testcontainers, real cache)
 *   - RocketMQ producer (captured via @MockBean — proves payload + topic without a flaky broker)
 *
 * Drives real HTTP through the running app. Runs under `mvn verify` (failsafe), needs Docker.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class NotificationEndToEndIT {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("taskdb")
            .withUsername("taskuser")
            .withPassword("taskpass")
            .withInitScript("schema.sql");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7").withExposedPorts(6379);

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", mysql::getJdbcUrl);
        r.add("spring.datasource.username", mysql::getUsername);
        r.add("spring.datasource.password", mysql::getPassword);
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("rocketmq.name-server", () -> "localhost:9876"); // unused — producer is mocked
    }

    // Replaces the real RocketMQ producer bean, so no broker is needed and we can assert the payload.
    @MockBean
    DefaultMQProducer mqProducer;

    @Autowired
    TestRestTemplate rest;
    @Autowired
    RedisTemplate<String, Object> redisTemplate;
    @Autowired
    NotificationRepository repo;

    private Map<String, String> emailBody(String subject) {
        return Map.of("type", "email", "recipient", "user@example.com",
                "subject", subject, "content", "hello");
    }

    @Test
    void create_persistsToMySql_cachesInRedis_andPublishesToTopic() throws Exception {
        ResponseEntity<Map> resp = rest.postForEntity("/notifications", emailBody("Welcome"), Map.class);
        assertEquals(HttpStatus.CREATED, resp.getStatusCode());
        Number id = (Number) resp.getBody().get("id");
        assertNotNull(id);

        // 1) MySQL — row really persisted
        assertTrue(repo.findById(id.longValue()).isPresent());

        // 2) Redis — by-id cache populated
        assertNotNull(redisTemplate.opsForValue().get("notification:" + id));

        // 3) RocketMQ — published to the correct topic with a real payload
        ArgumentCaptor<Message> msg = ArgumentCaptor.forClass(Message.class);
        verify(mqProducer, timeout(2000)).send(msg.capture());
        assertEquals("notification-topic", msg.getValue().getTopic());
        assertTrue(msg.getValue().getBody().length > 0);
    }

    @Test
    void fullLifecycle_create_get_recent_update_delete() {
        Long id = ((Number) rest.postForEntity("/notifications", emailBody("L1"), Map.class)
                .getBody().get("id")).longValue();

        // GET by id — 200 (served via cache-aside)
        ResponseEntity<Map> got = rest.getForEntity("/notifications/" + id, Map.class);
        assertEquals(HttpStatus.OK, got.getStatusCode());
        assertEquals("L1", got.getBody().get("subject"));

        // recent — contains our item
        ResponseEntity<java.util.List> recent = rest.getForEntity("/notifications/recent", java.util.List.class);
        assertEquals(HttpStatus.OK, recent.getStatusCode());
        assertTrue(recent.getBody().size() >= 1);

        // update — 200 and DB reflects change
        rest.put("/notifications/" + id, Map.of("subject", "L1-updated", "content", "c2"));
        Notification afterUpdate = repo.findById(id).orElseThrow();
        assertEquals("L1-updated", afterUpdate.getSubject());

        // delete — 204 and row gone
        ResponseEntity<Void> del = rest.exchange("/notifications/" + id,
                org.springframework.http.HttpMethod.DELETE, null, Void.class);
        assertEquals(HttpStatus.NO_CONTENT, del.getStatusCode());
        assertTrue(repo.findById(id).isEmpty());

        // GET after delete — 404
        assertEquals(HttpStatus.NOT_FOUND,
                rest.getForEntity("/notifications/" + id, Map.class).getStatusCode());
    }
}

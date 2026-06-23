package com.example.demo.messaging;

import com.example.demo.dto.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationProducerTest {

    @Mock
    DefaultMQProducer mqProducer;

    NotificationProducer producer;

    @BeforeEach
    void setup() {
        producer = new NotificationProducer(mqProducer, new ObjectMapper(), "notification-topic");
    }

    private NotificationMessage message() {
        NotificationMessage m = new NotificationMessage();
        m.id = 1L;
        m.type = "email";
        m.recipient = "u@e.com";
        m.subject = "s";
        m.content = "c";
        return m;
    }

    @Test
    void send_publishesToConfiguredTopic() throws Exception {
        producer.send(message());
        ArgumentCaptor<Message> captor = ArgumentCaptor.forClass(Message.class);
        verify(mqProducer).send(captor.capture());
        assertEquals("notification-topic", captor.getValue().getTopic());
    }

    @Test
    void send_whenBrokerFails_doesNotPropagate() throws Exception {
        when(mqProducer.send(any(Message.class))).thenThrow(new RuntimeException("broker down"));
        // log-and-continue: a broker failure must never break the caller (DB is source of truth)
        assertDoesNotThrow(() -> producer.send(message()));
    }
}

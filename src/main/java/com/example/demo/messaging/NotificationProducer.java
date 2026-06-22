package com.example.demo.messaging;

import com.example.demo.dto.NotificationMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.message.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class NotificationProducer {

    private static final Logger log = LoggerFactory.getLogger(NotificationProducer.class);

    private final DefaultMQProducer producer;
    private final ObjectMapper objectMapper;
    private final String topic;

    public NotificationProducer(DefaultMQProducer producer,
                                ObjectMapper objectMapper,
                                @Value("${rocketmq.topic}") String topic) {
        this.producer = producer;
        this.objectMapper = objectMapper;
        this.topic = topic;
    }

    public void send(NotificationMessage msg) {
        try {
            byte[] body = objectMapper.writeValueAsBytes(msg);
            producer.send(new Message(topic, body));
        } catch (Exception e) {
            // DB is source of truth; MQ publish is best-effort for this assignment scope.
            log.warn("MQ publish failed for notification id={}, continuing", msg.getId(), e);
        }
    }
}

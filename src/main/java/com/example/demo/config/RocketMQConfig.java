package com.example.demo.config;

import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RocketMQConfig {

    private static final Logger log = LoggerFactory.getLogger(RocketMQConfig.class);

    @Bean(destroyMethod = "shutdown")   // name must differ from the @Component NotificationProducer bean
    public DefaultMQProducer rocketMQProducer(
            @Value("${rocketmq.name-server}") String nameServer,
            @Value("${rocketmq.producer.group}") String group,
            @Value("${rocketmq.producer.send-timeout-ms:3000}") int sendTimeout,
            @Value("${rocketmq.producer.retries:2}") int retries) {
        DefaultMQProducer producer = new DefaultMQProducer(group);
        producer.setNamesrvAddr(nameServer);
        producer.setSendMsgTimeout(sendTimeout);                 // bound latency — never hang a request thread
        producer.setRetryTimesWhenSendFailed(retries);
        try {
            producer.start();
        } catch (Exception e) {
            // Don't fail app startup on a transient broker outage; producer will (re)connect on first send.
            log.error("RocketMQ producer failed to start against {} — app continues; sends will be best-effort",
                    nameServer, e);
        }
        return producer;
    }
}

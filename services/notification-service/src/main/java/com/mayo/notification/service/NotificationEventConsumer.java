package com.mayo.notification.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.topics.Topics;
import com.mayo.notification.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationProcessor notificationProcessor;

    @KafkaListener(
        topics = Topics.NOTIFICATION_EVENTS,
        groupId = "notification-service",
        concurrency = "#{${notification.kafka.consumer.concurrency:3}}",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeNotificationEvent(
            @Payload String message,
            @Header(KafkaHeaders.RECEIVED_KEY) String key,
            @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
            @Header(KafkaHeaders.RECEIVED_PARTITION) Integer partition,
            @Header(KafkaHeaders.OFFSET) Long offset,
            Acknowledgment acknowledgment) {

        try {
            log.debug("Received notification event: key={}, topic={}, partition={}, offset={}",
                     key, topic, partition, offset);

            NotificationEvent event = objectMapper.readValue(message, NotificationEvent.class);

            // Process the notification event
            notificationProcessor.processNotificationEvent(event);

            acknowledgment.acknowledge();
            log.info("Successfully processed notification event for user: {}", event.getUserId());

        } catch (Exception e) {
            log.error("Failed to process notification event: {}", message, e);
            // In a production system, you might want to send to a dead letter topic
            // or implement retry logic here
            acknowledgment.acknowledge(); // Acknowledge to avoid infinite retries for now
        }
    }
}
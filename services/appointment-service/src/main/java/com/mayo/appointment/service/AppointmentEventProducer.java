package com.mayo.appointment.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.model.AppointmentEvent;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentEventProducer {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public void publishAppointmentEvent(AppointmentEvent event) {
        try {
            String message = objectMapper.writeValueAsString(event);
            String key = event.getAppointmentId().toString();
            
            log.info("Publishing appointment event: {} with type: {}", key, event.getEventType());
            kafkaTemplate.send(Topics.APPOINTMENT_EVENTS, key, message);
            
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize appointment event", e);
        }
    }
}

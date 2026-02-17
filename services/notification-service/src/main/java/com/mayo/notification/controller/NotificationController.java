package com.mayo.notification.controller;

import com.mayo.common.core.dto.ApiResponse;
import com.mayo.notification.dto.NotificationResponse;
import com.mayo.notification.dto.SendNotificationRequest;
import com.mayo.notification.entity.Notification;
import com.mayo.notification.entity.NotificationStatus;
import com.mayo.notification.repository.NotificationRepository;
import com.mayo.notification.service.NotificationProcessor;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification API", description = "API for sending and managing notifications")
public class NotificationController {

    private final NotificationProcessor notificationProcessor;
    private final NotificationRepository notificationRepository;
    private final com.mayo.notification.service.NotificationService notificationService;

    @PostMapping("/send")
    @Operation(summary = "Send a notification")
    public ResponseEntity<ApiResponse<NotificationResponse>> sendNotification(
            @Valid @RequestBody SendNotificationRequest request) {

        log.info("Sending notification to user: {}", request.getUserId());

        // Create notification entity from request
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .templateId(request.getTemplateId())
                .templateData(request.getTemplateData())
                .priority(request.getPriority())
                .scheduledAt(request.getScheduledAt())
                .build();

        // Process the notification
        notificationProcessor.processNotificationEvent(convertToEvent(request));

        // Return response
        NotificationResponse response = convertToResponse(notification);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/schedule")
    @Operation(summary = "Schedule a notification for future delivery")
    public ResponseEntity<ApiResponse<NotificationResponse>> scheduleNotification(
            @Valid @RequestBody SendNotificationRequest request) {

        if (request.getScheduledAt() == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error("Scheduled time is required for scheduling"));
        }

        log.info("Scheduling notification for user: {} at {}", request.getUserId(), request.getScheduledAt());

        // Create notification entity
        Notification notification = Notification.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .templateId(request.getTemplateId())
                .templateData(request.getTemplateData())
                .priority(request.getPriority())
                .status(NotificationStatus.PENDING)
                .scheduledAt(request.getScheduledAt())
                .build();

        notification = notificationRepository.save(notification);

        NotificationResponse response = convertToResponse(notification);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/status")
    @Operation(summary = "Get notification delivery status")
    public ResponseEntity<ApiResponse<NotificationResponse>> getNotificationStatus(@PathVariable UUID id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Notification not found"));

        NotificationResponse response = convertToResponse(notification);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/history/{userId}")
    @Operation(summary = "Get notification history for a user")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> getNotificationHistory(
            @PathVariable UUID userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size);
        Page<Notification> notifications = notificationRepository.findByUserId(userId, pageable);

        List<NotificationResponse> responses = notifications.getContent().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(responses));
    }

    private com.mayo.notification.dto.NotificationEvent convertToEvent(SendNotificationRequest request) {
        return com.mayo.notification.dto.NotificationEvent.builder()
                .userId(request.getUserId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .templateId(request.getTemplateId())
                .templateData(request.getTemplateData())
                .priority(request.getPriority().name())
                .channels(request.getChannels() != null ? request.getChannels().toArray(new String[0]) : new String[]{"PUSH"})
                .scheduledAt(request.getScheduledAt() != null ? request.getScheduledAt().toString() : null)
                .build();
    }

    private NotificationResponse convertToResponse(Notification notification) {
        return NotificationResponse.builder()
                .id(notification.getId())
                .userId(notification.getUserId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .templateId(notification.getTemplateId())
                .templateData(notification.getTemplateData())
                .priority(notification.getPriority())
                .status(notification.getStatus())
                .scheduledAt(notification.getScheduledAt())
                .createdAt(notification.getCreatedAt())
                .sentAt(notification.getSentAt())
                .deliveredAt(notification.getDeliveredAt())
                .failureReason(notification.getFailureReason())
                .build();
    }
}
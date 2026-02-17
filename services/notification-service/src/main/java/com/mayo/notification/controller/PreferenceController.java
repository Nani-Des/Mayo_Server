package com.mayo.notification.controller;

import com.mayo.common.core.dto.ApiResponse;
import com.mayo.notification.dto.UserPreferencesDto;
import com.mayo.notification.dto.DeviceChannelDto;
import com.mayo.notification.entity.UserNotificationPreferences;
import com.mayo.notification.repository.UserNotificationPreferencesRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/notifications/preferences")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Notification Preferences API", description = "API for managing user notification preferences")
public class PreferenceController {

    private final UserNotificationPreferencesRepository preferencesRepository;

    @GetMapping("/{userId}")
    @Operation(summary = "Get user notification preferences")
    public ResponseEntity<ApiResponse<List<UserPreferencesDto>>> getUserPreferences(@PathVariable UUID userId) {
        List<UserNotificationPreferences> preferences = preferencesRepository.findByUserId(userId);

        List<UserPreferencesDto> dtos = preferences.stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    @PutMapping("/{userId}")
    @Operation(summary = "Update user notification preferences")
    public ResponseEntity<ApiResponse<UserPreferencesDto>> updateUserPreferences(
            @PathVariable UUID userId,
            @Valid @RequestBody UserPreferencesDto preferencesDto) {

        log.info("Updating preferences for user: {}", userId);

        UserNotificationPreferences preferences = preferencesRepository
                .findByUserIdAndNotificationType(userId, preferencesDto.getNotificationType())
                .orElse(UserNotificationPreferences.builder()
                        .userId(userId)
                        .notificationType(preferencesDto.getNotificationType())
                        .build());

        preferences.setPushEnabled(preferencesDto.isPushEnabled());
        preferences.setEmailEnabled(preferencesDto.isEmailEnabled());
        preferences.setSmsEnabled(preferencesDto.isSmsEnabled());
        preferences.setEmailAddress(preferencesDto.getEmailAddress());
        preferences.setPhoneNumber(preferencesDto.getPhoneNumber());
        preferences.setTimezone(preferencesDto.getTimezone());
        preferences.setLanguage(preferencesDto.getLanguage());
        preferences.setQuietHoursEnabled(preferencesDto.isQuietHoursEnabled());
        preferences.setQuietHoursStart(preferencesDto.getQuietHoursStart());
        preferences.setQuietHoursEnd(preferencesDto.getQuietHoursEnd());

        preferences = preferencesRepository.save(preferences);

        UserPreferencesDto response = convertToDto(preferences);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{userId}/channels")
    @Operation(summary = "Add device channel for user")
    public ResponseEntity<ApiResponse<String>> addDeviceChannel(
            @PathVariable UUID userId,
            @Valid @RequestBody DeviceChannelDto channelDto) {

        log.info("Adding device channel for user: {}, type: {}", userId, channelDto.getType());

        // Find or create preferences for this user and type
        UserNotificationPreferences preferences = preferencesRepository
                .findByUserIdAndNotificationType(userId, "ALL")
                .orElse(UserNotificationPreferences.builder()
                        .userId(userId)
                        .notificationType("ALL")
                        .build());

        // Update the appropriate token
        if ("FCM".equals(channelDto.getType())) {
            preferences.setFcmToken(channelDto.getToken());
        } else if ("APNs".equals(channelDto.getType())) {
            preferences.setApnsToken(channelDto.getToken());
        }

        preferencesRepository.save(preferences);

        return ResponseEntity.ok(ApiResponse.success("Device channel added successfully"));
    }

    @DeleteMapping("/{userId}/channels/{channelType}")
    @Operation(summary = "Remove device channel for user")
    public ResponseEntity<ApiResponse<String>> removeDeviceChannel(
            @PathVariable UUID userId,
            @PathVariable String channelType) {

        log.info("Removing device channel for user: {}, type: {}", userId, channelType);

        List<UserNotificationPreferences> preferences = preferencesRepository.findByUserId(userId);

        for (UserNotificationPreferences pref : preferences) {
            if ("FCM".equals(channelType)) {
                pref.setFcmToken(null);
            } else if ("APNs".equals(channelType)) {
                pref.setApnsToken(null);
            }
            preferencesRepository.save(pref);
        }

        return ResponseEntity.ok(ApiResponse.success("Device channel removed successfully"));
    }

    private UserPreferencesDto convertToDto(UserNotificationPreferences preferences) {
        return UserPreferencesDto.builder()
                .userId(preferences.getUserId())
                .notificationType(preferences.getNotificationType())
                .pushEnabled(preferences.isPushEnabled())
                .emailEnabled(preferences.isEmailEnabled())
                .smsEnabled(preferences.isSmsEnabled())
                .emailAddress(preferences.getEmailAddress())
                .phoneNumber(preferences.getPhoneNumber())
                .timezone(preferences.getTimezone())
                .language(preferences.getLanguage())
                .quietHoursEnabled(preferences.isQuietHoursEnabled())
                .quietHoursStart(preferences.getQuietHoursStart())
                .quietHoursEnd(preferences.getQuietHoursEnd())
                .channels(List.of(
                    DeviceChannelDto.builder().type("FCM").token(preferences.getFcmToken()).build(),
                    DeviceChannelDto.builder().type("APNs").token(preferences.getApnsToken()).build()
                ))
                .build();
    }
}
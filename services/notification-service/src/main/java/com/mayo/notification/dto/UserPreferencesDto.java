package com.mayo.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferencesDto {

    private UUID userId;
    private String notificationType;
    private boolean pushEnabled;
    private boolean emailEnabled;
    private boolean smsEnabled;
    private String emailAddress;
    private String phoneNumber;
    private String timezone;
    private String language;
    private boolean quietHoursEnabled;
    private LocalTime quietHoursStart;
    private LocalTime quietHoursEnd;
    private List<DeviceChannelDto> channels;
}
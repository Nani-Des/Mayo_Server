package com.mayo.appointment.dto;

import com.mayo.appointment.entity.Appointment.AppointmentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateAppointmentRequest {

    @NotNull(message = "Patient ID is required")
    private UUID patientId;

    private UUID familyMemberId;

    private UUID providerId;

    private UUID hospitalId;

    @NotNull(message = "Appointment date is required")
    private LocalDate appointmentDate;

    @NotNull(message = "Appointment time is required")
    private LocalTime appointmentTime;

    @NotNull(message = "Duration is required")
    private Integer duration; // in minutes

    @NotNull(message = "Appointment type is required")
    private AppointmentType type;

    private String notes;

    private UUID createdBy;
}

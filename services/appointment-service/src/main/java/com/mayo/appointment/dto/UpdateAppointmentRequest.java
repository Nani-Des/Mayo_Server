package com.mayo.appointment.dto;

import com.mayo.appointment.entity.Appointment.AppointmentType;
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
public class UpdateAppointmentRequest {

    private UUID patientId;

    private UUID familyMemberId;

    private UUID providerId;

    private UUID hospitalId;

    private LocalDate appointmentDate;

    private LocalTime appointmentTime;

    private Integer duration; // in minutes

    private AppointmentType type;

    private String notes;
}

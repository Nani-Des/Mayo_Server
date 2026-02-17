package com.mayo.appointment.controller;

import com.mayo.appointment.dto.AppointmentResponse;
import com.mayo.appointment.dto.CreateAppointmentRequest;
import com.mayo.appointment.dto.UpdateAppointmentRequest;
import com.mayo.appointment.dto.UpdateStatusRequest;
import com.mayo.appointment.entity.Appointment.AppointmentStatus;
import com.mayo.appointment.service.AppointmentService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/appointments")
@RequiredArgsConstructor
@Tag(name = "Appointment Management", description = "Appointment management APIs")
public class AppointmentController {

    private final AppointmentService appointmentService;

    @PostMapping
    @Operation(summary = "Create a new appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> createAppointment(
            @Valid @RequestBody CreateAppointmentRequest request) {
        AppointmentResponse created = appointmentService.createAppointment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Appointment created successfully"));
    }

    @GetMapping
    @Operation(summary = "Get all appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAllAppointments() {
        List<AppointmentResponse> appointments = appointmentService.getAllAppointments();
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments retrieved successfully"));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get appointment by ID")
    public ResponseEntity<ApiResponse<AppointmentResponse>> getAppointmentById(@PathVariable UUID id) {
        return appointmentService.getAppointmentById(id)
                .map(appointment -> ResponseEntity.ok(ApiResponse.success(appointment, "Appointment retrieved successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/patient/{patientId}")
    @Operation(summary = "Get appointments by patient ID")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByPatientId(
            @PathVariable UUID patientId) {
        List<AppointmentResponse> appointments = appointmentService.getAppointmentsByPatientId(patientId);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments retrieved successfully"));
    }

    @GetMapping("/family-member/{familyMemberId}")
    @Operation(summary = "Get appointments by family member ID")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByFamilyMemberId(
            @PathVariable UUID familyMemberId) {
        List<AppointmentResponse> appointments = appointmentService.getAppointmentsByFamilyMemberId(familyMemberId);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments retrieved successfully"));
    }

    @GetMapping("/provider/{providerId}")
    @Operation(summary = "Get appointments by provider ID")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByProviderId(
            @PathVariable UUID providerId) {
        List<AppointmentResponse> appointments = appointmentService.getAppointmentsByProviderId(providerId);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments retrieved successfully"));
    }

    @GetMapping("/hospital/{hospitalId}")
    @Operation(summary = "Get appointments by hospital ID")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByHospitalId(
            @PathVariable UUID hospitalId) {
        List<AppointmentResponse> appointments = appointmentService.getAppointmentsByHospitalId(hospitalId);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments retrieved successfully"));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get appointments by status")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsByStatus(
            @PathVariable AppointmentStatus status) {
        List<AppointmentResponse> appointments = appointmentService.getAppointmentsByStatus(status);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments retrieved successfully"));
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Get all upcoming appointments")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAllUpcomingAppointments() {
        List<AppointmentResponse> appointments = appointmentService.getAllUpcomingAppointments();
        return ResponseEntity.ok(ApiResponse.success(appointments, "All upcoming appointments retrieved successfully"));
    }

    @GetMapping("/upcoming/patient/{patientId}")
    @Operation(summary = "Get upcoming appointments for a patient")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUpcomingAppointmentsByPatient(
            @PathVariable UUID patientId) {
        List<AppointmentResponse> appointments = appointmentService.getUpcomingAppointmentsByPatient(patientId);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Upcoming appointments retrieved successfully"));
    }

    @GetMapping("/upcoming/provider/{providerId}")
    @Operation(summary = "Get upcoming appointments for a provider")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getUpcomingAppointmentsByProvider(
            @PathVariable UUID providerId) {
        List<AppointmentResponse> appointments = appointmentService.getUpcomingAppointmentsByProvider(providerId);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Upcoming appointments retrieved successfully"));
    }

    @GetMapping("/range")
    @Operation(summary = "Get appointments in a date range")
    public ResponseEntity<ApiResponse<List<AppointmentResponse>>> getAppointmentsInRange(
            @RequestParam LocalDate startDate,
            @RequestParam LocalDate endDate) {
        List<AppointmentResponse> appointments = appointmentService.getAppointmentsInDateRange(startDate, endDate);
        return ResponseEntity.ok(ApiResponse.success(appointments, "Appointments list in range retrieved successfully"));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update appointment")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointment(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateAppointmentRequest request) {
        return appointmentService.updateAppointment(id, request)
                .map(updated -> ResponseEntity.ok(ApiResponse.success(updated, "Appointment updated successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update appointment status")
    public ResponseEntity<ApiResponse<AppointmentResponse>> updateAppointmentStatus(
            @PathVariable UUID id,
            @Valid @RequestBody UpdateStatusRequest request) {
        return appointmentService.updateAppointmentStatus(id, request.getStatus())
                .map(updated -> ResponseEntity.ok(ApiResponse.success(updated, "Appointment status updated successfully")))
                .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete appointment")
    public ResponseEntity<ApiResponse<Void>> deleteAppointment(@PathVariable UUID id) {
        boolean deleted = appointmentService.deleteAppointment(id);
        if (deleted) {
            return ResponseEntity.ok(ApiResponse.success(null, "Appointment deleted successfully"));
        } else {
            return ResponseEntity.notFound().build();
        }
    }
}

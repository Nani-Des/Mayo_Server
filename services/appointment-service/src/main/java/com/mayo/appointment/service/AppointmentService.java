package com.mayo.appointment.service;

import com.mayo.appointment.dto.AppointmentResponse;
import com.mayo.appointment.dto.CreateAppointmentRequest;
import com.mayo.appointment.dto.UpdateAppointmentRequest;
import com.mayo.appointment.entity.Appointment;
import com.mayo.appointment.entity.Appointment.AppointmentStatus;
import com.mayo.appointment.repository.AppointmentRepository;
import com.mayo.events.model.AppointmentEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentEventProducer eventProducer;

    @Transactional
    public AppointmentResponse createAppointment(CreateAppointmentRequest request) {
        log.info("Creating appointment for patient: {}", request.getPatientId());

        Appointment appointment = Appointment.builder()
                .patientId(request.getPatientId())
                .familyMemberId(request.getFamilyMemberId())
                .providerId(request.getProviderId())
                .hospitalId(request.getHospitalId())
                .appointmentDate(request.getAppointmentDate())
                .appointmentTime(request.getAppointmentTime())
                .duration(request.getDuration())
                .type(request.getType())
                .status(AppointmentStatus.SCHEDULED)
                .notes(request.getNotes())
                .createdBy(request.getCreatedBy())
                .build();

        Appointment saved = appointmentRepository.save(appointment);
        log.info("Appointment created with ID: {}", saved.getId());

        // Publish event
        eventProducer.publishAppointmentEvent(mapToEvent(saved, AppointmentEvent.AppointmentEventType.CREATED));

        return mapToResponse(saved);
    }

    @Transactional(readOnly = true)
    public Optional<AppointmentResponse> getAppointmentById(UUID id) {
        log.debug("Fetching appointment by ID: {}", id);
        return appointmentRepository.findById(id).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllAppointments() {
        log.debug("Fetching all appointments");
        return appointmentRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByPatientId(UUID patientId) {
        log.debug("Fetching appointments for patient: {}", patientId);
        return appointmentRepository.findByPatientId(patientId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByFamilyMemberId(UUID familyMemberId) {
        log.debug("Fetching appointments for family member: {}", familyMemberId);
        return appointmentRepository.findByFamilyMemberId(familyMemberId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByProviderId(UUID providerId) {
        log.debug("Fetching appointments for provider: {}", providerId);
        return appointmentRepository.findByProviderId(providerId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByHospitalId(UUID hospitalId) {
        log.debug("Fetching appointments for hospital: {}", hospitalId);
        return appointmentRepository.findByHospitalId(hospitalId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByStatus(AppointmentStatus status) {
        log.debug("Fetching appointments with status: {}", status);
        return appointmentRepository.findByStatus(status).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsByDate(LocalDate date) {
        log.debug("Fetching appointments for date: {}", date);
        return appointmentRepository.findByAppointmentDate(date).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getUpcomingAppointmentsByPatient(UUID patientId) {
        log.info("Fetching upcoming appointments for patient: {}", patientId);
        return appointmentRepository.findByPatientIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                patientId, LocalDate.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getUpcomingAppointmentsByProvider(UUID providerId) {
        log.info("Fetching upcoming appointments for provider: {}", providerId);
        return appointmentRepository.findByProviderIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                providerId, LocalDate.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAllUpcomingAppointments() {
        log.info("Fetching all upcoming appointments");
        return appointmentRepository.findByAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(
                LocalDate.now()).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> getAppointmentsInDateRange(LocalDate startDate, LocalDate endDate) {
        log.info("Fetching appointments between {} and {}", startDate, endDate);
        return appointmentRepository.findByAppointmentDateBetween(startDate, endDate).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<AppointmentResponse> updateAppointment(UUID id, UpdateAppointmentRequest request) {
        log.info("Updating appointment: {}", id);

        return appointmentRepository.findById(id).map(appointment -> {
            if (request.getPatientId() != null) {
                appointment.setPatientId(request.getPatientId());
            }
            if (request.getFamilyMemberId() != null) {
                appointment.setFamilyMemberId(request.getFamilyMemberId());
            }
            if (request.getProviderId() != null) {
                appointment.setProviderId(request.getProviderId());
            }
            if (request.getHospitalId() != null) {
                appointment.setHospitalId(request.getHospitalId());
            }
            if (request.getAppointmentDate() != null) {
                appointment.setAppointmentDate(request.getAppointmentDate());
            }
            if (request.getAppointmentTime() != null) {
                appointment.setAppointmentTime(request.getAppointmentTime());
            }
            if (request.getDuration() != null) {
                appointment.setDuration(request.getDuration());
            }
            if (request.getType() != null) {
                appointment.setType(request.getType());
            }
            if (request.getNotes() != null) {
                appointment.setNotes(request.getNotes());
            }

            Appointment updated = appointmentRepository.save(appointment);
            log.info("Appointment updated: {}", updated.getId());

            // Publish event
            eventProducer.publishAppointmentEvent(mapToEvent(updated, AppointmentEvent.AppointmentEventType.UPDATED));

            return mapToResponse(updated);
        });
    }

    @Transactional
    public Optional<AppointmentResponse> updateAppointmentStatus(UUID id, AppointmentStatus status) {
        log.info("Updating appointment status: {} to {}", id, status);

        return appointmentRepository.findById(id).map(appointment -> {
            appointment.setStatus(status);
            Appointment updated = appointmentRepository.save(appointment);
            log.info("Appointment status updated: {}", updated.getId());

            // Publish event
            eventProducer.publishAppointmentEvent(mapToEvent(updated, AppointmentEvent.AppointmentEventType.STATUS_CHANGED));

            return mapToResponse(updated);
        });
    }

    @Transactional
    public boolean deleteAppointment(UUID id) {
        log.info("Deleting appointment: {}", id);

        if (appointmentRepository.existsById(id)) {
            appointmentRepository.deleteById(id);
            log.info("Appointment deleted: {}", id);
            return true;
        }

        log.warn("Appointment not found for deletion: {}", id);
        return false;
    }

    private AppointmentEvent mapToEvent(Appointment appointment, AppointmentEvent.AppointmentEventType eventType) {
        return AppointmentEvent.builder()
                .appointmentId(appointment.getId())
                .patientId(appointment.getPatientId())
                .providerId(appointment.getProviderId())
                .hospitalId(appointment.getHospitalId())
                .eventType(eventType)
                .appointmentDate(appointment.getAppointmentDate().toString())
                .appointmentTime(appointment.getAppointmentTime().toString())
                .status(appointment.getStatus().name())
                .build();
    }

    private AppointmentResponse mapToResponse(Appointment appointment) {
        return AppointmentResponse.builder()
                .id(appointment.getId())
                .patientId(appointment.getPatientId())
                .familyMemberId(appointment.getFamilyMemberId())
                .providerId(appointment.getProviderId())
                .hospitalId(appointment.getHospitalId())
                .appointmentDate(appointment.getAppointmentDate())
                .appointmentTime(appointment.getAppointmentTime())
                .duration(appointment.getDuration())
                .status(appointment.getStatus())
                .type(appointment.getType())
                .notes(appointment.getNotes())
                .createdBy(appointment.getCreatedBy())
                .createdAt(appointment.getCreatedAt())
                .updatedAt(appointment.getUpdatedAt())
                .build();
    }
}

package com.mayo.appointment.repository;

import com.mayo.appointment.entity.Appointment;
import com.mayo.appointment.entity.Appointment.AppointmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Repository
public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {

    List<Appointment> findByPatientId(UUID patientId);

    List<Appointment> findByFamilyMemberId(UUID familyMemberId);

    List<Appointment> findByProviderId(UUID providerId);

    List<Appointment> findByHospitalId(UUID hospitalId);

    List<Appointment> findByStatus(AppointmentStatus status);

    List<Appointment> findByAppointmentDate(LocalDate appointmentDate);

    List<Appointment> findByPatientIdAndStatus(UUID patientId, AppointmentStatus status);

    List<Appointment> findByAppointmentDateBetween(LocalDate startDate, LocalDate endDate);

    List<Appointment> findByPatientIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(UUID patientId, LocalDate date);

    List<Appointment> findByProviderIdAndAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(UUID providerId, LocalDate date);

    List<Appointment> findByHospitalIdAndAppointmentDateBetweenOrderByAppointmentDateAsc(UUID hospitalId, LocalDate startDate, LocalDate endDate);

    List<Appointment> findByAppointmentDateGreaterThanEqualOrderByAppointmentDateAsc(LocalDate date);
}

package com.mayo.patientrecord.service;

import com.mayo.patientrecord.dto.InitiateOwnershipTransferRequest;
import com.mayo.patientrecord.dto.OwnershipTransferDto;
import com.mayo.patientrecord.dto.OwnershipTransferHistoryDto;
import com.mayo.patientrecord.entity.OwnershipTransfer;
import com.mayo.patientrecord.entity.Patient;
import com.mayo.patientrecord.repository.*;
import com.mayo.common.core.exception.ResourceNotFoundException;
import com.mayo.common.core.exception.ForbiddenException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.kafka.core.KafkaTemplate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PatientOwnershipTransferServiceTest {

        @Mock
        private PatientRepository patientRepository;

        @Mock
        private PatientRecordRepository patientRecordRepository;

        @Mock
        private LabResultRepository labResultRepository;

        @Mock
        private ImagingStudyRepository imagingStudyRepository;

        @Mock
        private VitalSignsRepository vitalSignsRepository;

        @Mock
        private MedicationRepository medicationRepository;

        @Mock
        private AllergyRepository allergyRepository;

        @Mock
        private ActivityRepository activityRepository;

        @Mock
        private OwnershipTransferRepository ownershipTransferRepository;

        @Mock
        private KafkaTemplate<String, String> kafkaTemplate;

        @Mock
        private ObjectMapper objectMapper;

        @InjectMocks
        private PatientService patientService;

        private UUID patientId;
        private UUID currentOwnerId;
        private UUID newOwnerId;
        private UUID transferId;
        private Patient patient;
        private OwnershipTransfer transfer;

        @BeforeEach
        void setUp() {
                patientId = UUID.randomUUID();
                currentOwnerId = UUID.randomUUID();
                newOwnerId = UUID.randomUUID();
                transferId = UUID.randomUUID();

                patient = Patient.builder()
                                .id(patientId)
                                .medicalRecordNumber("MRN123456")
                                .firstName("John")
                                .lastName("Doe")
                                .ownerId(currentOwnerId)
                                .build();

                transfer = OwnershipTransfer.builder()
                                .id(transferId)
                                .patientId(patientId)
                                .previousOwnerId(currentOwnerId)
                                .newOwnerId(newOwnerId)
                                .status(OwnershipTransfer.TransferStatus.INITIATED)
                                .reason("Transfer to family member")
                                .initiatedBy(currentOwnerId)
                                .initiatedAt(LocalDateTime.now())
                                .build();
        }

        @Test
        void initiateOwnershipTransfer_ShouldCreateTransferSuccessfully() {
                // Given
                InitiateOwnershipTransferRequest request = new InitiateOwnershipTransferRequest(newOwnerId,
                                "Transfer to family member", true);
                when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
                when(ownershipTransferRepository.save(any(OwnershipTransfer.class))).thenReturn(transfer);

                // When
                OwnershipTransferDto result = patientService.initiateOwnershipTransfer(patientId, request);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getPatientId()).isEqualTo(patientId);
                assertThat(result.getPreviousOwnerId()).isEqualTo(currentOwnerId);
                assertThat(result.getNewOwnerId()).isEqualTo(newOwnerId);
                assertThat(result.getStatus()).isEqualTo("INITIATED");
                verify(ownershipTransferRepository).save(any(OwnershipTransfer.class));
        }

        @Test
        void initiateOwnershipTransfer_PatientNotFound_ShouldThrowException() {
                // Given
                InitiateOwnershipTransferRequest request = new InitiateOwnershipTransferRequest(newOwnerId,
                                "Transfer to family member", true);
                when(patientRepository.findById(patientId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> patientService.initiateOwnershipTransfer(patientId, request))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessage("Patient not found");
        }

        @Test
        void initiateOwnershipTransfer_UnauthorizedUser_ShouldThrowException() {
                // Given
                InitiateOwnershipTransferRequest request = new InitiateOwnershipTransferRequest(newOwnerId,
                                "Transfer to family member", true);
                when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));

                // When & Then
                assertThatThrownBy(() -> patientService.initiateOwnershipTransfer(patientId, request))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessage("Only the current owner can initiate transfer");
        }

        @Test
        void confirmOwnershipTransfer_ShouldCompleteTransferSuccessfully() {
                // Given
                OwnershipTransfer confirmedTransfer = OwnershipTransfer.builder()
                                .id(transferId)
                                .patientId(patientId)
                                .previousOwnerId(currentOwnerId)
                                .newOwnerId(newOwnerId)
                                .status(OwnershipTransfer.TransferStatus.CONFIRMED)
                                .confirmedBy(newOwnerId)
                                .confirmedAt(LocalDateTime.now())
                                .build();

                Patient updatedPatient = Patient.builder()
                                .id(patientId)
                                .ownerId(newOwnerId)
                                .build();

                when(ownershipTransferRepository.findById(transferId)).thenReturn(Optional.of(transfer));
                when(ownershipTransferRepository.save(any(OwnershipTransfer.class))).thenReturn(confirmedTransfer);
                when(patientRepository.findById(patientId)).thenReturn(Optional.of(patient));
                when(patientRepository.save(any(Patient.class))).thenReturn(updatedPatient);

                // When
                OwnershipTransferDto result = patientService.confirmOwnershipTransfer(transferId);

                // Then
                assertThat(result.getStatus()).isEqualTo("CONFIRMED");
                assertThat(result.getConfirmedBy()).isEqualTo(newOwnerId);
                verify(patientRepository).save(argThat(p -> p.getOwnerId().equals(newOwnerId)));
                verify(ownershipTransferRepository)
                                .save(argThat(t -> t.getStatus().equals(OwnershipTransfer.TransferStatus.COMPLETED)));
        }

        @Test
        void confirmOwnershipTransfer_TransferNotFound_ShouldThrowException() {
                // Given
                when(ownershipTransferRepository.findById(transferId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> patientService.confirmOwnershipTransfer(transferId))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessage("Transfer not found");
        }

        @Test
        void confirmOwnershipTransfer_UnauthorizedUser_ShouldThrowException() {
                // Given
                when(ownershipTransferRepository.findById(transferId)).thenReturn(Optional.of(transfer));

                // When & Then
                assertThatThrownBy(() -> patientService.confirmOwnershipTransfer(transferId))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessage("Only the new owner can confirm transfer");
        }

        @Test
        void confirmOwnershipTransfer_AlreadyCompleted_ShouldThrowException() {
                // Given
                OwnershipTransfer completedTransfer = OwnershipTransfer.builder()
                                .status(OwnershipTransfer.TransferStatus.COMPLETED)
                                .build();

                when(ownershipTransferRepository.findById(transferId)).thenReturn(Optional.of(completedTransfer));

                // When & Then
                assertThatThrownBy(() -> patientService.confirmOwnershipTransfer(transferId))
                                .isInstanceOf(RuntimeException.class)
                                .hasMessage("Transfer is not in initiated status");
        }

        @Test
        void rejectOwnershipTransfer_ShouldRejectTransferSuccessfully() {
                // Given
                OwnershipTransfer rejectedTransfer = OwnershipTransfer.builder()
                                .id(transferId)
                                .status(OwnershipTransfer.TransferStatus.REJECTED)
                                .build();

                when(ownershipTransferRepository.findById(transferId)).thenReturn(Optional.of(transfer));
                when(ownershipTransferRepository.save(any(OwnershipTransfer.class))).thenReturn(rejectedTransfer);

                // When
                OwnershipTransferDto result = patientService.rejectOwnershipTransfer(transferId);

                // Then
                assertThat(result.getStatus()).isEqualTo("REJECTED");
                verify(ownershipTransferRepository).save(any(OwnershipTransfer.class));
        }

}
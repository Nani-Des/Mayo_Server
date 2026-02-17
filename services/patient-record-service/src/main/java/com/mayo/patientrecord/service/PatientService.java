package com.mayo.patientrecord.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.events.topics.Topics;
import com.mayo.patientrecord.dto.*;
import com.mayo.patientrecord.entity.*;
import com.mayo.patientrecord.entity.Activity;
import com.mayo.patientrecord.entity.OwnershipTransfer;
import com.mayo.patientrecord.entity.Patient;
import com.mayo.patientrecord.entity.PatientRecord;
import com.mayo.patientrecord.entity.RecordType;
import com.mayo.patientrecord.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for patient operations
 */
@Service
@Slf4j
public class PatientService {

    private final PatientRepository patientRepository;
    private final PatientRecordRepository patientRecordRepository;
    private final LabResultRepository labResultRepository;
    private final ImagingStudyRepository imagingStudyRepository;
    private final VitalSignsRepository vitalSignsRepository;
    private final MedicationRepository medicationRepository;
    private final AllergyRepository allergyRepository;
    private final ActivityRepository activityRepository;
    private final OwnershipTransferRepository ownershipTransferRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public PatientService(PatientRepository patientRepository,
            PatientRecordRepository patientRecordRepository,
            LabResultRepository labResultRepository,
            ImagingStudyRepository imagingStudyRepository,
            VitalSignsRepository vitalSignsRepository,
            MedicationRepository medicationRepository,
            AllergyRepository allergyRepository,
            ActivityRepository activityRepository,
            OwnershipTransferRepository ownershipTransferRepository,
            KafkaTemplate<String, String> kafkaTemplate,
            ObjectMapper objectMapper) {
        this.patientRepository = patientRepository;
        this.patientRecordRepository = patientRecordRepository;
        this.labResultRepository = labResultRepository;
        this.imagingStudyRepository = imagingStudyRepository;
        this.vitalSignsRepository = vitalSignsRepository;
        this.medicationRepository = medicationRepository;
        this.allergyRepository = allergyRepository;
        this.activityRepository = activityRepository;
        this.ownershipTransferRepository = ownershipTransferRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Create a new patient
     */
    @Transactional
    public PatientDto createPatient(PatientDto patientDto) {
        log.info("Creating new patient: {}", patientDto.getFirstName() + " " + patientDto.getLastName());

        Patient patient = Patient.builder()
                .medicalRecordNumber(patientDto.getMedicalRecordNumber())
                .firstName(patientDto.getFirstName())
                .lastName(patientDto.getLastName())
                .email(patientDto.getEmail())
                .dateOfBirth(patientDto.getDateOfBirth())
                .gender(patientDto.getGender())
                .contactInfo(patientDto.getContactInfo())
                .familyMemberId(patientDto.getFamilyMemberId())
                .build();

        Patient savedPatient = patientRepository.save(patient);
        log.info("Patient created with ID: {}", savedPatient.getId());

        // Publish patient created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishPatientEvent(savedPatient.getId(), "PATIENT_CREATED", currentUserId,
            Map.of("patientId", savedPatient.getId(), "medicalRecordNumber", savedPatient.getMedicalRecordNumber()));

        return convertToDto(savedPatient);
    }

    /**
     * Get patient by ID
     */
    public Optional<PatientDto> getPatientById(UUID id) {
        log.info("Fetching patient with ID: {}", id);
        return patientRepository.findById(id).map(this::convertToDto);
    }

    /**
     * Get all patients
     */
    public List<PatientDto> getAllPatients() {
        log.info("Fetching all patients");
        return patientRepository.findAll().stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get patients by user ID (owner)
     */
    public List<PatientDto> getPatientsByUserId(UUID userId) {
        log.info("Fetching patients for user ID: {}", userId);
        return patientRepository.findByOwnerId(userId).stream()
                .map(this::convertToDto)
                .collect(Collectors.toList());
    }

    /**
     * Get patient by family member ID
     */
    public Optional<PatientDto> getPatientByFamilyMemberId(UUID familyMemberId) {
        log.info("Fetching patient for family member ID: {}", familyMemberId);
        return patientRepository.findByFamilyMemberId(familyMemberId).map(this::convertToDto);
    }

    /**
     * Update patient
     */
    @Transactional
    public Optional<PatientDto> updatePatient(UUID id, PatientDto patientDto) {
        log.info("Updating patient with ID: {}", id);

        return patientRepository.findById(id).map(patient -> {
            patient.setMedicalRecordNumber(patientDto.getMedicalRecordNumber());
            patient.setFirstName(patientDto.getFirstName());
            patient.setLastName(patientDto.getLastName());
            patient.setEmail(patientDto.getEmail());
            patient.setDateOfBirth(patientDto.getDateOfBirth());
            patient.setGender(patientDto.getGender());
            patient.setContactInfo(patientDto.getContactInfo());
            patient.setFamilyMemberId(patientDto.getFamilyMemberId());

            Patient updatedPatient = patientRepository.save(patient);

            // Publish patient updated event
            UUID currentUserId = getCurrentUserIdAsUUID();
            publishPatientEvent(updatedPatient.getId(), "PATIENT_UPDATED", currentUserId,
                Map.of("patientId", updatedPatient.getId()));

            return convertToDto(updatedPatient);
        });
    }

    /**
     * Delete patient
     */
    @Transactional
    public boolean deletePatient(UUID id) {
        log.info("Deleting patient with ID: {}", id);

        if (patientRepository.existsById(id)) {
            patientRepository.deleteById(id);
            return true;
        }
        return false;
    }

    private PatientDto convertToDto(Patient patient) {
        return PatientDto.builder()
                .id(patient.getId())
                .medicalRecordNumber(patient.getMedicalRecordNumber())
                .firstName(patient.getFirstName())
                .lastName(patient.getLastName())
                .email(patient.getEmail())
                .dateOfBirth(patient.getDateOfBirth())
                .gender(patient.getGender())
                .contactInfo(patient.getContactInfo())
                .ownerId(patient.getOwnerId())
                .familyMemberId(patient.getFamilyMemberId())
                .createdAt(patient.getCreatedAt())
                .updatedAt(patient.getUpdatedAt())
                .version(patient.getVersion())
                .build();
    }

    private PatientRecordDto convertToRecordDto(PatientRecord record) {
        return PatientRecordDto.builder()
                .id(record.getId())
                .patientId(record.getPatientId())
                .recordType(record.getRecordType())
                .title(record.getTitle())
                .description(record.getDescription())
                .metadata(record.getMetadata())
                .createdBy(record.getCreatedBy())
                .createdAt(record.getCreatedAt())
                .updatedAt(record.getUpdatedAt())
                .version(record.getVersion())
                .build();
    }

    // Conversion methods for new medical record types

    private LabResultDto convertToLabResultDto(LabResult labResult) {
        return LabResultDto.builder()
                .id(labResult.getId())
                .patientRecordId(labResult.getPatientRecordId())
                .testName(labResult.getTestName())
                .testCode(labResult.getTestCode())
                .category(labResult.getCategory())
                .value(labResult.getValue())
                .unit(labResult.getUnit())
                .referenceRange(labResult.getReferenceRange())
                .status(labResult.getStatus())
                .performedAt(labResult.getPerformedAt())
                .reportedAt(labResult.getReportedAt())
                .performingLab(labResult.getPerformingLab())
                .orderingProvider(labResult.getOrderingProvider())
                .interpretation(labResult.getInterpretation())
                .notes(labResult.getNotes())
                .createdAt(labResult.getCreatedAt())
                .updatedAt(labResult.getUpdatedAt())
                .version(labResult.getVersion())
                .build();
    }

    private ImagingStudyDto convertToImagingStudyDto(ImagingStudy imagingStudy) {
        return ImagingStudyDto.builder()
                .id(imagingStudy.getId())
                .patientRecordId(imagingStudy.getPatientRecordId())
                .studyType(imagingStudy.getStudyType())
                .modality(imagingStudy.getModality())
                .bodyPart(imagingStudy.getBodyPart())
                .studyDescription(imagingStudy.getStudyDescription())
                .status(imagingStudy.getStatus())
                .performedAt(imagingStudy.getPerformedAt())
                .reportedAt(imagingStudy.getReportedAt())
                .performingFacility(imagingStudy.getPerformingFacility())
                .orderingProvider(imagingStudy.getOrderingProvider())
                .interpretingProvider(imagingStudy.getInterpretingProvider())
                .findings(imagingStudy.getFindings())
                .impression(imagingStudy.getImpression())
                .recommendations(imagingStudy.getRecommendations())
                .accessionNumber(imagingStudy.getAccessionNumber())
                .studyInstanceUid(imagingStudy.getStudyInstanceUid())
                .imageCount(imagingStudy.getImageCount())
                .radiationDose(imagingStudy.getRadiationDose())
                .contrastUsed(imagingStudy.getContrastUsed())
                .notes(imagingStudy.getNotes())
                .createdAt(imagingStudy.getCreatedAt())
                .updatedAt(imagingStudy.getUpdatedAt())
                .version(imagingStudy.getVersion())
                .build();
    }

    private VitalSignsDto convertToVitalSignsDto(VitalSigns vitalSigns) {
        return VitalSignsDto.builder()
                .id(vitalSigns.getId())
                .patientRecordId(vitalSigns.getPatientRecordId())
                .recordedAt(vitalSigns.getRecordedAt())
                .recordedBy(vitalSigns.getRecordedBy())
                .location(vitalSigns.getLocation())
                .temperature(vitalSigns.getTemperature())
                .temperatureUnit(vitalSigns.getTemperatureUnit())
                .heartRate(vitalSigns.getHeartRate())
                .respiratoryRate(vitalSigns.getRespiratoryRate())
                .systolicBp(vitalSigns.getSystolicBp())
                .diastolicBp(vitalSigns.getDiastolicBp())
                .oxygenSaturation(vitalSigns.getOxygenSaturation())
                .oxygenSupplement(vitalSigns.getOxygenSupplement())
                .weightKg(vitalSigns.getWeightKg())
                .heightCm(vitalSigns.getHeightCm())
                .bmi(vitalSigns.getBmi())
                .painScale(vitalSigns.getPainScale())
                .glucoseMgDl(vitalSigns.getGlucoseMgDl())
                .position(vitalSigns.getPosition())
                .method(vitalSigns.getMethod())
                .notes(vitalSigns.getNotes())
                .createdAt(vitalSigns.getCreatedAt())
                .updatedAt(vitalSigns.getUpdatedAt())
                .version(vitalSigns.getVersion())
                .build();
    }

    private MedicationDto convertToMedicationDto(Medication medication) {
        return MedicationDto.builder()
                .id(medication.getId())
                .patientRecordId(medication.getPatientRecordId())
                .medicationName(medication.getMedicationName())
                .genericName(medication.getGenericName())
                .brandName(medication.getBrandName())
                .strength(medication.getStrength())
                .form(medication.getForm())
                .dosage(medication.getDosage())
                .frequency(medication.getFrequency())
                .route(medication.getRoute())
                .quantity(medication.getQuantity())
                .refills(medication.getRefills())
                .prescribingProvider(medication.getPrescribingProvider())
                .prescribedAt(medication.getPrescribedAt())
                .startedAt(medication.getStartedAt())
                .endedAt(medication.getEndedAt())
                .status(medication.getStatus())
                .indication(medication.getIndication())
                .instructions(medication.getInstructions())
                .sideEffects(medication.getSideEffects())
                .interactions(medication.getInteractions())
                .cost(medication.getCost())
                .insuranceCovered(medication.getInsuranceCovered())
                .pharmacy(medication.getPharmacy())
                .notes(medication.getNotes())
                .createdAt(medication.getCreatedAt())
                .updatedAt(medication.getUpdatedAt())
                .version(medication.getVersion())
                .build();
    }

    private AllergyDto convertToAllergyDto(Allergy allergy) {
        return AllergyDto.builder()
                .id(allergy.getId())
                .patientRecordId(allergy.getPatientRecordId())
                .allergen(allergy.getAllergen())
                .allergenType(allergy.getAllergenType())
                .reactionSeverity(allergy.getReactionSeverity())
                .reactionDescription(allergy.getReactionDescription())
                .symptoms(allergy.getSymptoms())
                .onsetDate(allergy.getOnsetDate())
                .reportedDate(allergy.getReportedDate())
                .reportedBy(allergy.getReportedBy())
                .status(allergy.getStatus())
                .verificationStatus(allergy.getVerificationStatus())
                .verificationDate(allergy.getVerificationDate())
                .verifiedBy(allergy.getVerifiedBy())
                .treatment(allergy.getTreatment())
                .notes(allergy.getNotes())
                .createdAt(allergy.getCreatedAt())
                .updatedAt(allergy.getUpdatedAt())
                .version(allergy.getVersion())
                .build();
    }

    private String getCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }

    private UUID getCurrentUserIdAsUUID() {
        String userIdStr = getCurrentUserId();
        if (userIdStr != null && !userIdStr.equals("system")) {
            try {
                return UUID.fromString(userIdStr);
            } catch (Exception e) {
                log.warn("Invalid userId format: {}", userIdStr);
            }
        }
        return null;
    }

    private UUID getCurrentDeviceId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
            String deviceId = (String) claims.get("deviceId");
            if (deviceId != null) {
                try {
                    return UUID.fromString(deviceId);
                } catch (Exception e) {
                    log.warn("Invalid deviceId format: {}", deviceId);
                }
            }
        }
        return null;
    }

    private UUID getCurrentHospitalId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getDetails() instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> claims = (Map<String, Object>) authentication.getDetails();
            String hospitalId = (String) claims.get("hospitalId");
            if (hospitalId != null) {
                try {
                    return UUID.fromString(hospitalId);
                } catch (Exception e) {
                    log.warn("Invalid hospitalId format: {}", hospitalId);
                }
            }
        }
        return null;
    }

    @Async
    public void logActivity(UUID patientId, UUID recordId, RecordType recordType, Activity.Action action) {
        if (recordType != RecordType.PRESCRIPTION && recordType != RecordType.DIAGNOSIS) {
            return; // Only track PRESCRIPTION and DIAGNOSIS
        }

        try {
            Activity activity = Activity.builder()
                    .patientId(patientId)
                    .recordId(recordId)
                    .recordType(recordType)
                    .action(action)
                    .timestamp(LocalDateTime.now())
                    .userId(getCurrentUserId())
                    .deviceId(getCurrentDeviceId())
                    .hospitalId(getCurrentHospitalId())
                    .build();

            activityRepository.save(activity);

            // Publish to Kafka
            ActivityDto activityDto = ActivityDto.builder()
                    .id(activity.getId())
                    .patientId(activity.getPatientId())
                    .recordId(activity.getRecordId())
                    .recordType(activity.getRecordType())
                    .action(activity.getAction())
                    .timestamp(activity.getTimestamp())
                    .userId(activity.getUserId())
                    .deviceId(activity.getDeviceId())
                    .hospitalId(activity.getHospitalId())
                    .createdAt(activity.getCreatedAt())
                    .build();

            String message = objectMapper.writeValueAsString(activityDto);
            kafkaTemplate.send(Topics.AUDIT_EVENTS, recordId.toString(), message);

            log.debug("Logged activity: {} for record: {}", action, recordId);
        } catch (Exception e) {
            log.error("Failed to log activity: {}", e.getMessage(), e);
        }
    }

    // Patient Record operations

    @Transactional
    public PatientRecordDto createPatientRecord(PatientRecordDto recordDto) {
        log.info("Creating patient record for patient: {}", recordDto.getPatientId());

        PatientRecord record = PatientRecord.builder()
                .patientId(recordDto.getPatientId())
                .recordType(recordDto.getRecordType())
                .title(recordDto.getTitle())
                .description(recordDto.getDescription())
                .metadata(recordDto.getMetadata())
                .createdBy(getCurrentUserId())
                .build();

        PatientRecord savedRecord = patientRecordRepository.save(record);
        logActivity(savedRecord.getPatientId(), savedRecord.getId(), savedRecord.getRecordType(),
                Activity.Action.CREATE);

        return convertToRecordDto(savedRecord);
    }

    public Optional<PatientRecordDto> getPatientRecordById(UUID id) {
        log.info("Fetching patient record with ID: {}", id);
        Optional<PatientRecord> record = patientRecordRepository.findById(id);
        record.ifPresent(r -> logActivity(r.getPatientId(), r.getId(), r.getRecordType(), Activity.Action.VIEW));
        return record.map(this::convertToRecordDto);
    }

    public List<PatientRecordDto> getPatientRecordsByPatientId(UUID patientId) {
        log.info("Fetching patient records for patient: {}", patientId);
        return patientRecordRepository.findByPatientId(patientId).stream()
                .map(this::convertToRecordDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public Optional<PatientRecordDto> updatePatientRecord(UUID id, PatientRecordDto recordDto) {
        log.info("Updating patient record with ID: {}", id);

        return patientRecordRepository.findById(id).map(record -> {
            record.setTitle(recordDto.getTitle());
            record.setDescription(recordDto.getDescription());
            record.setMetadata(recordDto.getMetadata());

            PatientRecord updatedRecord = patientRecordRepository.save(record);
            logActivity(updatedRecord.getPatientId(), updatedRecord.getId(), updatedRecord.getRecordType(),
                    Activity.Action.UPDATE);
            return convertToRecordDto(updatedRecord);
        });
    }

    @Transactional
    public boolean deletePatientRecord(UUID id) {
        log.info("Deleting patient record with ID: {}", id);

        Optional<PatientRecord> record = patientRecordRepository.findById(id);
        if (record.isPresent()) {
            patientRecordRepository.deleteById(id);
            logActivity(record.get().getPatientId(), record.get().getId(), record.get().getRecordType(),
                    Activity.Action.DELETE);
            return true;
        }
        return false;
    }

    // Lab Result methods

    @Transactional
    public LabResultDto createLabResult(LabResultDto labResultDto) {
        log.info("Creating lab result for patient record: {}", labResultDto.getPatientRecordId());

        LabResult labResult = LabResult.builder()
                .patientRecordId(labResultDto.getPatientRecordId())
                .testName(labResultDto.getTestName())
                .testCode(labResultDto.getTestCode())
                .category(labResultDto.getCategory())
                .value(labResultDto.getValue())
                .unit(labResultDto.getUnit())
                .referenceRange(labResultDto.getReferenceRange())
                .status(labResultDto.getStatus() != null ? labResultDto.getStatus() : LabResult.LabStatus.PENDING)
                .performedAt(labResultDto.getPerformedAt())
                .reportedAt(labResultDto.getReportedAt())
                .performingLab(labResultDto.getPerformingLab())
                .orderingProvider(labResultDto.getOrderingProvider())
                .interpretation(labResultDto.getInterpretation())
                .notes(labResultDto.getNotes())
                .build();

        LabResult savedLabResult = labResultRepository.save(labResult);

        // Publish medical record created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishMedicalRecordEvent(savedLabResult.getId(), "LAB_RESULT_ADDED", currentUserId,
            Map.of("recordId", savedLabResult.getId(), "patientRecordId", savedLabResult.getPatientRecordId(),
                   "testName", savedLabResult.getTestName()));

        return convertToLabResultDto(savedLabResult);
    }

    public List<LabResultDto> getLabResultsByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching lab results for patient record: {}", patientRecordId);
        return labResultRepository.findByPatientRecordIdOrderByPerformedAtDesc(patientRecordId)
                .stream()
                .map(this::convertToLabResultDto)
                .collect(Collectors.toList());
    }

    public Optional<LabResultDto> getLabResultById(UUID id) {
        log.info("Fetching lab result with ID: {}", id);
        return labResultRepository.findById(id).map(this::convertToLabResultDto);
    }

    @Transactional
    public Optional<LabResultDto> updateLabResult(UUID id, LabResultDto labResultDto) {
        log.info("Updating lab result with ID: {}", id);

        return labResultRepository.findById(id).map(labResult -> {
            labResult.setTestName(labResultDto.getTestName());
            labResult.setTestCode(labResultDto.getTestCode());
            labResult.setCategory(labResultDto.getCategory());
            labResult.setValue(labResultDto.getValue());
            labResult.setUnit(labResultDto.getUnit());
            labResult.setReferenceRange(labResultDto.getReferenceRange());
            labResult.setStatus(labResultDto.getStatus());
            labResult.setPerformedAt(labResultDto.getPerformedAt());
            labResult.setReportedAt(labResultDto.getReportedAt());
            labResult.setPerformingLab(labResultDto.getPerformingLab());
            labResult.setOrderingProvider(labResultDto.getOrderingProvider());
            labResult.setInterpretation(labResultDto.getInterpretation());
            labResult.setNotes(labResultDto.getNotes());

            LabResult updatedLabResult = labResultRepository.save(labResult);

            // Publish medical record updated event
            UUID currentUserId = getCurrentUserIdAsUUID();
            publishMedicalRecordEvent(updatedLabResult.getId(), "LAB_RESULT_UPDATED", currentUserId,
                Map.of("recordId", updatedLabResult.getId(), "patientRecordId", updatedLabResult.getPatientRecordId()));

            return convertToLabResultDto(updatedLabResult);
        });
    }

    @Transactional
    public boolean deleteLabResult(UUID id) {
        log.info("Deleting lab result with ID: {}", id);

        return labResultRepository.findById(id).map(labResult -> {
            labResultRepository.delete(labResult);

            // Publish medical record deleted event
            UUID currentUserId = getCurrentUserIdAsUUID();
            publishMedicalRecordEvent(labResult.getId(), "LAB_RESULT_DELETED", currentUserId,
                Map.of("recordId", labResult.getId(), "patientRecordId", labResult.getPatientRecordId()));

            return true;
        }).orElse(false);
    }

    public List<LabResultDto> getLabResultsByPatientId(UUID patientId) {
        log.info("Fetching lab results for patient: {}", patientId);
        List<UUID> recordIds = patientRecordRepository.findByPatientIdAndRecordType(patientId, RecordType.LAB_RESULT)
                .stream()
                .map(PatientRecord::getId)
                .collect(Collectors.toList());

        if (recordIds.isEmpty()) {
            return List.of();
        }

        return labResultRepository.findByPatientRecordIdInOrderByPerformedAtDesc(recordIds)
                .stream()
                .map(this::convertToLabResultDto)
                .collect(Collectors.toList());
    }

    // Imaging Study methods

    @Transactional
    public ImagingStudyDto createImagingStudy(ImagingStudyDto imagingStudyDto) {
        log.info("Creating imaging study for patient record: {}", imagingStudyDto.getPatientRecordId());

        ImagingStudy imagingStudy = ImagingStudy.builder()
                .patientRecordId(imagingStudyDto.getPatientRecordId())
                .studyType(imagingStudyDto.getStudyType())
                .modality(imagingStudyDto.getModality())
                .bodyPart(imagingStudyDto.getBodyPart())
                .studyDescription(imagingStudyDto.getStudyDescription())
                .status(imagingStudyDto.getStatus() != null ? imagingStudyDto.getStatus()
                        : ImagingStudy.StudyStatus.SCHEDULED)
                .performedAt(imagingStudyDto.getPerformedAt())
                .reportedAt(imagingStudyDto.getReportedAt())
                .performingFacility(imagingStudyDto.getPerformingFacility())
                .orderingProvider(imagingStudyDto.getOrderingProvider())
                .interpretingProvider(imagingStudyDto.getInterpretingProvider())
                .findings(imagingStudyDto.getFindings())
                .impression(imagingStudyDto.getImpression())
                .recommendations(imagingStudyDto.getRecommendations())
                .accessionNumber(imagingStudyDto.getAccessionNumber())
                .studyInstanceUid(imagingStudyDto.getStudyInstanceUid())
                .imageCount(imagingStudyDto.getImageCount())
                .radiationDose(imagingStudyDto.getRadiationDose())
                .contrastUsed(imagingStudyDto.getContrastUsed())
                .notes(imagingStudyDto.getNotes())
                .build();

        ImagingStudy savedImagingStudy = imagingStudyRepository.save(imagingStudy);

        // Publish medical record created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishMedicalRecordEvent(savedImagingStudy.getId(), "IMAGING_STUDY_ADDED", currentUserId,
            Map.of("recordId", savedImagingStudy.getId(), "patientRecordId", savedImagingStudy.getPatientRecordId(),
                   "studyType", savedImagingStudy.getStudyType()));

        return convertToImagingStudyDto(savedImagingStudy);
    }

    public List<ImagingStudyDto> getImagingStudiesByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching imaging studies for patient record: {}", patientRecordId);
        return imagingStudyRepository.findByPatientRecordIdOrderByPerformedAtDesc(patientRecordId)
                .stream()
                .map(this::convertToImagingStudyDto)
                .collect(Collectors.toList());
    }

    public List<ImagingStudyDto> getImagingStudiesByPatientId(UUID patientId) {
        log.info("Fetching imaging studies for patient: {}", patientId);
        List<UUID> recordIds = patientRecordRepository.findByPatientIdAndRecordType(patientId, RecordType.IMAGING_STUDY)
                .stream()
                .map(PatientRecord::getId)
                .collect(Collectors.toList());

        if (recordIds.isEmpty()) {
            return List.of();
        }

        return imagingStudyRepository.findByPatientRecordIdInOrderByPerformedAtDesc(recordIds)
                .stream()
                .map(this::convertToImagingStudyDto)
                .collect(Collectors.toList());
    }

    // Vital Signs methods

    @Transactional
    public VitalSignsDto createVitalSigns(VitalSignsDto vitalSignsDto) {
        log.info("Creating vital signs for patient record: {}", vitalSignsDto.getPatientRecordId());

        VitalSigns vitalSigns = VitalSigns.builder()
                .patientRecordId(vitalSignsDto.getPatientRecordId())
                .recordedAt(vitalSignsDto.getRecordedAt())
                .recordedBy(vitalSignsDto.getRecordedBy() != null ? vitalSignsDto.getRecordedBy() : getCurrentUserId())
                .location(vitalSignsDto.getLocation())
                .temperature(vitalSignsDto.getTemperature())
                .temperatureUnit(vitalSignsDto.getTemperatureUnit() != null ? vitalSignsDto.getTemperatureUnit()
                        : VitalSigns.TemperatureUnit.CELSIUS)
                .heartRate(vitalSignsDto.getHeartRate())
                .respiratoryRate(vitalSignsDto.getRespiratoryRate())
                .systolicBp(vitalSignsDto.getSystolicBp())
                .diastolicBp(vitalSignsDto.getDiastolicBp())
                .oxygenSaturation(vitalSignsDto.getOxygenSaturation())
                .oxygenSupplement(vitalSignsDto.getOxygenSupplement())
                .weightKg(vitalSignsDto.getWeightKg())
                .heightCm(vitalSignsDto.getHeightCm())
                .bmi(vitalSignsDto.getBmi())
                .painScale(vitalSignsDto.getPainScale())
                .glucoseMgDl(vitalSignsDto.getGlucoseMgDl())
                .position(vitalSignsDto.getPosition())
                .method(vitalSignsDto.getMethod())
                .notes(vitalSignsDto.getNotes())
                .build();

        VitalSigns savedVitalSigns = vitalSignsRepository.save(vitalSigns);

        // Publish medical record created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishMedicalRecordEvent(savedVitalSigns.getId(), "VITAL_SIGNS_RECORDED", currentUserId,
            Map.of("recordId", savedVitalSigns.getId(), "patientRecordId", savedVitalSigns.getPatientRecordId()));

        return convertToVitalSignsDto(savedVitalSigns);
    }

    public List<VitalSignsDto> getVitalSignsByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching vital signs for patient record: {}", patientRecordId);
        return vitalSignsRepository.findByPatientRecordIdOrderByRecordedAtDesc(patientRecordId)
                .stream()
                .map(this::convertToVitalSignsDto)
                .collect(Collectors.toList());
    }

    public List<VitalSignsDto> getVitalSignsByPatientId(UUID patientId) {
        log.info("Fetching vital signs for patient: {}", patientId);
        List<UUID> recordIds = patientRecordRepository.findByPatientIdAndRecordType(patientId, RecordType.VITAL_SIGNS)
                .stream()
                .map(PatientRecord::getId)
                .collect(Collectors.toList());

        if (recordIds.isEmpty()) {
            return List.of();
        }

        return vitalSignsRepository.findByPatientRecordIdInOrderByRecordedAtDesc(recordIds)
                .stream()
                .map(this::convertToVitalSignsDto)
                .collect(Collectors.toList());
    }

    // Medication methods

    @Transactional
    public MedicationDto createMedication(MedicationDto medicationDto) {
        log.info("Creating medication for patient record: {}", medicationDto.getPatientRecordId());

        Medication medication = Medication.builder()
                .patientRecordId(medicationDto.getPatientRecordId())
                .medicationName(medicationDto.getMedicationName())
                .genericName(medicationDto.getGenericName())
                .brandName(medicationDto.getBrandName())
                .strength(medicationDto.getStrength())
                .form(medicationDto.getForm())
                .dosage(medicationDto.getDosage())
                .frequency(medicationDto.getFrequency())
                .route(medicationDto.getRoute())
                .quantity(medicationDto.getQuantity())
                .refills(medicationDto.getRefills())
                .prescribingProvider(medicationDto.getPrescribingProvider())
                .prescribedAt(medicationDto.getPrescribedAt())
                .startedAt(medicationDto.getStartedAt())
                .endedAt(medicationDto.getEndedAt())
                .status(medicationDto.getStatus() != null ? medicationDto.getStatus()
                        : Medication.MedicationStatus.PRESCRIBED)
                .indication(medicationDto.getIndication())
                .instructions(medicationDto.getInstructions())
                .sideEffects(medicationDto.getSideEffects())
                .interactions(medicationDto.getInteractions())
                .cost(medicationDto.getCost())
                .insuranceCovered(medicationDto.getInsuranceCovered())
                .pharmacy(medicationDto.getPharmacy())
                .notes(medicationDto.getNotes())
                .build();

        Medication savedMedication = medicationRepository.save(medication);

        // Publish medical record created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishMedicalRecordEvent(savedMedication.getId(), "MEDICATION_PRESCRIBED", currentUserId,
            Map.of("recordId", savedMedication.getId(), "patientRecordId", savedMedication.getPatientRecordId(),
                   "medicationName", savedMedication.getMedicationName()));

        return convertToMedicationDto(savedMedication);
    }

    public List<MedicationDto> getMedicationsByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching medications for patient record: {}", patientRecordId);
        return medicationRepository.findByPatientRecordIdOrderByPrescribedAtDesc(patientRecordId)
                .stream()
                .map(this::convertToMedicationDto)
                .collect(Collectors.toList());
    }

    public List<MedicationDto> getActiveMedicationsByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching active medications for patient record: {}", patientRecordId);
        return medicationRepository.findActiveMedicationsByPatientRecordId(patientRecordId, LocalDateTime.now())
                .stream()
                .map(this::convertToMedicationDto)
                .collect(Collectors.toList());
    }

    public List<MedicationDto> getMedicationsByPatientId(UUID patientId) {
        log.info("Fetching medications for patient: {}", patientId);
        List<UUID> recordIds = patientRecordRepository.findByPatientIdAndRecordType(patientId, RecordType.MEDICATION)
                .stream()
                .map(PatientRecord::getId)
                .collect(Collectors.toList());

        if (recordIds.isEmpty()) {
            return List.of();
        }

        return medicationRepository.findByPatientRecordIdInOrderByPrescribedAtDesc(recordIds)
                .stream()
                .map(this::convertToMedicationDto)
                .collect(Collectors.toList());
    }

    // Allergy methods

    @Transactional
    public AllergyDto createAllergy(AllergyDto allergyDto) {
        log.info("Creating allergy for patient record: {}", allergyDto.getPatientRecordId());

        Allergy allergy = Allergy.builder()
                .patientRecordId(allergyDto.getPatientRecordId())
                .allergen(allergyDto.getAllergen())
                .allergenType(allergyDto.getAllergenType())
                .reactionSeverity(allergyDto.getReactionSeverity())
                .reactionDescription(allergyDto.getReactionDescription())
                .symptoms(allergyDto.getSymptoms())
                .onsetDate(allergyDto.getOnsetDate())
                .reportedDate(allergyDto.getReportedDate())
                .reportedBy(allergyDto.getReportedBy() != null ? allergyDto.getReportedBy() : getCurrentUserId())
                .status(allergyDto.getStatus() != null ? allergyDto.getStatus() : Allergy.AllergyStatus.ACTIVE)
                .verificationStatus(allergyDto.getVerificationStatus())
                .verificationDate(allergyDto.getVerificationDate())
                .verifiedBy(allergyDto.getVerifiedBy())
                .treatment(allergyDto.getTreatment())
                .notes(allergyDto.getNotes())
                .build();

        Allergy savedAllergy = allergyRepository.save(allergy);

        // Publish medical record created event
        UUID currentUserId = getCurrentUserIdAsUUID();
        publishMedicalRecordEvent(savedAllergy.getId(), "ALLERGY_RECORDED", currentUserId,
            Map.of("recordId", savedAllergy.getId(), "patientRecordId", savedAllergy.getPatientRecordId(),
                   "allergen", savedAllergy.getAllergen()));

        return convertToAllergyDto(savedAllergy);
    }

    public List<AllergyDto> getAllergiesByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching allergies for patient record: {}", patientRecordId);
        return allergyRepository.findByPatientRecordIdOrderByReportedDateDesc(patientRecordId)
                .stream()
                .map(this::convertToAllergyDto)
                .collect(Collectors.toList());
    }

    public List<AllergyDto> getActiveAllergiesByPatientRecordId(UUID patientRecordId) {
        log.info("Fetching active allergies for patient record: {}", patientRecordId);
        return allergyRepository.findActiveAllergiesByPatientRecordId(patientRecordId)
                .stream()
                .map(this::convertToAllergyDto)
                .collect(Collectors.toList());
    }

    public List<AllergyDto> getAllergiesByPatientId(UUID patientId) {
        log.info("Fetching allergies for patient: {}", patientId);
        List<UUID> recordIds = patientRecordRepository.findByPatientIdAndRecordType(patientId, RecordType.ALLERGY)
                .stream()
                .map(PatientRecord::getId)
                .collect(Collectors.toList());

        if (recordIds.isEmpty()) {
            return List.of();
        }

        return allergyRepository.findByPatientRecordIdInOrderByReportedDateDesc(recordIds)
                .stream()
                .map(this::convertToAllergyDto)
                .collect(Collectors.toList());
    }

    // Ownership Transfer methods

    /**
     * Initiate ownership transfer
     */
    @Transactional
    public OwnershipTransferDto initiateOwnershipTransfer(UUID patientId, InitiateOwnershipTransferRequest request) {
        log.info("Initiating ownership transfer for patient: {} to new owner: {}", patientId, request.getNewOwnerId());

        // Get current patient
        Patient patient = patientRepository.findById(patientId)
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        // Validate current user is the owner
        UUID currentUserId = getCurrentUserIdAsUUID();
        if (currentUserId == null || !currentUserId.equals(patient.getOwnerId())) {
            throw new RuntimeException("Only the current owner can initiate transfer");
        }

        // Check for existing initiated transfers
        List<OwnershipTransfer> pendingTransfers = ownershipTransferRepository
                .findByPatientIdAndStatus(patientId, OwnershipTransfer.TransferStatus.INITIATED);
        if (!pendingTransfers.isEmpty()) {
            throw new RuntimeException("Patient already has an initiated transfer");
        }

        // Create transfer record
        OwnershipTransfer transfer = OwnershipTransfer.builder()
                .patientId(patientId)
                .previousOwnerId(patient.getOwnerId())
                .newOwnerId(request.getNewOwnerId())
                .status(OwnershipTransfer.TransferStatus.INITIATED)
                .reason(request.getReason())
                .initiatedBy(currentUserId)
                .build();

        OwnershipTransfer savedTransfer = ownershipTransferRepository.save(transfer);

        // If no confirmation required, complete the transfer immediately
        if (!request.getConfirmationRequired()) {
            completeOwnershipTransfer(savedTransfer.getId());
        }

        // Publish audit event
        publishOwnershipTransferEvent(savedTransfer, "OWNERSHIP_TRANSFER_INITIATED");

        return convertToTransferDto(savedTransfer);
    }

    /**
     * Confirm ownership transfer
     */
    @Transactional
    public OwnershipTransferDto confirmOwnershipTransfer(UUID transferId) {
        log.info("Confirming ownership transfer: {}", transferId);

        OwnershipTransfer transfer = ownershipTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));

        if (transfer.getStatus() != OwnershipTransfer.TransferStatus.INITIATED) {
            throw new RuntimeException("Transfer is not in initiated status");
        }

        // Validate current user is the new owner
        UUID currentUserId = getCurrentUserIdAsUUID();
        if (currentUserId == null || !currentUserId.equals(transfer.getNewOwnerId())) {
            throw new RuntimeException("Only the new owner can confirm transfer");
        }

        transfer.setStatus(OwnershipTransfer.TransferStatus.CONFIRMED);
        transfer.setConfirmedBy(currentUserId);
        transfer.setConfirmedAt(LocalDateTime.now());

        OwnershipTransfer updatedTransfer = ownershipTransferRepository.save(transfer);

        // Complete the transfer
        completeOwnershipTransfer(transferId);

        return convertToTransferDto(updatedTransfer);
    }

    /**
     * Complete ownership transfer
     */
    private void completeOwnershipTransfer(UUID transferId) {
        OwnershipTransfer transfer = ownershipTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));

        // Update patient ownership
        Patient patient = patientRepository.findById(transfer.getPatientId())
                .orElseThrow(() -> new RuntimeException("Patient not found"));

        patient.setOwnerId(transfer.getNewOwnerId());
        patientRepository.save(patient);

        // Update transfer status
        transfer.setStatus(OwnershipTransfer.TransferStatus.COMPLETED);
        transfer.setCompletedAt(LocalDateTime.now());
        ownershipTransferRepository.save(transfer);

        // Publish completion event
        publishOwnershipTransferEvent(transfer, "OWNERSHIP_TRANSFER_COMPLETED");
    }

    /**
     * Reject ownership transfer
     */
    @Transactional
    public OwnershipTransferDto rejectOwnershipTransfer(UUID transferId) {
        log.info("Rejecting ownership transfer: {}", transferId);

        OwnershipTransfer transfer = ownershipTransferRepository.findById(transferId)
                .orElseThrow(() -> new RuntimeException("Transfer not found"));

        if (transfer.getStatus() != OwnershipTransfer.TransferStatus.INITIATED) {
            throw new RuntimeException("Transfer is not in initiated status");
        }

        // Validate current user is the new owner
        UUID currentUserId = getCurrentUserIdAsUUID();
        if (currentUserId == null || !currentUserId.equals(transfer.getNewOwnerId())) {
            throw new RuntimeException("Only the new owner can reject transfer");
        }

        transfer.setStatus(OwnershipTransfer.TransferStatus.REJECTED);
        OwnershipTransfer updatedTransfer = ownershipTransferRepository.save(transfer);

        // Publish rejection event
        publishOwnershipTransferEvent(transfer, "OWNERSHIP_TRANSFER_REJECTED");

        return convertToTransferDto(updatedTransfer);
    }

    /**
     * Get ownership transfer history for a patient
     */
    public List<OwnershipTransferHistoryDto> getOwnershipTransferHistory(UUID patientId) {
        log.info("Getting ownership transfer history for patient: {}", patientId);

        return ownershipTransferRepository.findByPatientIdOrderByInitiatedAtDesc(patientId)
                .stream()
                .map(this::convertToHistoryDto)
                .collect(Collectors.toList());
    }

    private OwnershipTransferDto convertToTransferDto(OwnershipTransfer transfer) {
        return OwnershipTransferDto.builder() // The method builder() is undefined for the type PatientDto
                .id(transfer.getId())
                .patientId(transfer.getPatientId())
                .previousOwnerId(transfer.getPreviousOwnerId())
                .newOwnerId(transfer.getNewOwnerId())
                .status(transfer.getStatus().name())
                .reason(transfer.getReason())
                .initiatedBy(transfer.getInitiatedBy())
                .confirmedBy(transfer.getConfirmedBy())
                .initiatedAt(transfer.getInitiatedAt())
                .confirmedAt(transfer.getConfirmedAt())
                .completedAt(transfer.getCompletedAt())
                .createdAt(transfer.getCreatedAt())
                .updatedAt(transfer.getUpdatedAt())
                .version(transfer.getVersion())
                .build();
    }

    private OwnershipTransferHistoryDto convertToHistoryDto(OwnershipTransfer transfer) {
        return OwnershipTransferHistoryDto.builder()
                .transferId(transfer.getId())
                .previousOwnerId(transfer.getPreviousOwnerId())
                .newOwnerId(transfer.getNewOwnerId())
                .transferredAt(
                        transfer.getCompletedAt() != null ? transfer.getCompletedAt() : transfer.getInitiatedAt())
                .reason(transfer.getReason())
                .status(transfer.getStatus().name())
                .build();
    }

    private void publishOwnershipTransferEvent(OwnershipTransfer transfer, String eventType) {
        try {
            // Create audit event data
            String eventData = objectMapper.writeValueAsString(Map.of(
                    "transferId", transfer.getId(),
                    "patientId", transfer.getPatientId(),
                    "previousOwnerId", transfer.getPreviousOwnerId(),
                    "newOwnerId", transfer.getNewOwnerId(),
                    "status", transfer.getStatus(),
                    "reason", transfer.getReason(),
                    "initiatedBy", transfer.getInitiatedBy(),
                    "initiatedAt", transfer.getInitiatedAt()));

            kafkaTemplate.send(Topics.AUDIT_EVENTS, transfer.getId().toString(), eventData);
            log.debug("Published ownership transfer event: {} for transfer: {}", eventType, transfer.getId());
        } catch (Exception e) {
            log.error("Failed to publish ownership transfer event: {}", e.getMessage(), e);
        }
    }

    /**
     * Publish patient event to Kafka
     */
    private void publishPatientEvent(UUID patientId, String eventType, UUID actorId, Map<String, Object> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"patientId\":\"%s\",\"actorId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    UUID.randomUUID(), eventType, patientId, actorId, LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.PATIENT_EVENTS, patientId.toString(), eventMessage);
            log.debug("Published patient event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish patient event for patient {} event {}", patientId, eventType, e);
        }
    }

    /**
     * Publish medical record event to Kafka
     */
    private void publishMedicalRecordEvent(UUID recordId, String eventType, UUID actorId, Map<String, Object> eventData) {
        try {
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"recordId\":\"%s\",\"actorId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    UUID.randomUUID(), eventType, recordId, actorId, LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.MEDICAL_RECORD_EVENTS, recordId.toString(), eventMessage);
            log.debug("Published medical record event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish medical record event for record {} event {}", recordId, eventType, e);
        }
    }

    /**
     * Atomically merge transferred patient data, medications, labs, and records.
     */
    @Transactional
    public SyncMergeResponseDto syncMerge(SyncMergeRequestDto request) {
        log.info("Starting atomic sync merge for MRN: {}", request.getPatient().getMedicalRecordNumber());

        // 1. Resolve Patient
        PatientDto pDto = request.getPatient();
        Patient patient = patientRepository.findByMedicalRecordNumber(pDto.getMedicalRecordNumber())
                .orElseGet(() -> {
                    log.info("Patient not found, creating new patient for MRN: {}", pDto.getMedicalRecordNumber());
                    Patient newPatient = Patient.builder()
                            .medicalRecordNumber(pDto.getMedicalRecordNumber())
                            .firstName(pDto.getFirstName())
                            .lastName(pDto.getLastName())
                            .dateOfBirth(pDto.getDateOfBirth())
                            .gender(pDto.getGender())
                            .build();
                    return patientRepository.save(newPatient);
                });

        UUID patientId = patient.getId();

        // 2. Create Visit/Consultation Record
        PatientRecordDto vDto = request.getVisit();
        PatientRecord visit = PatientRecord.builder()
                .patientId(patientId)
                .recordType(vDto != null && vDto.getRecordType() != null ? vDto.getRecordType() : RecordType.VISIT)
                .title(vDto != null ? vDto.getTitle() : "Transferred Visit")
                .description(vDto != null ? vDto.getDescription() : "Transferred from mobile device")
                .createdBy(vDto != null && vDto.getCreatedBy() != null ? vDto.getCreatedBy() : "SYNC_SERVICE")
                .build();
        PatientRecord savedVisit = patientRecordRepository.save(visit);
        UUID visitId = savedVisit.getId();

        // 3. Sync Medications
        int medicationCount = 0;
        if (request.getMedications() != null) {
            for (MedicationDto mDto : request.getMedications()) {
                if (mDto.getMedicationName() == null) continue;
                Medication med = Medication.builder()
                        .patientRecordId(visitId)
                        .medicationName(mDto.getMedicationName())
                        .dosage(mDto.getDosage())
                        .frequency(mDto.getFrequency())
                        .status(mDto.getStatus() != null ? mDto.getStatus() : Medication.MedicationStatus.ACTIVE)
                        .startedAt(mDto.getStartedAt() != null ? mDto.getStartedAt() : LocalDateTime.now())
                        .build();
                medicationRepository.save(med);
                medicationCount++;
            }
        }

        // 4. Sync Lab Results
        int laboratoryCount = 0;
        if (request.getLabResults() != null) {
            for (LabResultDto lDto : request.getLabResults()) {
                if (lDto.getTestName() == null) continue;
                LabResult lab = LabResult.builder()
                        .patientRecordId(visitId)
                        .testName(lDto.getTestName())
                        .value(lDto.getValue())
                        .unit(lDto.getUnit())
                        .status(lDto.getStatus() != null ? lDto.getStatus() : LabResult.LabStatus.COMPLETED)
                        .performedAt(lDto.getPerformedAt() != null ? lDto.getPerformedAt() : LocalDateTime.now())
                        .build();
                labResultRepository.save(lab);
                laboratoryCount++;
            }
        }

        // 5. Sync Historical Records (Notes)
        int recordsCount = 0;
        if (request.getHistoricalRecords() != null) {
            for (PatientRecordDto hDto : request.getHistoricalRecords()) {
                PatientRecord history = PatientRecord.builder()
                        .patientId(patientId)
                        .recordType(RecordType.NOTE)
                        .title(hDto.getTitle() != null ? hDto.getTitle() : "Historical Note")
                        .description(hDto.getDescription())
                        .createdBy("MOBILE_TRANSFER")
                        .build();
                patientRecordRepository.save(history);
                recordsCount++;
            }
        }

        log.info("Sync merge completed for MRN: {}. Patient: {}, Visit: {}", 
            pDto.getMedicalRecordNumber(), patientId, visitId);

        return SyncMergeResponseDto.builder()
                .patientId(patientId)
                .visitId(visitId)
                .medicationsSynced(medicationCount)
                .labsSynced(laboratoryCount)
                .historySynced(recordsCount)
                .status("COMPLETED")
                .build();
    }
}
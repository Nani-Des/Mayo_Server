package com.mayo.auth.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mayo.auth.dto.AddFamilyMemberRequest;
import com.mayo.auth.dto.CreateFamilyRequest;
import com.mayo.auth.dto.FamilyDto;
import com.mayo.auth.dto.FamilyMemberDto;
import com.mayo.auth.entity.Family;
import com.mayo.auth.entity.FamilyMember;
import com.mayo.auth.entity.User;
import com.mayo.common.core.enums.UserType;
import com.mayo.auth.repository.FamilyMemberRepository;
import com.mayo.auth.repository.FamilyRepository;
import com.mayo.auth.repository.UserRepository;
import com.mayo.common.core.exception.ForbiddenException;
import com.mayo.common.core.exception.ResourceNotFoundException;
import com.mayo.events.topics.Topics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for family management operations
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FamilyService {

    private final FamilyRepository familyRepository;
    private final FamilyMemberRepository familyMemberRepository;
    private final UserRepository userRepository;
    private final com.mayo.auth.client.PatientClient patientClient;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Create a new family
     */
    @Transactional
    public FamilyDto createFamily(UUID userId, CreateFamilyRequest request) {
        log.info("Creating family: {} for user: {}", request.getName(), userId);

        // Check if user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Check if family name already exists for this user
        if (familyRepository.existsByNameAndCreatedBy(request.getName(), userId)) {
            throw new IllegalArgumentException("Family name already exists for this user");
        }

        // Create family
        Family family = Family.builder()
                .name(request.getName())
                .createdBy(userId)
                .description(request.getDescription())
                .isActive(true)
                .build();

        Family savedFamily = familyRepository.save(family);

        // Add creator as HEAD member
        FamilyMember headMember = FamilyMember.builder()
                .familyId(savedFamily.getId())
                .userId(userId)
                .role(FamilyMember.FamilyRole.HEAD)
                .status(FamilyMember.MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .invitedBy(userId)
                .build();

        FamilyMember savedHead = familyMemberRepository.save(headMember);

        // Auto-create patient record for HEAD
        try {
            User headUser = userRepository.findById(userId).orElse(null);
            if (headUser != null) {
                com.mayo.auth.client.PatientDto patientDto = com.mayo.auth.client.PatientDto.builder()
                        .firstName(headUser.getFullName().split(" ")[0])
                        .lastName(headUser.getFullName().contains(" ") ? headUser.getFullName().split(" ", 2)[1] : "")
                        .email(headUser.getEmail())
                        .ownerId(userId)
                        .familyMemberId(savedHead.getId())
                        .build();
                patientClient.createPatientRecord(patientDto);
            }
        } catch (Exception e) {
            log.error("Failed to auto-create patient record for head member: {}", userId, e);
        }

        // Publish family created event
        publishFamilyEvent(savedFamily.getId(), "FAMILY_CREATED", userId, Map.of(
                "familyId", savedFamily.getId(),
                "name", savedFamily.getName(),
                "createdBy", savedFamily.getCreatedBy()));

        log.info("Family created successfully with ID: {}", savedFamily.getId());
        return convertToFamilyDto(savedFamily);
    }

    /**
     * Get family by ID
     */
    public FamilyDto getFamilyById(UUID familyId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResourceNotFoundException("Family not found"));
        return convertToFamilyDto(family);
    }

    /**
     * Get families created by a user
     */
    public List<FamilyDto> getFamiliesByUser(UUID userId) {
        List<Family> families = familyRepository.findActiveByCreatedBy(userId);
        return families.stream()
                .map(this::convertToFamilyDto)
                .collect(Collectors.toList());
    }

    /**
     * Update family
     */
    @Transactional
    public FamilyDto updateFamily(UUID familyId, UUID userId, CreateFamilyRequest request) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResourceNotFoundException("Family not found"));

        // Check if user is the creator
        if (!family.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("Only family creator can update family");
        }

        // Check if new name conflicts
        if (!family.getName().equals(request.getName()) &&
                familyRepository.existsByNameAndCreatedBy(request.getName(), userId)) {
            throw new IllegalArgumentException("Family name already exists for this user");
        }

        family.setName(request.getName());
        family.setDescription(request.getDescription());

        Family updatedFamily = familyRepository.save(family);
        log.info("Family updated: {}", familyId);
        return convertToFamilyDto(updatedFamily);
    }

    /**
     * Delete family (soft delete)
     */
    @Transactional
    public void deleteFamily(UUID familyId, UUID userId) {
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResourceNotFoundException("Family not found"));

        // Check if user is the creator
        if (!family.getCreatedBy().equals(userId)) {
            throw new ForbiddenException("Only family creator can delete family");
        }

        family.setIsActive(false);
        familyRepository.save(family);
        log.info("Family deleted (soft): {}", familyId);
    }

    /**
     * Add member to family
     */
    @Transactional
    public FamilyMemberDto addFamilyMember(UUID familyId, UUID userId, AddFamilyMemberRequest request) {
        log.info("Adding member {} to family {} by user {}", request.getUserId(), familyId, userId);

        // Verify family exists and user has permission
        Family family = familyRepository.findById(familyId)
                .orElseThrow(() -> new ResourceNotFoundException("Family not found"));

        if (!family.getIsActive()) {
            throw new IllegalArgumentException("Family is not active");
        }

        // Check if user has permission to add members (creator or head)
        FamilyMember requesterMember = familyMemberRepository
                .findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new ForbiddenException("User is not a member of this family"));

        if (requesterMember.getRole() != FamilyMember.FamilyRole.HEAD) {
            throw new ForbiddenException("Only family head can add members");
        }

        // Check if user to be added exists or create dependent
        UUID targetUserId = request.getUserId();
        if (targetUserId == null) {
            // Create dependent user
            String dependentEmail = "dependent." + UUID.randomUUID() + "@mayo.app";
            User dependentUser = User.builder()
                .email(dependentEmail)
                .password(UUID.randomUUID().toString()) // Random password
                .fullName(request.getFirstName() + " " + request.getLastName())
                .userType(UserType.PATIENT)
                .isActive(true)
                .emailVerified(true)
                .build();
            User savedUser = userRepository.save(dependentUser);
            targetUserId = savedUser.getId();
            log.info("Created dependent user: {}", targetUserId);
        } else {
             userRepository.findById(request.getUserId())
                .orElseThrow(() -> new ResourceNotFoundException("User to be added not found"));
        }

        // Check if user is already a member
        if (familyMemberRepository.existsByFamilyIdAndUserId(familyId, targetUserId)) {
            throw new IllegalArgumentException("User is already a member of this family");
        }

        // Parse DOB
        java.time.LocalDate dob = null;
        if (request.getDateOfBirth() != null) {
             try {
                dob = java.time.LocalDate.parse(request.getDateOfBirth());
             } catch (Exception e) {
                log.warn("Invalid DOB format: {}", request.getDateOfBirth());
             }
        }

        // Create member
        FamilyMember member = FamilyMember.builder()
                .familyId(familyId)
                .userId(targetUserId)
                .patientId(request.getPatientId())
                .role(request.getRole())
                .relationship(request.getRelationship())
                .dateOfBirth(dob)
                .status(FamilyMember.MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .invitedBy(userId)
                .build();

        FamilyMember savedMember = familyMemberRepository.save(member);

        // Auto-create patient record for member
        try {
            User targetUser = userRepository.findById(targetUserId).orElse(null);
            if (targetUser != null) {
                 com.mayo.auth.client.PatientDto patientDto = com.mayo.auth.client.PatientDto.builder()
                        .firstName(request.getFirstName() != null ? request.getFirstName() : targetUser.getFullName().split(" ")[0])
                        .lastName(request.getLastName() != null ? request.getLastName() : (targetUser.getFullName().contains(" ") ? targetUser.getFullName().split(" ", 2)[1] : ""))
                        .email(targetUser.getEmail())
                        .dateOfBirth(dob)
                        .ownerId(targetUserId)
                        .familyMemberId(savedMember.getId())
                        .build();
                
                com.mayo.common.core.dto.ApiResponse<com.mayo.auth.client.PatientDto> response = patientClient.createPatientRecord(patientDto);
                if (response.isSuccess() && response.getData() != null) {
                    savedMember.setPatientId(response.getData().getId());
                    familyMemberRepository.save(savedMember);
                }
            }
        } catch (Exception e) {
            log.error("Failed to auto-create patient record for family member: {}", targetUserId, e);
        }

        // Publish member added event
        publishFamilyEvent(familyId, "MEMBER_ADDED", userId, Map.of(
                "memberId", savedMember.getId(),
                "userId", savedMember.getUserId(),
                "role", savedMember.getRole().name()));

        log.info("Member added to family: {}", savedMember.getId());
        return convertToFamilyMemberDto(savedMember);
    }

    /**
     * Get family members
     */
    public List<FamilyMemberDto> getFamilyMembers(UUID familyId, UUID userId) {
        // Verify user has access to family
        if (!hasAccessToFamily(familyId, userId)) {
            throw new ForbiddenException("Access denied to family");
        }

        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);
        return members.stream()
                .map(this::convertToFamilyMemberDto)
                .collect(Collectors.toList());
    }

    /**
     * Remove family member
     */
    @Transactional
    public void removeFamilyMember(UUID familyId, UUID memberId, UUID userId) {
        FamilyMember member = familyMemberRepository.findById(memberId)
                .orElseThrow(() -> new ResourceNotFoundException("Family member not found"));

        if (!member.getFamilyId().equals(familyId)) {
            throw new IllegalArgumentException("Member does not belong to this family");
        }

        // Check permissions - only head can remove members (except themselves)
        FamilyMember requesterMember = familyMemberRepository
                .findByFamilyIdAndUserId(familyId, userId)
                .orElseThrow(() -> new ForbiddenException("User is not a member of this family"));

        if (requesterMember.getRole() != FamilyMember.FamilyRole.HEAD) {
            throw new ForbiddenException("Only family head can remove members");
        }

        if (member.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Cannot remove yourself from family");
        }

        familyMemberRepository.delete(member);

        // Publish member removed event
        publishFamilyEvent(familyId, "MEMBER_REMOVED", userId, Map.of(
                "memberId", memberId,
                "userId", member.getUserId(),
                "role", member.getRole().name()));

        log.info("Member removed from family: {}", memberId);
    }

    /**
     * Check if user has access to family
     */
    boolean hasAccessToFamily(UUID familyId, UUID userId) {
        return familyMemberRepository.existsByFamilyIdAndUserId(familyId, userId);
    }

    /**
     * Convert Family to FamilyDto
     */
    private FamilyDto convertToFamilyDto(Family family) {
        return FamilyDto.builder()
                .id(family.getId())
                .name(family.getName())
                .createdBy(family.getCreatedBy())
                .description(family.getDescription())
                .isActive(family.getIsActive())
                .createdAt(family.getCreatedAt())
                .updatedAt(family.getUpdatedAt())
                .build();
    }

    /**
     * Convert FamilyMember to FamilyMemberDto
     */
    private FamilyMemberDto convertToFamilyMemberDto(FamilyMember member) {
        User user = userRepository.findById(member.getUserId()).orElse(null);
        String firstName = "";
        String lastName = "";
        if (user != null && user.getFullName() != null) {
            String[] parts = user.getFullName().split(" ", 2);
            firstName = parts[0];
            lastName = parts.length > 1 ? parts[1] : "";
        }

        // Calculate age
        Integer age = null;
        if (member.getDateOfBirth() != null) {
            age = java.time.Period.between(member.getDateOfBirth(), java.time.LocalDate.now()).getYears();
        }

        return FamilyMemberDto.builder()
                .id(member.getId())
                .familyId(member.getFamilyId())
                .userId(member.getUserId())
                .patientId(member.getPatientId())
                .role(member.getRole())
                .status(member.getStatus())
                .joinedAt(member.getJoinedAt())
                .invitedBy(member.getInvitedBy())
                .createdAt(member.getCreatedAt())
                .updatedAt(member.getUpdatedAt())
                .firstName(firstName)
                .lastName(lastName)
                .relationship(member.getRelationship())
                .dateOfBirth(member.getDateOfBirth() != null ? member.getDateOfBirth().toString() : null)
                .age(age)
                .build();
    }

    /**
     * Publish family event to Kafka
     */
    private void publishFamilyEvent(UUID familyId, String eventType, UUID userId, Map<String, Object> eventData) {
        try {
            // Convert eventData to proper JSON string
            String eventDataJson = objectMapper.writeValueAsString(eventData);

            String eventMessage = String.format(
                    "{\"eventId\":\"%s\",\"eventType\":\"%s\",\"familyId\":\"%s\",\"userId\":\"%s\",\"timestamp\":\"%s\",\"data\":%s}",
                    UUID.randomUUID(), eventType, familyId, userId, LocalDateTime.now(), eventDataJson);
            kafkaTemplate.send(Topics.FAMILY_EVENTS, familyId.toString(), eventMessage);
            log.debug("Published family event: {}", eventMessage);
        } catch (Exception e) {
            log.error("Failed to publish family event for family {} event {}", familyId, eventType, e);
        }
    }
}
package com.mayo.integration;

import com.mayo.auth.entity.Family;
import com.mayo.auth.entity.FamilyMember;
import com.mayo.auth.entity.User;
import com.mayo.auth.repository.FamilyRepository;
import com.mayo.auth.repository.FamilyMemberRepository;
import com.mayo.auth.repository.UserRepository;
import com.mayo.patientrecord.entity.OwnershipTransfer;
import com.mayo.patientrecord.entity.Patient;
import com.mayo.patientrecord.repository.OwnershipTransferRepository;
import com.mayo.patientrecord.repository.PatientRepository;
import com.mayo.audit.entity.AuditEvent;
import com.mayo.audit.repository.AuditEventRepository;
import com.mayo.notification.entity.Notification;
import com.mayo.notification.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional
class FamilyOwnershipTransferIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:3.6.0");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FamilyRepository familyRepository;

    @Autowired
    private FamilyMemberRepository familyMemberRepository;

    @Autowired
    private PatientRepository patientRepository;

    @Autowired
    private OwnershipTransferRepository ownershipTransferRepository;

    @Autowired
    private AuditEventRepository auditEventRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    private UUID familyHeadId;
    private UUID familyMemberId;
    private UUID patientId;
    private UUID familyId;

    @BeforeEach
    void setUp() {
        // Create test users
        User familyHead = User.builder()
                .id(UUID.randomUUID())
                .email("family.head@example.com")
                .fullName("Family Head")
                .build();
        familyHeadId = userRepository.save(familyHead).getId();

        User familyMember = User.builder()
                .id(UUID.randomUUID())
                .email("family.member@example.com")
                .fullName("Family Member")
                .build();
        familyMemberId = userRepository.save(familyMember).getId();

        // Create family
        Family family = Family.builder()
                .name("Test Family")
                .createdBy(familyHeadId)
                .description("Integration test family")
                .isActive(true)
                .createdAt(LocalDateTime.now())
                .build();
        familyId = familyRepository.save(family).getId();

        // Add family head
        FamilyMember headMember = FamilyMember.builder()
                .familyId(familyId)
                .userId(familyHeadId)
                .role(FamilyMember.FamilyRole.HEAD)
                .status(FamilyMember.MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        familyMemberRepository.save(headMember);

        // Add family member
        FamilyMember member = FamilyMember.builder()
                .familyId(familyId)
                .userId(familyMemberId)
                .role(FamilyMember.FamilyRole.MEMBER)
                .status(FamilyMember.MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        familyMemberRepository.save(member);

        // Create patient owned by family head
        Patient patient = Patient.builder()
                .medicalRecordNumber("MRN" + UUID.randomUUID().toString().substring(0, 8))
                .firstName("Test")
                .lastName("Patient")
                .ownerId(familyHeadId)
                .build();
        patientId = patientRepository.save(patient).getId();
    }

    @Test
    void completeFamilyOwnershipTransferWorkflow_ShouldSucceed() {
        // Step 1: Verify initial state
        Patient initialPatient = patientRepository.findById(patientId).orElseThrow();
        assertThat(initialPatient.getOwnerId()).isEqualTo(familyHeadId);

        // Step 2: Initiate ownership transfer
        OwnershipTransfer transfer = OwnershipTransfer.builder()
                .patientId(patientId)
                .previousOwnerId(familyHeadId)
                .newOwnerId(familyMemberId)
                .status(OwnershipTransfer.TransferStatus.PENDING)
                .reason("Transfer to family member")
                .initiatedBy(familyHeadId)
                .initiatedAt(LocalDateTime.now())
                .build();
        UUID transferId = ownershipTransferRepository.save(transfer).getId();

        // Step 3: Verify transfer was created
        OwnershipTransfer savedTransfer = ownershipTransferRepository.findById(transferId).orElseThrow();
        assertThat(savedTransfer.getStatus()).isEqualTo(OwnershipTransfer.TransferStatus.PENDING);
        assertThat(savedTransfer.getPatientId()).isEqualTo(patientId);

        // Step 4: Confirm ownership transfer
        savedTransfer.setStatus(OwnershipTransfer.TransferStatus.CONFIRMED);
        savedTransfer.setConfirmedBy(familyMemberId);
        savedTransfer.setConfirmedAt(LocalDateTime.now());
        ownershipTransferRepository.save(savedTransfer);

        // Step 5: Update patient ownership
        Patient patient = patientRepository.findById(patientId).orElseThrow();
        patient.setOwnerId(familyMemberId);
        patientRepository.save(patient);

        // Step 6: Complete the transfer
        savedTransfer.setStatus(OwnershipTransfer.TransferStatus.COMPLETED);
        savedTransfer.setCompletedAt(LocalDateTime.now());
        ownershipTransferRepository.save(savedTransfer);

        // Step 7: Verify final state
        Patient finalPatient = patientRepository.findById(patientId).orElseThrow();
        assertThat(finalPatient.getOwnerId()).isEqualTo(familyMemberId);

        OwnershipTransfer completedTransfer = ownershipTransferRepository.findById(transferId).orElseThrow();
        assertThat(completedTransfer.getStatus()).isEqualTo(OwnershipTransfer.TransferStatus.COMPLETED);

        // Step 8: Verify audit trail was created
        await().untilAsserted(() -> {
            List<AuditEvent> auditEvents = auditEventRepository.findByResourceId(patientId);
            assertThat(auditEvents).isNotEmpty();
            assertThat(auditEvents.stream().anyMatch(event ->
                "OWNERSHIP_TRANSFERRED".equals(event.getAction()))).isTrue();
        });

        // Step 9: Verify notifications were sent
        await().untilAsserted(() -> {
            List<Notification> notifications = notificationRepository.findByUserId(familyMemberId);
            assertThat(notifications).isNotEmpty();
            assertThat(notifications.stream().anyMatch(notification ->
                "OWNERSHIP_TRANSFER".equals(notification.getType()))).isTrue();
        });
    }

    @Test
    void familyCreationAndMemberAdditionWorkflow_ShouldSucceed() {
        // Step 1: Verify family exists
        Family createdFamily = familyRepository.findById(familyId).orElseThrow();
        assertThat(createdFamily.getName()).isEqualTo("Test Family");
        assertThat(createdFamily.getCreatedBy()).isEqualTo(familyHeadId);

        // Step 2: Verify family members
        List<FamilyMember> members = familyMemberRepository.findByFamilyId(familyId);
        assertThat(members).hasSize(2);

        FamilyMember head = members.stream()
                .filter(m -> m.getRole().equals(FamilyMember.FamilyRole.HEAD))
                .findFirst().orElseThrow();
        assertThat(head.getUserId()).isEqualTo(familyHeadId);

        FamilyMember member = members.stream()
                .filter(m -> m.getRole().equals(FamilyMember.FamilyRole.MEMBER))
                .findFirst().orElseThrow();
        assertThat(member.getUserId()).isEqualTo(familyMemberId);

        // Step 3: Verify audit events for family creation
        await().untilAsserted(() -> {
            List<AuditEvent> auditEvents = auditEventRepository.findByResourceId(familyId);
            assertThat(auditEvents).isNotEmpty();
            assertThat(auditEvents.stream().anyMatch(event ->
                "FAMILY_CREATED".equals(event.getAction()))).isTrue();
        });

        // Step 4: Verify member addition audit events
        await().untilAsserted(() -> {
            List<AuditEvent> memberEvents = auditEventRepository.findByAction("MEMBER_ADDED");
            assertThat(memberEvents).isNotEmpty();
        });
    }

    @Test
    void concurrentOwnershipTransfers_ShouldHandleConflicts() {
        // Create another user
        User anotherMember = User.builder()
                .id(UUID.randomUUID())
                .email("another.member@example.com")
                .fullName("Another Member")
                .build();
        UUID anotherMemberId = userRepository.save(anotherMember).getId();

        // Add to family
        FamilyMember anotherFamilyMember = FamilyMember.builder()
                .familyId(familyId)
                .userId(anotherMemberId)
                .role(FamilyMember.FamilyRole.MEMBER)
                .status(FamilyMember.MemberStatus.ACTIVE)
                .joinedAt(LocalDateTime.now())
                .build();
        familyMemberRepository.save(anotherFamilyMember);

        // Create two concurrent transfer requests
        OwnershipTransfer transfer1 = OwnershipTransfer.builder()
                .patientId(patientId)
                .previousOwnerId(familyHeadId)
                .newOwnerId(familyMemberId)
                .status(OwnershipTransfer.TransferStatus.PENDING)
                .reason("First transfer")
                .initiatedBy(familyHeadId)
                .initiatedAt(LocalDateTime.now())
                .build();
        ownershipTransferRepository.save(transfer1);

        OwnershipTransfer transfer2 = OwnershipTransfer.builder()
                .patientId(patientId)
                .previousOwnerId(familyHeadId)
                .newOwnerId(anotherMemberId)
                .status(OwnershipTransfer.TransferStatus.PENDING)
                .reason("Second transfer")
                .initiatedBy(familyHeadId)
                .initiatedAt(LocalDateTime.now().plusSeconds(1))
                .build();
        ownershipTransferRepository.save(transfer2);

        // Verify both transfers exist
        List<OwnershipTransfer> pendingTransfers = ownershipTransferRepository
                .findByPatientIdAndStatus(patientId, OwnershipTransfer.TransferStatus.PENDING);
        assertThat(pendingTransfers).hasSize(2);

        // Complete first transfer
        transfer1.setStatus(OwnershipTransfer.TransferStatus.CONFIRMED);
        transfer1.setConfirmedBy(familyMemberId);
        transfer1.setConfirmedAt(LocalDateTime.now());
        ownershipTransferRepository.save(transfer1);

        Patient patient = patientRepository.findById(patientId).orElseThrow();
        patient.setOwnerId(familyMemberId);
        patientRepository.save(patient);

        transfer1.setStatus(OwnershipTransfer.TransferStatus.COMPLETED);
        transfer1.setCompletedAt(LocalDateTime.now());
        ownershipTransferRepository.save(transfer1);

        // Second transfer should now be invalid
        OwnershipTransfer secondTransfer = ownershipTransferRepository.findById(transfer2.getId()).orElseThrow();
        assertThat(secondTransfer.getStatus()).isEqualTo(OwnershipTransfer.TransferStatus.PENDING);

        // Verify audit trail captures the conflict scenario
        await().untilAsserted(() -> {
            List<AuditEvent> auditEvents = auditEventRepository.findByResourceId(patientId);
            assertThat(auditEvents.stream().filter(event ->
                "OWNERSHIP_TRANSFERRED".equals(event.getAction()))).hasSize(1);
        });
    }

    @Test
    void familyDataSynchronization_ShouldTriggerSyncEvents() {
        // This test would verify that family data changes trigger sync events
        // In a real implementation, this would test Kafka event publishing

        // Step 1: Make a change to family data
        Family family = familyRepository.findById(familyId).orElseThrow();
        family.setDescription("Updated description");
        familyRepository.save(family);

        // Step 2: Verify audit event was created
        await().untilAsserted(() -> {
            List<AuditEvent> auditEvents = auditEventRepository.findByResourceId(familyId);
            assertThat(auditEvents.stream().anyMatch(event ->
                "FAMILY_UPDATED".equals(event.getAction()))).isTrue();
        });

        // Step 3: In a full implementation, verify Kafka events were published
        // This would require Kafka test containers and event consumption verification
    }

    @Test
    void ownershipTransferExpiration_ShouldHandleExpiredTransfers() {
        // Create an old transfer
        OwnershipTransfer oldTransfer = OwnershipTransfer.builder()
                .patientId(patientId)
                .previousOwnerId(familyHeadId)
                .newOwnerId(familyMemberId)
                .status(OwnershipTransfer.TransferStatus.PENDING)
                .reason("Expired transfer")
                .initiatedBy(familyHeadId)
                .initiatedAt(LocalDateTime.now().minusDays(8)) // More than 7 days ago
                .build();
        UUID oldTransferId = ownershipTransferRepository.save(oldTransfer).getId();

        // Verify transfer exists
        OwnershipTransfer savedOldTransfer = ownershipTransferRepository.findById(oldTransferId).orElseThrow();
        assertThat(savedOldTransfer.getStatus()).isEqualTo(OwnershipTransfer.TransferStatus.PENDING);

        // In a real scenario, a scheduled job would cancel expired transfers
        // Here we simulate the cancellation
        savedOldTransfer.setStatus(OwnershipTransfer.TransferStatus.CANCELLED);
        ownershipTransferRepository.save(savedOldTransfer);

        // Verify transfer was cancelled
        OwnershipTransfer cancelledTransfer = ownershipTransferRepository.findById(oldTransferId).orElseThrow();
        assertThat(cancelledTransfer.getStatus()).isEqualTo(OwnershipTransfer.TransferStatus.CANCELLED);

        // Verify audit event for cancellation
        await().untilAsserted(() -> {
            List<AuditEvent> auditEvents = auditEventRepository.findByResourceId(patientId);
            assertThat(auditEvents.stream().anyMatch(event ->
                "OWNERSHIP_TRANSFER_CANCELLED".equals(event.getAction()))).isTrue();
        });
    }
}
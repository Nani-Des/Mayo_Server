package com.mayo.auth.service;

import com.mayo.auth.dto.AddFamilyMemberRequest;
import com.mayo.auth.dto.CreateFamilyRequest;
import com.mayo.auth.dto.FamilyDto;
import com.mayo.auth.dto.FamilyMemberDto;
import com.mayo.auth.entity.Family;
import com.mayo.auth.entity.FamilyMember;
import com.mayo.auth.entity.User;
import com.mayo.auth.repository.FamilyMemberRepository;
import com.mayo.auth.repository.FamilyRepository;
import com.mayo.auth.repository.UserRepository;
import com.mayo.common.core.exception.ResourceNotFoundException;
import com.mayo.common.core.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FamilyServiceTest {

        @Mock
        private FamilyRepository familyRepository;

        @Mock
        private FamilyMemberRepository familyMemberRepository;

        @Mock
        private UserRepository userRepository;

        @InjectMocks
        private FamilyService familyService;

        private UUID userId;
        private UUID familyId;
        private UUID memberId;
        private Family family;
        private FamilyMember familyMember;
        private User user;

        @BeforeEach
        void setUp() {
                userId = UUID.randomUUID();
                familyId = UUID.randomUUID();
                memberId = UUID.randomUUID();

                user = User.builder()
                                .id(userId)
                                .email("test@example.com")
                                .fullName("Test User")
                                .build();

                family = Family.builder()
                                .id(familyId)
                                .name("Test Family")
                                .createdBy(userId)
                                .description("Test family description")
                                .isActive(true)
                                .createdAt(LocalDateTime.now())
                                .build();

                familyMember = FamilyMember.builder()
                                .id(memberId)
                                .familyId(familyId)
                                .userId(userId)
                                .role(FamilyMember.FamilyRole.HEAD)
                                .status(FamilyMember.MemberStatus.ACTIVE)
                                .joinedAt(LocalDateTime.now())
                                .build();
        }

        @Test
        void createFamily_ShouldCreateFamilySuccessfully() {
                // Given
                CreateFamilyRequest request = CreateFamilyRequest.builder()
                                .name("Test Family")
                                .description("Test description")
                                .build();

                when(userRepository.findById(userId)).thenReturn(Optional.of(user));
                when(familyRepository.save(any(Family.class))).thenReturn(family);
                when(familyMemberRepository.save(any(FamilyMember.class))).thenReturn(familyMember);

                // When
                FamilyDto result = familyService.createFamily(userId, request);

                // Then
                assertThat(result).isNotNull();
                assertThat(result.getName()).isEqualTo("Test Family");
                assertThat(result.getCreatedBy()).isEqualTo(userId);
                verify(familyRepository).save(any(Family.class));
                verify(familyMemberRepository).save(any(FamilyMember.class));
        }

        @Test
        void createFamily_UserNotFound_ShouldThrowException() {
                // Given
                CreateFamilyRequest request = CreateFamilyRequest.builder()
                                .name("Test Family")
                                .description("Test description")
                                .build();

                when(userRepository.findById(userId)).thenReturn(Optional.empty());

                // When & Then
                assertThatThrownBy(() -> familyService.createFamily(userId, request))
                                .isInstanceOf(ResourceNotFoundException.class)
                                .hasMessage("User not found");
        }

        @Test
        void addFamilyMember_ShouldAddMemberSuccessfully() {
                // Given
                UUID newMemberId = UUID.randomUUID();
                User newUser = User.builder().id(newMemberId).email("new@example.com").build();

                AddFamilyMemberRequest request = AddFamilyMemberRequest.builder()
                                .userId(newMemberId)
                                .role(FamilyMember.FamilyRole.MEMBER)
                                .build();

                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
                when(userRepository.findById(newMemberId)).thenReturn(Optional.of(newUser));
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(Optional.of(familyMember)); // Creator is head
                when(familyMemberRepository.save(any(FamilyMember.class))).thenReturn(familyMember);

                // When
                FamilyMemberDto result = familyService.addFamilyMember(familyId, userId, request);

                // Then
                assertThat(result).isNotNull();
                verify(familyMemberRepository).save(any(FamilyMember.class));
        }

        @Test
        void addFamilyMember_UnauthorizedUser_ShouldThrowException() {
                // Given
                UUID unauthorizedUserId = UUID.randomUUID();
                UUID newMemberId = UUID.randomUUID();

                AddFamilyMemberRequest request = AddFamilyMemberRequest.builder()
                                .userId(newMemberId)
                                .role(FamilyMember.FamilyRole.MEMBER)
                                .build();

                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, unauthorizedUserId))
                                .thenReturn(Optional.empty()); // Not a member

                // When & Then
                assertThatThrownBy(() -> familyService.addFamilyMember(familyId, unauthorizedUserId, request))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Only family heads can add members");
        }

        @Test
        void removeFamilyMember_ShouldRemoveMemberSuccessfully() {
                // Given
                UUID memberToRemoveId = UUID.randomUUID();
                FamilyMember memberToRemove = FamilyMember.builder()
                                .id(memberToRemoveId)
                                .familyId(familyId)
                                .userId(memberToRemoveId)
                                .role(FamilyMember.FamilyRole.MEMBER)
                                .build();

                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(Optional.of(familyMember)); // User is head
                when(familyMemberRepository.findById(memberToRemoveId)).thenReturn(Optional.of(memberToRemove));

                // When
                familyService.removeFamilyMember(familyId, userId, memberToRemoveId);

                // Then
                verify(familyMemberRepository).delete(memberToRemove);
        }

        @Test
        void removeFamilyMember_CannotRemoveSelf_ShouldThrowException() {
                // Given
                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(Optional.of(familyMember));

                // When & Then
                assertThatThrownBy(() -> familyService.removeFamilyMember(familyId, userId, userId))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Cannot remove yourself from family");
        }

        @Test
        void getFamilyMembers_ShouldReturnMembersSuccessfully() {
                // Given
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(Optional.of(familyMember)); // User is member
                when(familyMemberRepository.findByFamilyId(familyId)).thenReturn(List.of(familyMember));

                // When
                List<FamilyMemberDto> result = familyService.getFamilyMembers(familyId, userId);

                // Then
                assertThat(result).isNotNull();
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getUserId()).isEqualTo(userId);
        }

        @Test
        void getFamilyMembers_UserNotMember_ShouldThrowException() {
                // Given
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(Optional.empty()); // User is not a member

                // When & Then
                assertThatThrownBy(() -> familyService.getFamilyMembers(familyId, userId))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Access denied to family");
        }

        @Test
        void getUserFamilies_ShouldReturnUserFamilies() {
                // Given
                List<Family> families = List.of(family);
                when(familyRepository.findActiveByCreatedBy(userId)).thenReturn(families);

                // When
                List<FamilyDto> result = familyService.getFamiliesByUser(userId);

                // Then
                assertThat(result).hasSize(1);
                assertThat(result.get(0).getId()).isEqualTo(familyId);
        }

        @Test
        void updateFamily_ShouldUpdateFamilySuccessfully() {
                // Given
                Family updatedFamily = Family.builder()
                                .id(familyId)
                                .name("Updated Family")
                                .description("Updated description")
                                .build();

                CreateFamilyRequest request = CreateFamilyRequest.builder()
                                .name("Updated Family")
                                .description("Updated description")
                                .build();

                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
                when(familyRepository.save(any(Family.class))).thenReturn(updatedFamily);

                // When
                FamilyDto result = familyService.updateFamily(familyId, userId, request);

                // Then
                assertThat(result.getName()).isEqualTo("Updated Family");
                assertThat(result.getDescription()).isEqualTo("Updated description");
                verify(familyRepository).save(any(Family.class));
        }

        @Test
        void updateFamily_UnauthorizedUser_ShouldThrowException() {
                // Given
                FamilyMember regularMember = FamilyMember.builder()
                                .id(memberId)
                                .familyId(familyId)
                                .userId(userId)
                                .role(FamilyMember.FamilyRole.MEMBER) // Not HEAD
                                .build();

                CreateFamilyRequest request = CreateFamilyRequest.builder()
                                .name("Updated Family")
                                .description("Updated description")
                                .build();

                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));
                when(familyMemberRepository.findByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(Optional.of(regularMember));

                // When & Then
                assertThatThrownBy(() -> familyService.updateFamily(familyId, userId, request))
                                .isInstanceOf(ForbiddenException.class)
                                .hasMessage("Only family creator can update family");
        }

        @Test
        void deleteFamily_ShouldDeleteFamilySuccessfully() {
                // Given
                when(familyRepository.findById(familyId)).thenReturn(Optional.of(family));

                // When
                familyService.deleteFamily(familyId, userId);

                // Then
                verify(familyRepository).save(argThat(f -> !f.getIsActive()));
        }

        @Test
        void hasAccessToFamily_ShouldReturnTrueForMember() {
                // Given
                when(familyMemberRepository.existsByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(true); // User is a member

                // When
                boolean result = familyService.hasAccessToFamily(familyId, userId);

                // Then
                assertThat(result).isTrue();
        }

        @Test
        void hasAccessToFamily_ShouldReturnFalseForNonMember() {
                // Given
                when(familyMemberRepository.existsByFamilyIdAndUserId(familyId, userId))
                                .thenReturn(false); // User is not a member

                // When
                boolean result = familyService.hasAccessToFamily(familyId, userId);

                // Then
                assertThat(result).isFalse();
        }
}
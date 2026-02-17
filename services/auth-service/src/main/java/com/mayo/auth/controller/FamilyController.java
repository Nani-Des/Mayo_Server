package com.mayo.auth.controller;

import com.mayo.auth.dto.AddFamilyMemberRequest;
import com.mayo.auth.dto.CreateFamilyRequest;
import com.mayo.auth.dto.FamilyDto;
import com.mayo.auth.dto.FamilyMemberDto;
import com.mayo.auth.service.FamilyService;
import com.mayo.common.core.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST controller for family management operations
 */
@RestController
@RequestMapping("/api/v1/families")
@RequiredArgsConstructor
@Tag(name = "Family Management", description = "Family account management APIs")
public class FamilyController {

    private final FamilyService familyService;

    /**
     * Create a new family
     */
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new family")
    public ResponseEntity<ApiResponse<FamilyDto>> createFamily(
            @Valid @RequestBody CreateFamilyRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        FamilyDto family = familyService.createFamily(userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(family, "Family created successfully"));
    }

    /**
     * Get family by ID
     */
    @GetMapping("/{familyId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get family by ID")
    public ResponseEntity<ApiResponse<FamilyDto>> getFamily(
            @Parameter(description = "Family ID") @PathVariable UUID familyId) {

        FamilyDto family = familyService.getFamilyById(familyId);
        return ResponseEntity.ok(ApiResponse.success(family, "Family retrieved successfully"));
    }

    /**
     * Get families created by current user
     */
    @GetMapping("/my-families")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get families created by current user")
    public ResponseEntity<ApiResponse<List<FamilyDto>>> getMyFamilies(Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        List<FamilyDto> families = familyService.getFamiliesByUser(userId);
        return ResponseEntity.ok(ApiResponse.success(families, "Families retrieved successfully"));
    }

    /**
     * Update family
     */
    @PutMapping("/{familyId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Update family")
    public ResponseEntity<ApiResponse<FamilyDto>> updateFamily(
            @Parameter(description = "Family ID") @PathVariable UUID familyId,
            @Valid @RequestBody CreateFamilyRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        FamilyDto family = familyService.updateFamily(familyId, userId, request);
        return ResponseEntity.ok(ApiResponse.success(family, "Family updated successfully"));
    }

    /**
     * Delete family
     */
    @DeleteMapping("/{familyId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete family")
    public ResponseEntity<ApiResponse<Void>> deleteFamily(
            @Parameter(description = "Family ID") @PathVariable UUID familyId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        familyService.deleteFamily(familyId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Family deleted successfully"));
    }

    /**
     * Add member to family
     */
    @PostMapping("/{familyId}/members")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add member to family")
    public ResponseEntity<ApiResponse<FamilyMemberDto>> addFamilyMember(
            @Parameter(description = "Family ID") @PathVariable UUID familyId,
            @Valid @RequestBody AddFamilyMemberRequest request,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        FamilyMemberDto member = familyService.addFamilyMember(familyId, userId, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(member, "Member added to family successfully"));
    }

    /**
     * Get family members
     */
    @GetMapping("/{familyId}/members")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get family members")
    public ResponseEntity<ApiResponse<List<FamilyMemberDto>>> getFamilyMembers(
            @Parameter(description = "Family ID") @PathVariable UUID familyId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        List<FamilyMemberDto> members = familyService.getFamilyMembers(familyId, userId);
        return ResponseEntity.ok(ApiResponse.success(members, "Family members retrieved successfully"));
    }

    /**
     * Remove family member
     */
    @DeleteMapping("/{familyId}/members/{memberId}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove family member")
    public ResponseEntity<ApiResponse<Void>> removeFamilyMember(
            @Parameter(description = "Family ID") @PathVariable UUID familyId,
            @Parameter(description = "Member ID") @PathVariable UUID memberId,
            Authentication authentication) {

        UUID userId = UUID.fromString(authentication.getName());
        familyService.removeFamilyMember(familyId, memberId, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Member removed from family successfully"));
    }
}
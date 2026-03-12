package com.risktrace.user_service.Controller;

import com.risktrace.user_service.DTO.*;
import com.risktrace.user_service.Service.OrganizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/organizations")
@RequiredArgsConstructor
public class OrganizationController {

    private final OrganizationService organizationService;

    @PostMapping
    public ResponseEntity<OrganizationResponse> createOrganization(
            @RequestBody OrganizationRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(organizationService.createOrganization(request, userId));
    }

    @GetMapping("/my")
    public ResponseEntity<List<OrganizationResponse>> getMyOrganizations(
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.ok(organizationService.getUserOrganizations(userId));
    }

    @GetMapping("/{organizationId}/members")
    public ResponseEntity<List<OrganizationMemberResponse>> getOrganizationMembers(
            @PathVariable String organizationId) {
        return ResponseEntity.ok(organizationService.getOrganizationMembers(organizationId));
    }

    @PostMapping("/{organizationId}/members/invite")
    public ResponseEntity<OrganizationMemberResponse> inviteMember(
            @PathVariable String organizationId,
            @RequestBody InviteMemberRequest request,
            @RequestHeader("X-User-Id") String userId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(organizationService.inviteMember(organizationId, request, userId));
    }

    @DeleteMapping("/{organizationId}/members/{userIdToRemove}")
    public ResponseEntity<Void> removeMember(
            @PathVariable String organizationId,
            @PathVariable String userIdToRemove,
            @RequestHeader("X-User-Id") String userId) {
        organizationService.removeMember(organizationId, userIdToRemove, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{organizationId}/transfer-ownership")
    public ResponseEntity<Void> transferOwnership(
            @PathVariable String organizationId,
            @RequestBody TransferOwnershipRequest request,
            @RequestHeader("X-User-Id") String userId) {
        organizationService.transferOwnership(organizationId, request, userId);
        return ResponseEntity.ok().build();
    }

    // For Platform Admins
    @GetMapping("/all")
    public ResponseEntity<List<OrganizationResponse>> getAllOrganizations() {
        return ResponseEntity.ok(organizationService.getAllOrganizations());
    }

    @PutMapping("/{organizationId}/status")
    public ResponseEntity<OrganizationResponse> updateOrganizationStatus(
            @PathVariable String organizationId,
            @RequestParam boolean enabled) {
        return ResponseEntity.ok(organizationService.updateOrganizationStatus(organizationId, enabled));
    }

    @PutMapping("/{organizationId}/admin/transfer-ownership")
    public ResponseEntity<Void> adminTransferOwnership(
            @PathVariable String organizationId,
            @RequestParam("newOwnerId") String newOwnerId) {
        organizationService.adminTransferOwnership(organizationId, newOwnerId);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{organizationId}/admin/assign-owner")
    public ResponseEntity<Void> adminAssignOwner(
            @PathVariable String organizationId,
            @RequestParam("newOwnerId") String newOwnerId) {
        organizationService.adminAssignOwner(organizationId, newOwnerId);
        return ResponseEntity.ok().build();
    }
}

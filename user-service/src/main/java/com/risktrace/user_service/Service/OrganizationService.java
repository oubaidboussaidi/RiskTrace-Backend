package com.risktrace.user_service.Service;

import com.risktrace.user_service.DTO.*;
import com.risktrace.user_service.Enums.OrganizationRole;
import com.risktrace.user_service.Model.Organization;
import com.risktrace.user_service.Model.OrganizationMember;
import com.risktrace.user_service.Model.User;
import com.risktrace.user_service.Repository.OrganizationMemberRepository;
import com.risktrace.user_service.Repository.OrganizationRepository;
import com.risktrace.user_service.Repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrganizationService {

        private final OrganizationRepository organizationRepository;
        private final OrganizationMemberRepository organizationMemberRepository;
        private final UserRepository userRepository;

        @Transactional
        public OrganizationResponse createOrganization(OrganizationRequest request, String currentUserId) {
                Organization org = Organization.builder()
                                .name(request.getName())
                                .createdBy(currentUserId)
                                .createdAt(Instant.now())
                                .build();

                Organization savedOrg = organizationRepository.save(org);

                // Add creator as OWNER
                OrganizationMember member = OrganizationMember.builder()
                                .userId(currentUserId)
                                .organizationId(savedOrg.getId())
                                .role(OrganizationRole.OWNER)
                                .createdAt(Instant.now())
                                .build();

                organizationMemberRepository.save(member);

                return mapToResponse(savedOrg);
        }

        public List<OrganizationResponse> getUserOrganizations(String userId) {
                List<OrganizationMember> memberships = organizationMemberRepository.findByUserId(userId);
                return memberships.stream()
                                .map(m -> organizationRepository.findById(m.getOrganizationId()))
                                .filter(Optional::isPresent)
                                .map(Optional::get)
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        public List<OrganizationMemberResponse> getOrganizationMembers(String organizationId) {
                return organizationMemberRepository.findByOrganizationId(organizationId).stream()
                                .map(this::mapToMemberResponse)
                                .collect(Collectors.toList());
        }

        @Transactional
        public OrganizationMemberResponse inviteMember(String organizationId, InviteMemberRequest request,
                        String inviterId) {
                verifyOwner(organizationId, inviterId);

                User invitedUser = userRepository.findByEmail(request.getEmail())
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Check if already a member
                Optional<OrganizationMember> existing = organizationMemberRepository
                                .findByUserIdAndOrganizationId(invitedUser.getId(), organizationId);
                if (existing.isPresent()) {
                        throw new RuntimeException("User is already a member of this organization");
                }

                OrganizationMember member = OrganizationMember.builder()
                                .userId(invitedUser.getId())
                                .organizationId(organizationId)
                                .role(request.getRole() != null ? request.getRole() : OrganizationRole.ANALYST)
                                .createdAt(Instant.now())
                                .build();

                OrganizationMember saved = organizationMemberRepository.save(member);
                return mapToMemberResponse(saved);
        }

        @Transactional
        public void removeMember(String organizationId, String userIdToRemove, String requesterId) {
                verifyOwner(organizationId, requesterId);

                // Cannot remove yourself if you are the only owner
                if (userIdToRemove.equals(requesterId)) {
                        long ownerCount = organizationMemberRepository.findByOrganizationId(organizationId).stream()
                                        .filter(m -> m.getRole() == OrganizationRole.OWNER)
                                        .count();
                        if (ownerCount <= 1) {
                                throw new RuntimeException("Cannot remove the only owner of the organization");
                        }
                }

                organizationMemberRepository.deleteByUserIdAndOrganizationId(userIdToRemove, organizationId);
        }

        @Transactional
        public void transferOwnership(String organizationId, TransferOwnershipRequest request, String currentOwnerId) {
                verifyOwner(organizationId, currentOwnerId);

                OrganizationMember currentOwnerMembership = organizationMemberRepository
                                .findByUserIdAndOrganizationId(currentOwnerId, organizationId)
                                .orElseThrow(() -> new RuntimeException("Current owner membership not found"));

                OrganizationMember newOwnerMembership = organizationMemberRepository
                                .findByUserIdAndOrganizationId(request.getNewOwnerUserId(), organizationId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Target user is not a member of this organization"));

                // Swap roles
                newOwnerMembership.setRole(OrganizationRole.OWNER);
                currentOwnerMembership.setRole(OrganizationRole.ANALYST);

                organizationMemberRepository.save(newOwnerMembership);
                organizationMemberRepository.save(currentOwnerMembership);
        }

        @Transactional
        public void adminTransferOwnership(String organizationId, String newOwnerUserId) {
                OrganizationMember newOwnerMembership = organizationMemberRepository
                                .findByUserIdAndOrganizationId(newOwnerUserId, organizationId)
                                .orElseThrow(() -> new RuntimeException(
                                                "Target user is not a member of this organization"));

                // Demote existing owners
                List<OrganizationMember> owners = organizationMemberRepository.findByOrganizationId(organizationId)
                                .stream()
                                .filter(m -> m.getRole() == OrganizationRole.OWNER)
                                .collect(Collectors.toList());

                for (OrganizationMember owner : owners) {
                        owner.setRole(OrganizationRole.ANALYST);
                        organizationMemberRepository.save(owner);
                }

                // Promote new user
                newOwnerMembership.setRole(OrganizationRole.OWNER);
                organizationMemberRepository.save(newOwnerMembership);
        }

        /**
         * Platform-admin: assign any platform user as owner of an org.
         * Works even if they are not yet a member (adds them automatically).
         */
        @Transactional
        public void adminAssignOwner(String organizationId, String newOwnerUserId) {
                // Verify org exists
                organizationRepository.findById(organizationId)
                                .orElseThrow(() -> new RuntimeException("Organization not found"));

                // Verify user exists
                userRepository.findById(newOwnerUserId)
                                .orElseThrow(() -> new RuntimeException("User not found"));

                // Demote all existing owners
                organizationMemberRepository.findByOrganizationId(organizationId).stream()
                                .filter(m -> m.getRole() == OrganizationRole.OWNER)
                                .forEach(m -> {
                                        m.setRole(OrganizationRole.ANALYST);
                                        organizationMemberRepository.save(m);
                                });

                // Upsert: find existing membership or create new one
                OrganizationMember membership = organizationMemberRepository
                                .findByUserIdAndOrganizationId(newOwnerUserId, organizationId)
                                .orElseGet(() -> OrganizationMember.builder()
                                                .userId(newOwnerUserId)
                                                .organizationId(organizationId)
                                                .createdAt(Instant.now())
                                                .build());

                membership.setRole(OrganizationRole.OWNER);
                organizationMemberRepository.save(membership);
        }

        public List<OrganizationResponse> getAllOrganizations() {
                return organizationRepository.findAll().stream()
                                .map(this::mapToResponse)
                                .collect(Collectors.toList());
        }

        @Transactional
        public OrganizationResponse updateOrganizationStatus(String organizationId, boolean enabled) {
                Organization org = organizationRepository.findById(organizationId)
                                .orElseThrow(() -> new RuntimeException("Organization not found"));
                org.setEnabled(enabled);
                return mapToResponse(organizationRepository.save(org));
        }

        private void verifyOwner(String organizationId, String userId) {
                OrganizationMember member = organizationMemberRepository
                                .findByUserIdAndOrganizationId(userId, organizationId)
                                .orElseThrow(() -> new RuntimeException("You are not a member of this organization"));

                if (member.getRole() != OrganizationRole.OWNER) {
                        throw new RuntimeException("Requires OWNER role");
                }
        }

        private OrganizationResponse mapToResponse(Organization org) {
                long membersCount = organizationMemberRepository.findByOrganizationId(org.getId()).size();
                return new OrganizationResponse(
                                org.getId(),
                                org.getName(),
                                org.getCreatedAt(),
                                org.getCreatedBy(),
                                org.isEnabled(),
                                membersCount);
        }

        private OrganizationMemberResponse mapToMemberResponse(OrganizationMember member) {
                User user = userRepository.findById(member.getUserId()).orElse(null);
                return new OrganizationMemberResponse(
                                member.getId(),
                                member.getUserId(),
                                user != null ? user.getEmail() : "Unknown",
                                user != null ? user.getFullName() : "Unknown",
                                member.getOrganizationId(),
                                member.getRole(),
                                member.getCreatedAt());
        }
}

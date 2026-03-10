package com.risktrace.user_service.Repository;

import com.risktrace.user_service.Model.OrganizationMember;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrganizationMemberRepository extends MongoRepository<OrganizationMember, String> {
    List<OrganizationMember> findByUserId(String userId);

    List<OrganizationMember> findByOrganizationId(String organizationId);

    Optional<OrganizationMember> findByUserIdAndOrganizationId(String userId, String organizationId);

    void deleteByUserIdAndOrganizationId(String userId, String organizationId);

    void deleteByUserId(String userId);
}

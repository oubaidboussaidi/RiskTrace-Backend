package com.risktrace.site_service.Service;

import com.risktrace.site_service.DTO.SiteRequest;
import com.risktrace.site_service.DTO.SiteResponse;
import com.risktrace.site_service.Model.Site;
import com.risktrace.site_service.Repository.SiteRepository;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class SiteService {

    private final SiteRepository siteRepository;

    public SiteService(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    public List<SiteResponse> getAllSites(String userId) {
        return siteRepository.findByUserId(userId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public List<SiteResponse> getSitesByOrganization(String organizationId) {
        return siteRepository.findByOrganizationId(organizationId)
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public SiteResponse createSite(SiteRequest request, String userId) {

        Site site = new Site();
        site.setUserId(userId);
        site.setOrganizationId(request.getOrganizationId());
        site.setSiteName(request.getSiteName());
        site.setDomain(request.getDomain());
        site.setApiKey(generateApiKey());
        site.setStatus("ACTIVE");
        site.setCreatedAt(new Date());

        return mapToResponse(siteRepository.save(site));
    }

    public SiteResponse regenerateKey(String id) {
        Site site = siteRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Site not found"));

        site.setApiKey(generateApiKey());
        return mapToResponse(siteRepository.save(site));
    }

    public void deleteSite(String id) {
        if (!siteRepository.existsById(id)) {
            throw new RuntimeException("Site not found");
        }
        siteRepository.deleteById(id);
    }

    private SiteResponse mapToResponse(Site site) {
        return new SiteResponse(
                site.getId(),
                site.getSiteName(),
                site.getDomain(),
                site.getApiKey(),
                site.getStatus(),
                site.getOrganizationId(),
                site.getCreatedAt(),
                site.getLastActive());
    }

    private String generateApiKey() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 16)
                .toUpperCase();
    }
}

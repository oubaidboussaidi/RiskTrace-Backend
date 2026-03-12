package com.risktrace.site_service.Controller;

import com.risktrace.site_service.DTO.SiteRequest;
import com.risktrace.site_service.DTO.SiteResponse;
import com.risktrace.site_service.Service.SiteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/sites")
public class SiteController {

    private final SiteService siteService;

    public SiteController(SiteService siteService) {
        this.siteService = siteService;
    }

    /** List sites for the authenticated user (backward-compatible) */
    @GetMapping
    public List<SiteResponse> getAllSites(
            @RequestHeader("X-User-Id") String userId) {
        return siteService.getAllSites(userId);
    }

    /** List sites belonging to a specific organization */
    @GetMapping("/org/{organizationId}")
    public ResponseEntity<List<SiteResponse>> getSitesByOrganization(
            @PathVariable String organizationId) {
        return ResponseEntity.ok(siteService.getSitesByOrganization(organizationId));
    }

    @PostMapping
    public ResponseEntity<SiteResponse> createSite(
            @Valid @RequestBody SiteRequest request,
            @RequestHeader("X-User-Id") String userId) {

        SiteResponse response = siteService.createSite(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}/regenerate-key")
    public ResponseEntity<SiteResponse> regenerateKey(@PathVariable String id) {
        return ResponseEntity.ok(siteService.regenerateKey(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSite(@PathVariable String id) {
        siteService.deleteSite(id);
        return ResponseEntity.noContent().build();
    }
}

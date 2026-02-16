package com.risktrace.site_service.Controller;

import com.risktrace.site_service.Model.Site;
import com.risktrace.site_service.Repository.SiteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/sites")
public class SiteController {

    @Autowired
    private SiteRepository siteRepository;

    /** List sites for the authenticated user */
    @GetMapping
    public List<Site> getAllSites(@RequestHeader(value = "X-User-Id", required = false) String userId) {
        if (userId == null)
            return List.of();
        return siteRepository.findByUserId(userId);
    }

    /**
     * Create a new site.
     * The gateway forwards the authenticated user's ID in the X-User-Id header.
     * Body: { "siteName": "...", "domain": "..." }
     */
    @PostMapping
    public ResponseEntity<?> createSite(
            @RequestBody Map<String, String> body,
            @RequestHeader(value = "X-User-Id", required = false) String userId) {

        String siteName = body.get("siteName");
        String domain = body.get("domain");

        if (siteName == null || siteName.isBlank() || domain == null || domain.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "siteName and domain are required"));
        }

        Site site = new Site();
        site.setUserId(userId); // owner from JWT (forwarded by gateway)
        site.setSiteName(siteName);
        site.setDomain(domain);
        site.setApiKey(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
        site.setStatus("ACTIVE");
        site.setCreatedAt(new Date());

        return ResponseEntity.status(HttpStatus.CREATED).body(siteRepository.save(site));
    }

    /** Regenerate the API key for a site */
    @PutMapping("/{id}/regenerate-key")
    public ResponseEntity<Site> regenerateKey(@PathVariable String id) {
        return siteRepository.findById(id)
                .map(site -> {
                    site.setApiKey(UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase());
                    return ResponseEntity.ok(siteRepository.save(site));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /** Delete a site */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteSite(@PathVariable String id) {
        if (!siteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        siteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }
}

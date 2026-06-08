package com.risktrace.site_service.Service;

import com.risktrace.site_service.DTO.SiteRequest;
import com.risktrace.site_service.DTO.SiteResponse;
import com.risktrace.site_service.Model.Site;
import com.risktrace.site_service.Repository.SiteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class SiteServiceTest {

    @Mock
    private SiteRepository siteRepository;

    @InjectMocks
    private SiteService siteService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllSites_Success() {
        String userId = "user123";
        Site site1 = new Site("1", userId, "org123", "Site One", "one.com", "KEY1", "ACTIVE", null, null);
        Site site2 = new Site("2", userId, "org123", "Site Two", "two.com", "KEY2", "ACTIVE", null, null);

        when(siteRepository.findByUserId(userId)).thenReturn(Arrays.asList(site1, site2));

        List<SiteResponse> responses = siteService.getAllSites(userId);

        assertNotNull(responses);
        assertEquals(2, responses.size());
        assertEquals("Site One", responses.get(0).getSiteName());
        assertEquals("Site Two", responses.get(1).getSiteName());
        verify(siteRepository, times(1)).findByUserId(userId);
    }

    @Test
    void testGetSitesByOrganization_Success() {
        String orgId = "org123";
        Site site = new Site("1", "user123", orgId, "Site One", "one.com", "KEY1", "ACTIVE", null, null);

        when(siteRepository.findByOrganizationId(orgId)).thenReturn(Arrays.asList(site));

        List<SiteResponse> responses = siteService.getSitesByOrganization(orgId);

        assertNotNull(responses);
        assertEquals(1, responses.size());
        assertEquals("Site One", responses.get(0).getSiteName());
        verify(siteRepository, times(1)).findByOrganizationId(orgId);
    }

    @Test
    void testCreateSite_Success() {
        String userId = "user123";
        SiteRequest request = new SiteRequest();
        request.setSiteName("Site One");
        request.setDomain("one.com");
        request.setOrganizationId("org123");
        Site savedSite = new Site("1", userId, "org123", "Site One", "one.com", "GENERATEDKEY1234", "ACTIVE", null, null);

        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        SiteResponse response = siteService.createSite(request, userId);

        assertNotNull(response);
        assertEquals("Site One", response.getSiteName());
        assertEquals("ACTIVE", response.getStatus());
        assertEquals("org123", response.getOrganizationId());
        verify(siteRepository, times(1)).save(any(Site.class));
    }

    @Test
    void testRegenerateKey_Success() {
        String siteId = "1";
        Site existingSite = new Site(siteId, "user123", "org123", "Site One", "one.com", "OLD_KEY", "ACTIVE", null, null);
        Site savedSite = new Site(siteId, "user123", "org123", "Site One", "one.com", "NEW_KEY", "ACTIVE", null, null);

        when(siteRepository.findById(siteId)).thenReturn(Optional.of(existingSite));
        when(siteRepository.save(any(Site.class))).thenReturn(savedSite);

        SiteResponse response = siteService.regenerateKey(siteId);

        assertNotNull(response);
        verify(siteRepository, times(1)).findById(siteId);
        verify(siteRepository, times(1)).save(any(Site.class));
    }

    @Test
    void testRegenerateKey_NotFound() {
        String siteId = "invalid_id";
        when(siteRepository.findById(siteId)).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class, () -> siteService.regenerateKey(siteId));
        verify(siteRepository, times(1)).findById(siteId);
        verify(siteRepository, never()).save(any(Site.class));
    }

    @Test
    void testDeleteSite_Success() {
        String siteId = "1";
        when(siteRepository.existsById(siteId)).thenReturn(true);
        doNothing().when(siteRepository).deleteById(siteId);

        assertDoesNotThrow(() -> siteService.deleteSite(siteId));

        verify(siteRepository, times(1)).existsById(siteId);
        verify(siteRepository, times(1)).deleteById(siteId);
    }

    @Test
    void testDeleteSite_NotFound() {
        String siteId = "invalid_id";
        when(siteRepository.existsById(siteId)).thenReturn(false);

        assertThrows(RuntimeException.class, () -> siteService.deleteSite(siteId));

        verify(siteRepository, times(1)).existsById(siteId);
        verify(siteRepository, never()).deleteById(anyString());
    }
}

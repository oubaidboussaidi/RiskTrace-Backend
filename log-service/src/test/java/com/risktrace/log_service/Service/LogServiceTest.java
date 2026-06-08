package com.risktrace.log_service.Service;

import com.risktrace.log_service.DTO.LogRequestDto;
import com.risktrace.log_service.DTO.LogResponseDto;
import com.risktrace.log_service.Model.Log;
import com.risktrace.log_service.Model.SiteRef;
import com.risktrace.log_service.Repository.log.LogRepository;
import com.risktrace.log_service.Repository.site.SiteRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LogServiceTest {

    @Mock
    private LogRepository logRepository;

    @Mock
    private SiteRefRepository siteRefRepository;

    @Mock
    private SessionAggregator sessionAggregator;

    @Mock
    private MlClient mlClient;

    @Mock
    private AlertClient alertClient;

    @InjectMocks
    private LogService logService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void testGetAllLogs_Success() {
        Log log1 = new Log();
        log1.setId("1");
        Log log2 = new Log();
        log2.setId("2");

        when(logRepository.findAll()).thenReturn(Arrays.asList(log1, log2));

        List<LogResponseDto> dtos = logService.getAllLogs();

        assertNotNull(dtos);
        assertEquals(2, dtos.size());
        verify(logRepository, times(1)).findAll();
    }

    @Test
    void testGetLogsBySite_Success() {
        String siteId = "site123";
        Log log = new Log();
        log.setId("1");
        log.setSiteId(siteId);

        when(logRepository.findBySiteId(siteId)).thenReturn(Arrays.asList(log));

        List<LogResponseDto> dtos = logService.getLogsBySite(siteId);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals(siteId, dtos.get(0).getSiteId());
        verify(logRepository, times(1)).findBySiteId(siteId);
    }

    @Test
    void testGetLogsByOrganization_Success() {
        String orgId = "org123";
        Log log = new Log();
        log.setId("1");
        log.setOrganizationId(orgId);

        when(logRepository.findByOrganizationId(orgId)).thenReturn(Arrays.asList(log));

        List<LogResponseDto> dtos = logService.getLogsByOrganization(orgId);

        assertNotNull(dtos);
        assertEquals(1, dtos.size());
        assertEquals(orgId, dtos.get(0).getOrganizationId());
        verify(logRepository, times(1)).findByOrganizationId(orgId);
    }

    @Test
    void testCollectLogs_EmptyPayload() {
        assertThrows(ResponseStatusException.class, () -> logService.collectLogs(null, "127.0.0.1"));
        assertThrows(ResponseStatusException.class, () -> logService.collectLogs(Collections.emptyList(), "127.0.0.1"));
    }

    @Test
    void testCollectLogs_MissingApiKey() {
        LogRequestDto dto = new LogRequestDto();
        dto.setApiKey(null);

        assertThrows(ResponseStatusException.class, () -> logService.collectLogs(Arrays.asList(dto), "127.0.0.1"));
    }

    @Test
    void testCollectLogs_InvalidApiKey() {
        LogRequestDto dto = new LogRequestDto();
        dto.setApiKey("INVALID_KEY");

        when(siteRefRepository.findByApiKey("INVALID_KEY")).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> logService.collectLogs(Arrays.asList(dto), "127.0.0.1"));
    }

    @Test
    void testCollectLogs_InactiveSite() {
        LogRequestDto dto = new LogRequestDto();
        dto.setApiKey("INACTIVE_KEY");

        SiteRef siteRef = new SiteRef();
        siteRef.setId("site123");
        siteRef.setApiKey("INACTIVE_KEY");
        siteRef.setOrganizationId("org123");
        siteRef.setStatus("INACTIVE");

        when(siteRefRepository.findByApiKey("INACTIVE_KEY")).thenReturn(Optional.of(siteRef));

        assertThrows(ResponseStatusException.class, () -> logService.collectLogs(Arrays.asList(dto), "127.0.0.1"));
    }

    @Test
    void testCollectLogs_SuccessWithoutMl() {
        LogRequestDto dto = new LogRequestDto();
        dto.setApiKey("VALID_KEY");
        dto.setSessionId("session123");
        dto.setType("page_load");
        dto.setUrl("/dashboard");
        dto.setMethod("GET");
        dto.setStatusCode(200);

        SiteRef siteRef = new SiteRef();
        siteRef.setId("site123");
        siteRef.setApiKey("VALID_KEY");
        siteRef.setOrganizationId("org123");
        siteRef.setStatus("ACTIVE");
        Log savedLog = new Log();
        savedLog.setId("log123");
        savedLog.setSiteId("site123");
        savedLog.setOrganizationId("org123");
        savedLog.setSessionId("session123");

        when(siteRefRepository.findByApiKey("VALID_KEY")).thenReturn(Optional.of(siteRef));
        when(logRepository.saveAll(anyList())).thenReturn(Arrays.asList(savedLog));
        // Mock ML client returning null to trigger ML skip exception handling gracefully
        when(mlClient.predictBatch(any())).thenReturn(null);

        List<LogResponseDto> response = logService.collectLogs(Arrays.asList(dto), "192.168.1.1");

        assertNotNull(response);
        assertEquals(1, response.size());
        assertEquals("log123", response.get(0).getId());
        verify(logRepository, times(1)).saveAll(anyList());
    }

    @Test
    void testMarkLogAsSuspicious_Success() {
        String logId = "log123";
        Log log = new Log();
        log.setId(logId);
        log.setIsSuspicious(false);

        Log savedLog = new Log();
        savedLog.setId(logId);
        savedLog.setIsSuspicious(true);

        when(logRepository.findById(logId)).thenReturn(Optional.of(log));
        when(logRepository.save(any(Log.class))).thenReturn(savedLog);
        doNothing().when(alertClient).sendAlert(any(Log.class), anyString(), anyString(), anyString());

        LogResponseDto response = logService.markLogAsSuspicious(logId);

        assertNotNull(response);
        assertTrue(response.getIsSuspicious());
        verify(logRepository, times(1)).findById(logId);
        verify(logRepository, times(1)).save(any(Log.class));
        verify(alertClient, times(1)).sendAlert(any(Log.class), eq("SUSPICIOUS_ACTIVITY"), eq("HIGH"), anyString());
    }

    @Test
    void testMarkLogAsSuspicious_NotFound() {
        String logId = "invalid_id";
        when(logRepository.findById(logId)).thenReturn(Optional.empty());

        assertThrows(ResponseStatusException.class, () -> logService.markLogAsSuspicious(logId));
        verify(logRepository, times(1)).findById(logId);
        verify(logRepository, never()).save(any(Log.class));
    }
}

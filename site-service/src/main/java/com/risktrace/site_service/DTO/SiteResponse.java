package com.risktrace.site_service.DTO;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Date;

@Data
@AllArgsConstructor
public class SiteResponse {

    private String id;
    private String siteName;
    private String domain;
    private String apiKey;
    private String status;
    private Date createdAt;
    private Date lastActive;
}
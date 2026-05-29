package com.risktrace.log_service.Config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.risktrace.log_service.Repository.site.SiteRefRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

/**
 * Secondary MongoTemplate that points to sitedb.
 * SiteRefRepository uses this template to read the `sites` collection
 * for apiKey validation without an inter-service HTTP call.
 */
@Configuration
// @EnableMongoRepositories(basePackages =
// "com.risktrace.log_service.Repository", includeFilters =
// @org.springframework.context.annotation.ComponentScan.Filter(type =
// org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE, classes =
// SiteRefRepository.class), mongoTemplateRef = "siteMongoTemplate")

@EnableMongoRepositories(basePackages = "com.risktrace.log_service.Repository.site", mongoTemplateRef = "siteMongoTemplate")
public class SiteMongoConfig {

    @Value("${app.sitedb.uri:mongodb://localhost:27017/sitedb}")
    private String siteDbUri;

    @Value("${app.sitedb.name:risktrace_site}")
    private String siteDbName;

    @Bean(name = "siteMongoClient")
    public MongoClient siteMongoClient() {
        System.out.println("[LogService] SiteRef database URI: " + siteDbUri);
        return MongoClients.create(siteDbUri);
    }

    @Bean(name = "siteMongoDatabaseFactory")
    public MongoDatabaseFactory siteMongoDatabaseFactory() {
        return new SimpleMongoClientDatabaseFactory(siteMongoClient(), siteDbName);
    }

    @Bean(name = "siteMongoTemplate")
    public MongoTemplate siteMongoTemplate() {
        return new MongoTemplate(siteMongoDatabaseFactory());
    }
}

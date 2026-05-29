package com.risktrace.log_service.Config;

import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.SimpleMongoClientDatabaseFactory;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoRepositories(
        basePackages = "com.risktrace.log_service.Repository.log",
        mongoTemplateRef = "mongoTemplate"
)
public class PrimaryMongoConfig {
    @Value("${spring.data.mongodb.uri:mongodb://localhost:27017/logdb}")
    private String primaryDbUri;

    @Value("${app.logdb.name:risktrace_log}")
    private String primaryDbName;

    @Bean(name = "primaryMongoClient")
    @Primary
    public MongoClient primaryMongoClient() {
        return MongoClients.create(primaryDbUri);
    }

    @Bean(name = "primaryMongoDatabaseFactory")
    @Primary
    public MongoDatabaseFactory primaryMongoDatabaseFactory() {
        return new SimpleMongoClientDatabaseFactory(primaryMongoClient(), primaryDbName);
    }

    @Bean(name = "mongoTemplate")
    @Primary
    public MongoTemplate mongoTemplate() {
        return new MongoTemplate(primaryMongoDatabaseFactory());
    }
}

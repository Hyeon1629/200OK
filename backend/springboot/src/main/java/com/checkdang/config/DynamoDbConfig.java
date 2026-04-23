package com.checkdang.config;

import com.checkdang.domain.User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;
import java.time.Instant;

@Configuration
public class DynamoDbConfig {

    @Value("${aws.region}")
    private String region;

    @Value("${aws.credentials.access-key}")
    private String accessKey;

    @Value("${aws.credentials.secret-key}")
    private String secretKey;

    @Value("${aws.dynamodb.endpoint:}")
    private String endpoint;

    @Value("${aws.dynamodb.table-name}")
    private String tableName;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        DynamoDbClientBuilder builder = DynamoDbClient.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)));

        if (endpoint != null && !endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint));
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }

    @Bean
    public DynamoDbTable<User> userTable(DynamoDbEnhancedClient enhancedClient) {
        StaticTableSchema<User> schema = StaticTableSchema.builder(User.class)
                .newItemSupplier(User::new)
                .addAttribute(String.class, a -> a
                        .name("email")
                        .getter(User::getEmail)
                        .setter(User::setEmail)
                        .tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a
                        .name("id")
                        .getter(User::getId)
                        .setter(User::setId))
                .addAttribute(String.class, a -> a
                        .name("password")
                        .getter(User::getPassword)
                        .setter(User::setPassword))
                .addAttribute(String.class, a -> a
                        .name("name")
                        .getter(User::getName)
                        .setter(User::setName))
                .addAttribute(String.class, a -> a
                        .name("role")
                        .getter(u -> u.getRole() != null ? u.getRole().name() : null)
                        .setter((u, v) -> u.setRole(v != null ? User.Role.valueOf(v) : null)))
                .addAttribute(String.class, a -> a
                        .name("provider")
                        .getter(u -> u.getProvider() != null ? u.getProvider().name() : null)
                        .setter((u, v) -> u.setProvider(v != null ? User.Provider.valueOf(v) : null)))
                .addAttribute(String.class, a -> a
                        .name("providerId")
                        .getter(User::getProviderId)
                        .setter(User::setProviderId))
                .addAttribute(String.class, a -> a
                        .name("createdAt")
                        .getter(u -> u.getCreatedAt() != null ? u.getCreatedAt().toString() : null)
                        .setter((u, v) -> u.setCreatedAt(v != null ? Instant.parse(v) : null)))
                .build();

        return enhancedClient.table(tableName, schema);
    }
}

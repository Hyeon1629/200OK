package com.checkdang.config;

import com.checkdang.domain.BloodSugarRecord;
import com.checkdang.domain.RefreshToken;
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

    @Value("${aws.dynamodb.refresh-token-table-name}")
    private String refreshTokenTableName;

    @Value("${aws.dynamodb.blood-sugar-table-name}")
    private String bloodSugarTableName;

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
                // 기존 필드
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
                // A 담당 필드
                .addAttribute(Boolean.class, a -> a
                        .name("isGuest")
                        .getter(User::getIsGuest)
                        .setter(User::setIsGuest))
                .addAttribute(Boolean.class, a -> a
                        .name("termsAgreed")
                        .getter(User::getTermsAgreed)
                        .setter(User::setTermsAgreed))
                .addAttribute(String.class, a -> a
                        .name("gender")
                        .getter(u -> u.getGender() != null ? u.getGender().name() : null)
                        .setter((u, v) -> u.setGender(v != null ? User.Gender.valueOf(v) : null)))
                .addAttribute(String.class, a -> a
                        .name("birthDate")
                        .getter(User::getBirthDate)
                        .setter(User::setBirthDate))
                .addAttribute(Integer.class, a -> a
                        .name("height")
                        .getter(User::getHeight)
                        .setter(User::setHeight))
                .addAttribute(Integer.class, a -> a
                        .name("weight")
                        .getter(User::getWeight)
                        .setter(User::setWeight))
                .addAttribute(String.class, a -> a
                        .name("diabetesType")
                        .getter(u -> u.getDiabetesType() != null ? u.getDiabetesType().name() : null)
                        .setter((u, v) -> u.setDiabetesType(v != null ? User.DiabetesType.valueOf(v) : null)))
                // B 담당 필드
                .addAttribute(Boolean.class, a -> a
                        .name("isPremium")
                        .getter(User::getIsPremium)
                        .setter(User::setIsPremium))
                .addAttribute(String.class, a -> a
                        .name("premiumExpiresAt")
                        .getter(u -> u.getPremiumExpiresAt() != null ? u.getPremiumExpiresAt().toString() : null)
                        .setter((u, v) -> u.setPremiumExpiresAt(v != null ? Instant.parse(v) : null)))
                .addAttribute(String.class, a -> a
                        .name("familyGroupId")
                        .getter(User::getFamilyGroupId)
                        .setter(User::setFamilyGroupId))
                .addAttribute(String.class, a -> a
                        .name("familyRole")
                        .getter(u -> u.getFamilyRole() != null ? u.getFamilyRole().name() : null)
                        .setter((u, v) -> u.setFamilyRole(v != null ? User.FamilyRole.valueOf(v) : null)))
                .addAttribute(Integer.class, a -> a
                        .name("targetBloodSugar")
                        .getter(User::getTargetBloodSugar)
                        .setter(User::setTargetBloodSugar))
                .addAttribute(Boolean.class, a -> a
                        .name("samsungHealthConnected")
                        .getter(User::getSamsungHealthConnected)
                        .setter(User::setSamsungHealthConnected))
                .addAttribute(Boolean.class, a -> a
                        .name("notificationEnabled")
                        .getter(User::getNotificationEnabled)
                        .setter(User::setNotificationEnabled))
                .addAttribute(String.class, a -> a
                        .name("accountStatus")
                        .getter(u -> u.getAccountStatus() != null ? u.getAccountStatus().name() : null)
                        .setter((u, v) -> u.setAccountStatus(v != null ? User.AccountStatus.valueOf(v) : null)))
                .build();

        return enhancedClient.table(tableName, schema);
    }

    @Bean
    public DynamoDbTable<RefreshToken> refreshTokenTable(DynamoDbEnhancedClient enhancedClient) {
        StaticTableSchema<RefreshToken> schema = StaticTableSchema.builder(RefreshToken.class)
                .newItemSupplier(RefreshToken::new)
                .addAttribute(String.class, a -> a
                        .name("token")
                        .getter(RefreshToken::getToken)
                        .setter(RefreshToken::setToken)
                        .tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a
                        .name("userId")
                        .getter(RefreshToken::getUserId)
                        .setter(RefreshToken::setUserId))
                .addAttribute(Long.class, a -> a
                        .name("expiresAt")
                        .getter(RefreshToken::getExpiresAt)
                        .setter(RefreshToken::setExpiresAt))
                .addAttribute(String.class, a -> a
                        .name("createdAt")
                        .getter(RefreshToken::getCreatedAt)
                        .setter(RefreshToken::setCreatedAt))
                .build();

        return enhancedClient.table(refreshTokenTableName, schema);
    }

    @Bean
    public DynamoDbTable<BloodSugarRecord> bloodSugarTable(DynamoDbEnhancedClient enhancedClient) {
        StaticTableSchema<BloodSugarRecord> schema = StaticTableSchema.builder(BloodSugarRecord.class)
                .newItemSupplier(BloodSugarRecord::new)
                .addAttribute(String.class, a -> a
                        .name("user_date")
                        .getter(BloodSugarRecord::getUserDate)
                        .setter(BloodSugarRecord::setUserDate)
                        .tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a
                        .name("timestamp")
                        .getter(BloodSugarRecord::getTimestamp)
                        .setter(BloodSugarRecord::setTimestamp)
                        .tags(StaticAttributeTags.primarySortKey()))
                .addAttribute(Integer.class, a -> a
                        .name("level")
                        .getter(BloodSugarRecord::getLevel)
                        .setter(BloodSugarRecord::setLevel))
                .addAttribute(String.class, a -> a
                        .name("memo")
                        .getter(BloodSugarRecord::getMemo)
                        .setter(BloodSugarRecord::setMemo))
                .addAttribute(String.class, a -> a
                        .name("meal_timing")
                        .getter(BloodSugarRecord::getMealTiming)
                        .setter(BloodSugarRecord::setMealTiming))
                .build();

        return enhancedClient.table(bloodSugarTableName, schema);
    }
}

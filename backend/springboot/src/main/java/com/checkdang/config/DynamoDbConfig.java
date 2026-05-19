package com.checkdang.config;

import com.checkdang.domain.PaymentRecord;
import com.checkdang.domain.RefreshToken;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticAttributeTags;
import software.amazon.awssdk.enhanced.dynamodb.mapper.StaticTableSchema;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClientBuilder;

import java.net.URI;

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

    @Value("${aws.dynamodb.refresh-token-table-name}")
    private String refreshTokenTableName;

    @Value("${aws.dynamodb.payment-table-name}")
    private String paymentTableName;

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
    public DynamoDbTable<PaymentRecord> paymentTable(DynamoDbEnhancedClient enhancedClient) {
        StaticTableSchema<PaymentRecord> schema = StaticTableSchema.builder(PaymentRecord.class)
                .newItemSupplier(PaymentRecord::new)
                .addAttribute(String.class, a -> a
                        .name("userId")
                        .getter(PaymentRecord::getUserId)
                        .setter(PaymentRecord::setUserId)
                        .tags(StaticAttributeTags.primaryPartitionKey()))
                .addAttribute(String.class, a -> a
                        .name("orderId")
                        .getter(PaymentRecord::getOrderId)
                        .setter(PaymentRecord::setOrderId)
                        .tags(StaticAttributeTags.primarySortKey()))
                .addAttribute(String.class, a -> a
                        .name("paymentMethod")
                        .getter(PaymentRecord::getPaymentMethod)
                        .setter(PaymentRecord::setPaymentMethod))
                .addAttribute(String.class, a -> a
                        .name("tid")
                        .getter(PaymentRecord::getTid)
                        .setter(PaymentRecord::setTid))
                .addAttribute(String.class, a -> a
                        .name("itemName")
                        .getter(PaymentRecord::getItemName)
                        .setter(PaymentRecord::setItemName))
                .addAttribute(Integer.class, a -> a
                        .name("amount")
                        .getter(PaymentRecord::getAmount)
                        .setter(PaymentRecord::setAmount))
                .addAttribute(String.class, a -> a
                        .name("status")
                        .getter(PaymentRecord::getStatus)
                        .setter(PaymentRecord::setStatus))
                .addAttribute(Integer.class, a -> a
                        .name("premiumMonths")
                        .getter(PaymentRecord::getPremiumMonths)
                        .setter(PaymentRecord::setPremiumMonths))
                .addAttribute(String.class, a -> a
                        .name("approvedAt")
                        .getter(PaymentRecord::getApprovedAt)
                        .setter(PaymentRecord::setApprovedAt))
                .addAttribute(String.class, a -> a
                        .name("createdAt")
                        .getter(PaymentRecord::getCreatedAt)
                        .setter(PaymentRecord::setCreatedAt))
                .build();

        return enhancedClient.table(paymentTableName, schema);
    }
}

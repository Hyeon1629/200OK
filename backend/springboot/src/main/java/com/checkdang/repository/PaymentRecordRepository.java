package com.checkdang.repository;

import com.checkdang.domain.PaymentRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class PaymentRecordRepository {

    private final DynamoDbTable<PaymentRecord> paymentTable;

    public PaymentRecord save(PaymentRecord record) {
        paymentTable.putItem(record);
        return record;
    }

    public Optional<PaymentRecord> findByUserIdAndOrderId(String userId, String orderId) {
        Key key = Key.builder()
                .partitionValue(userId)
                .sortValue(orderId)
                .build();
        return Optional.ofNullable(paymentTable.getItem(key));
    }

    public Optional<PaymentRecord> findByUserIdAndOrderIdAndStatus(
            String userId, String orderId, PaymentRecord.PaymentStatus status) {
        return findByUserIdAndOrderId(userId, orderId)
                .filter(r -> status.name().equals(r.getStatus()));
    }

    public List<PaymentRecord> findByUserIdOrderByCreatedAtDesc(String userId) {
        QueryConditional condition = QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build());
        return paymentTable.query(condition).items().stream()
                .sorted(Comparator.comparing(PaymentRecord::getCreatedAt, Comparator.reverseOrder()))
                .toList();
    }

    // purchaseToken(orderId)으로 역조회 — RTDN 수신 시 userId를 모를 때 사용
    // payment_records 건수가 적어 scan 비용 허용 범위 내
    public Optional<PaymentRecord> findByOrderId(String orderId) {
        Expression filter = Expression.builder()
                .expression("orderId = :orderId")
                .expressionValues(Map.of(":orderId", AttributeValue.fromS(orderId)))
                .build();
        return paymentTable.scan(r -> r.filterExpression(filter))
                .items()
                .stream()
                .findFirst();
    }
}

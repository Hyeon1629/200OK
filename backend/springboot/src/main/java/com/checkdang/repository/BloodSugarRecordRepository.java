package com.checkdang.repository;

import com.checkdang.domain.BloodSugarRecord;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BloodSugarRecordRepository {

    private final DynamoDbTable<BloodSugarRecord> bloodSugarTable;

    public void save(BloodSugarRecord record) {
        bloodSugarTable.putItem(record);
    }

    public List<BloodSugarRecord> findByUserAndDate(String userId, String date) {
        String userDate = userId + "#" + date;
        QueryConditional condition = QueryConditional
                .keyEqualTo(Key.builder().partitionValue(userDate).build());
        return bloodSugarTable.query(condition).items().stream().toList();
    }
}

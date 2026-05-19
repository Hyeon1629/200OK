package com.checkdang.repository;

import com.checkdang.domain.RefreshToken;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

@Repository
@RequiredArgsConstructor
public class RefreshTokenRepository {

    private final DynamoDbTable<RefreshToken> refreshTokenTable;

    public RefreshToken save(RefreshToken refreshToken) {
        refreshTokenTable.putItem(refreshToken);
        return refreshToken;
    }

    public Optional<RefreshToken> findByToken(String token) {
        Key key = Key.builder().partitionValue(token).build();
        return Optional.ofNullable(refreshTokenTable.getItem(key));
    }

    public void deleteByToken(String token) {
        Key key = Key.builder().partitionValue(token).build();
        refreshTokenTable.deleteItem(key);
    }

    public void deleteAllByUserId(String userId) {
        DynamoDbIndex<RefreshToken> index = refreshTokenTable.index("userId-index");
        index.query(QueryConditional.keyEqualTo(
                Key.builder().partitionValue(userId).build()))
                .stream()
                .flatMap(page -> page.items().stream())
                .forEach(token -> deleteByToken(token.getToken()));
    }
}

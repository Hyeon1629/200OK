package com.checkdang.repository;

import com.checkdang.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final DynamoDbTable<User> userTable;

    public User save(User user) {
        if (user.getId() == null) {
            user.setId(UUID.randomUUID().toString());
        }
        if (user.getCreatedAt() == null) {
            user.setCreatedAt(Instant.now());
        }
        if (user.getProvider() == null) {
            user.setProvider(User.Provider.LOCAL);
        }
        userTable.putItem(user);
        return user;
    }

    public Optional<User> findByEmail(String email) {
        Key key = Key.builder().partitionValue(email).build();
        return Optional.ofNullable(userTable.getItem(key));
    }

    public boolean existsByEmail(String email) {
        Key key = Key.builder().partitionValue(email).build();
        return userTable.getItem(key) != null;
    }
}

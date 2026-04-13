package com.checkdang.repository;

import com.checkdang.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    Optional<User> findByCognitoSub(String cognitoSub);
    boolean existsByEmail(String email);
}

package com.hnh.example.transaction_example.repository;

import java.util.Optional;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.hnh.example.transaction_example.domain.User;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    String USERS_BY_EMAIL_CACHE = "usersByEmail";

    @Query("SELECT u FROM User u LEFT JOIN FETCH u.authorities WHERE u.email = :email")
    @EntityGraph(attributePaths = "authorities")
    @Cacheable(cacheNames = USERS_BY_EMAIL_CACHE, key = "#email")
    Optional<User> findByEmailWithAuthorities(String email);
}

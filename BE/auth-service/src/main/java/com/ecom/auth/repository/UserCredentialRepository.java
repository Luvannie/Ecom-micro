package com.ecom.auth.repository;

import com.ecom.auth.domain.UserCredential;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface UserCredentialRepository extends JpaRepository<UserCredential, UUID> {
    Optional<UserCredential> findByEmail(String email);

    boolean existsByEmail(String email);
}

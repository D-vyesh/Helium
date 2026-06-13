package com.helium.core.authuser.infrastructure;

import com.helium.core.authuser.domain.Credential;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CredentialRepository extends JpaRepository<Credential, UUID> {
    Optional<Credential> findByUserId(UUID userId);
}

package com.helium.core.matching.infrastructure;

import com.helium.core.matching.domain.Execution;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExecutionRepository extends JpaRepository<Execution, UUID> {
    Optional<Execution> findByExecutionId(String executionId);
}

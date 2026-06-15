package com.helium.core.admin.infrastructure;

import com.helium.core.admin.domain.ManualReconciliationCase;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ManualReconciliationCaseRepository extends JpaRepository<ManualReconciliationCase, UUID> {
}

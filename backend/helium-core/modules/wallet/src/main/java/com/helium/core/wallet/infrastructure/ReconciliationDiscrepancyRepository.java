package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.ReconciliationDiscrepancy;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationDiscrepancyRepository extends JpaRepository<ReconciliationDiscrepancy, UUID> {
}


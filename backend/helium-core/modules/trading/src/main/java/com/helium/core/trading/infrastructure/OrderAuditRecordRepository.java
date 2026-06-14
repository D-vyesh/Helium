package com.helium.core.trading.infrastructure;

import com.helium.core.trading.domain.OrderAuditRecord;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderAuditRecordRepository extends JpaRepository<OrderAuditRecord, UUID> {
}

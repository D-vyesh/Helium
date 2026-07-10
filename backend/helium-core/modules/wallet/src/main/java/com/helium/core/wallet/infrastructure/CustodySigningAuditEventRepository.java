package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.CustodySigningAuditEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustodySigningAuditEventRepository extends JpaRepository<CustodySigningAuditEvent, UUID> {
    List<CustodySigningAuditEvent> findAllByWithdrawalIdOrderByOccurredAtDesc(UUID withdrawalId);
}

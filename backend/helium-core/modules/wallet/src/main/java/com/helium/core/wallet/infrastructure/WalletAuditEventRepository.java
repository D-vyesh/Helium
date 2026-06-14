package com.helium.core.wallet.infrastructure;

import com.helium.core.wallet.domain.WalletAuditEvent;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletAuditEventRepository extends JpaRepository<WalletAuditEvent, UUID> {
}


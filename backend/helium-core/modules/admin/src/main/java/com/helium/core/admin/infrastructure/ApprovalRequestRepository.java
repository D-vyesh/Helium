package com.helium.core.admin.infrastructure;

import com.helium.core.admin.domain.ApprovalRequest;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, UUID> {
}

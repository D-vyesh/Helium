package com.helium.core.admin.application;

import com.helium.core.admin.domain.AdminAuditAction;
import com.helium.core.admin.domain.AdminValidationException;
import com.helium.core.admin.domain.ManualReconciliationCase;
import com.helium.core.admin.infrastructure.ManualReconciliationCaseRepository;
import com.helium.core.admin.infrastructure.ReconciliationDiscrepancyRecordRepository;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ManualReconciliationService {
    private final AdminSecurityService securityService;
    private final AdminAuditService auditService;
    private final ReconciliationDiscrepancyRecordRepository discrepancyRepository;
    private final ManualReconciliationCaseRepository caseRepository;
    private final Clock clock;

    public ManualReconciliationService(
        AdminSecurityService securityService,
        AdminAuditService auditService,
        ReconciliationDiscrepancyRecordRepository discrepancyRepository,
        ManualReconciliationCaseRepository caseRepository,
        Clock clock
    ) {
        this.securityService = securityService;
        this.auditService = auditService;
        this.discrepancyRepository = discrepancyRepository;
        this.caseRepository = caseRepository;
        this.clock = clock;
    }

    @Transactional
    public UUID openCase(UUID discrepancyId) {
        String actorId = securityService.requireAdminActor();
        if (!discrepancyRepository.existsById(discrepancyId)) {
            throw new AdminValidationException("discrepancy record was not found");
        }
        ManualReconciliationCase reconciliationCase = caseRepository.save(ManualReconciliationCase.open(discrepancyId, actorId, clock.instant()));
        auditService.record(AdminAuditAction.MANUAL_RECONCILIATION_OPENED, actorId, "DISCREPANCY", discrepancyId.toString(), "manual reconciliation opened");
        return reconciliationCase.id();
    }

    @Transactional
    public void resolveCase(UUID caseId, String notes) {
        String actorId = securityService.requireAdminActor();
        ManualReconciliationCase reconciliationCase = caseRepository.findById(caseId)
            .orElseThrow(() -> new AdminValidationException("manual reconciliation case was not found"));
        reconciliationCase.resolve(notes, actorId, clock.instant());
        auditService.record(AdminAuditAction.MANUAL_RECONCILIATION_RESOLVED, actorId, "RECONCILIATION_CASE", caseId.toString(), "manual reconciliation resolved");
    }
}

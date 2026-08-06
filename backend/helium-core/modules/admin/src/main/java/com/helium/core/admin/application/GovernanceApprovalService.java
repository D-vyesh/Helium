package com.helium.core.admin.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.admin.domain.ApprovalRequest;
import com.helium.core.admin.infrastructure.ApprovalRequestRepository;
import com.helium.core.authuser.application.EmailService;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.RoleGrant;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GovernanceApprovalService {

    private static final Logger log = LoggerFactory.getLogger(GovernanceApprovalService.class);

    private final ApprovalRequestRepository approvalRepository;
    private final RoleGrantRepository roleGrantRepository;
    private final UserAccountRepository userAccountRepository;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;
    private final Map<String, GovernanceCommandHandler> handlers;

    public GovernanceApprovalService(
        ApprovalRequestRepository approvalRepository,
        RoleGrantRepository roleGrantRepository,
        UserAccountRepository userAccountRepository,
        EmailService emailService,
        ObjectMapper objectMapper,
        List<GovernanceCommandHandler> handlerList
    ) {
        this.approvalRepository = approvalRepository;
        this.roleGrantRepository = roleGrantRepository;
        this.userAccountRepository = userAccountRepository;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
        this.handlers = handlerList.stream()
            .collect(Collectors.toMap(GovernanceCommandHandler::supportedRequestType, h -> h));
    }

    @Transactional
    public ApprovalRequest initiateApproval(String requestType, String makerId, Role checkerRole, String payloadJson) {
        ApprovalRequest request = new ApprovalRequest(requestType, makerId, checkerRole, payloadJson);
        ApprovalRequest saved = approvalRepository.save(request);

        notifyApprovers(requestType, checkerRole, saved.id());

        return saved;
    }

    @Transactional
    public ApprovalRequest approveRequest(UUID requestId, String checkerId, Role currentRole) {
        ApprovalRequest request = approvalRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        
        if (request.checkerRole() != currentRole) {
            throw new SecurityException("User does not have the required role to approve this request");
        }

        request.approve(checkerId);
        
        GovernanceCommandHandler handler = handlers.get(request.requestType());
        if (handler != null) {
            try {
                JsonNode payload = objectMapper.readTree(request.payloadJson());
                handler.execute(payload);
            } catch (Exception e) {
                log.error("Failed to execute governance command handler for requestType={}", request.requestType(), e);
                throw new RuntimeException("Failed to execute governance command", e);
            }
        } else {
            log.warn("No GovernanceCommandHandler registered for requestType={}", request.requestType());
        }

        return approvalRepository.save(request);
    }

    @Transactional
    public ApprovalRequest rejectRequest(UUID requestId, String checkerId, Role currentRole) {
        ApprovalRequest request = approvalRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        
        if (request.checkerRole() != currentRole) {
            throw new SecurityException("User does not have the required role to reject this request");
        }

        request.reject(checkerId);
        return approvalRepository.save(request);
    }

    private void notifyApprovers(String requestType, Role checkerRole, UUID requestId) {
        try {
            List<RoleGrant> grants = roleGrantRepository.findAllByRoleAndRevokedAtIsNull(checkerRole);
            for (RoleGrant grant : grants) {
                userAccountRepository.findById(grant.userId()).ifPresent(approver -> {
                    emailService.sendGovernanceNotificationEmail(
                        approver.email(),
                        approver.displayName(),
                        requestType,
                        requestId.toString()
                    );
                });
            }
        } catch (Exception e) {
            log.error("Failed to send governance notification emails for requestId={}", requestId, e);
        }
    }
}

package com.helium.core.admin.application;

import com.helium.core.admin.domain.ApprovalRequest;
import com.helium.core.admin.infrastructure.ApprovalRequestRepository;
import com.helium.core.authuser.domain.Role;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class GovernanceApprovalService {

    private final ApprovalRequestRepository approvalRepository;

    public GovernanceApprovalService(ApprovalRequestRepository approvalRepository) {
        this.approvalRepository = approvalRepository;
    }

    public ApprovalRequest initiateApproval(String requestType, String makerId, Role checkerRole, String payloadJson) {
        ApprovalRequest request = new ApprovalRequest(requestType, makerId, checkerRole, payloadJson);
        return approvalRepository.save(request);
    }

    public ApprovalRequest approveRequest(UUID requestId, String checkerId, Role currentRole) {
        ApprovalRequest request = approvalRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        
        if (request.checkerRole() != currentRole) {
            throw new SecurityException("User does not have the required role to approve this request");
        }

        request.approve(checkerId);
        // Dispatch event or execute the payload logic here 
        // depending on requestType
        
        return approvalRepository.save(request);
    }

    public ApprovalRequest rejectRequest(UUID requestId, String checkerId, Role currentRole) {
        ApprovalRequest request = approvalRepository.findById(requestId)
            .orElseThrow(() -> new IllegalArgumentException("Request not found"));
        
        if (request.checkerRole() != currentRole) {
            throw new SecurityException("User does not have the required role to reject this request");
        }

        request.reject(checkerId);
        return approvalRepository.save(request);
    }
}

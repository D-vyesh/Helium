package com.helium.core.admin.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helium.core.admin.domain.ApprovalRequest;
import com.helium.core.admin.infrastructure.ApprovalRequestRepository;
import com.helium.core.authuser.application.EmailService;
import com.helium.core.authuser.domain.Role;
import com.helium.core.authuser.domain.UserAccount;
import com.helium.core.authuser.domain.RoleGrant;
import com.helium.core.authuser.infrastructure.RoleGrantRepository;
import com.helium.core.authuser.infrastructure.UserAccountRepository;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GovernanceApprovalServiceTest {

    private ApprovalRequestRepository approvalRepository;
    private RoleGrantRepository roleGrantRepository;
    private UserAccountRepository userAccountRepository;
    private EmailService emailService;
    private ObjectMapper objectMapper;
    private GovernanceCommandHandler testHandler;
    private GovernanceApprovalService service;

    @BeforeEach
    void setUp() {
        approvalRepository = Mockito.mock(ApprovalRequestRepository.class);
        roleGrantRepository = Mockito.mock(RoleGrantRepository.class);
        userAccountRepository = Mockito.mock(UserAccountRepository.class);
        emailService = Mockito.mock(EmailService.class);
        objectMapper = new ObjectMapper();
        testHandler = Mockito.mock(GovernanceCommandHandler.class);

        when(testHandler.supportedRequestType()).thenReturn("FEE_SCHEDULE_UPDATE");

        service = new GovernanceApprovalService(
            approvalRepository,
            roleGrantRepository,
            userAccountRepository,
            emailService,
            objectMapper,
            List.of(testHandler)
        );
    }

    @Test
    void initiateApprovalSavesRequestAndNotifiesApprovers() {
        UserAccount approver = UserAccount.register("approver@helium.com", "Approver", Instant.now());
        RoleGrant grant = RoleGrant.grant(approver.id(), Role.ADMIN, approver.id(), Instant.now());
        when(roleGrantRepository.findAllByRoleAndRevokedAtIsNull(Role.ADMIN)).thenReturn(List.of(grant));
        when(userAccountRepository.findById(approver.id())).thenReturn(Optional.of(approver));
        when(approvalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest request = service.initiateApproval("FEE_SCHEDULE_UPDATE", "maker1", Role.ADMIN, "{\"marketSymbol\":\"BTC-USDT\"}");

        assertThat(request.requestType()).isEqualTo("FEE_SCHEDULE_UPDATE");
        assertThat(request.makerId()).isEqualTo("maker1");
        assertThat(request.checkerRole()).isEqualTo(Role.ADMIN);

        verify(emailService).sendGovernanceNotificationEmail(
            eq("approver@helium.com"),
            eq("Approver"),
            eq("FEE_SCHEDULE_UPDATE"),
            any()
        );
    }

    @Test
    void approveRequestExecutesMatchingHandler() {
        UUID requestId = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest("FEE_SCHEDULE_UPDATE", "maker1", Role.ADMIN, "{\"marketSymbol\":\"BTC-USDT\"}");
        when(approvalRepository.findById(requestId)).thenReturn(Optional.of(request));
        when(approvalRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        ApprovalRequest approved = service.approveRequest(requestId, "checker1", Role.ADMIN);

        assertThat(approved.checkerId()).isEqualTo("checker1");
        verify(testHandler).execute(any());
    }

    @Test
    void approveRequestRejectsWrongRole() {
        UUID requestId = UUID.randomUUID();
        ApprovalRequest request = new ApprovalRequest("FEE_SCHEDULE_UPDATE", "maker1", Role.ADMIN, "{\"marketSymbol\":\"BTC-USDT\"}");
        when(approvalRepository.findById(requestId)).thenReturn(Optional.of(request));

        assertThatThrownBy(() -> service.approveRequest(requestId, "checker1", Role.USER))
            .isInstanceOf(SecurityException.class);
    }
}

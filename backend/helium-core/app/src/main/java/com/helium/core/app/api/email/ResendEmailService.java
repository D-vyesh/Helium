package com.helium.core.app.api.email;

import com.helium.core.authuser.application.EmailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import java.util.Map;

/**
 * Production email service backed by Resend HTTP API.
 * Bypasses Render's strict SMTP port blocking.
 */
@Service
public class ResendEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(ResendEmailService.class);

    private final EmailProperties props;
    private final EmailTemplates templates;
    private final RestClient restClient;
    private final String apiKey;

    public ResendEmailService(
            EmailProperties props, 
            EmailTemplates templates, 
            RestClient.Builder restClientBuilder,
            @Value("${helium.email.resend.api-key:}") String apiKey) {
        this.props = props;
        this.templates = templates;
        this.apiKey = apiKey;
        this.restClient = restClientBuilder.baseUrl("https://api.resend.com").build();
    }

    @Override
    @Async
    public void sendVerificationEmail(String toEmail, String displayName, String verificationUrl) {
        if (!props.enabled()) {
            log.info("[EMAIL-DISABLED] Verification email to {} url={}", toEmail, verificationUrl);
            return;
        }
        send(toEmail, "Verify your HELIUM account",
            templates.verificationHtml(displayName, verificationUrl));
    }

    @Override
    @Async
    public void sendPasswordResetEmail(String toEmail, String displayName, String resetUrl) {
        if (!props.enabled()) {
            log.info("[EMAIL-DISABLED] Password reset email to {} url={}", toEmail, resetUrl);
            return;
        }
        send(toEmail, "Reset your HELIUM password",
            templates.passwordResetHtml(displayName, resetUrl));
    }

    @Override
    @Async
    public void sendWelcomeEmail(String toEmail, String displayName) {
        if (!props.enabled()) {
            log.info("[EMAIL-DISABLED] Welcome email to {}", toEmail);
            return;
        }
        send(toEmail, "Welcome to HELIUM", templates.welcomeHtml(displayName));
    }

    @Override
    @Async
    public void sendSecurityAlertEmail(String toEmail, String displayName, String eventDescription, String ipAddress) {
        if (!props.enabled()) {
            log.info("[EMAIL-DISABLED] Security alert to {} event={}", toEmail, eventDescription);
            return;
        }
        send(toEmail, "HELIUM Security Alert", templates.securityAlertHtml(displayName, eventDescription, ipAddress));
    }

    @Override
    @Async
    public void sendWithdrawalConfirmationEmail(
        String toEmail,
        String displayName,
        String confirmationUrl,
        String assetCode,
        String amount,
        String destinationAddress
    ) {
        if (!props.enabled()) {
            log.info("[EMAIL-DISABLED] Withdrawal confirmation email to {}", toEmail);
            return;
        }
        send(
            toEmail,
            "Confirm your HELIUM withdrawal",
            templates.withdrawalConfirmationHtml(displayName, confirmationUrl, assetCode, amount, destinationAddress)
        );
    }

    @Override
    @Async
    public void sendGovernanceNotificationEmail(String toEmail, String displayName, String requestType, String requestId) {
        if (!props.enabled()) {
            log.info("[EMAIL-DISABLED] Governance notification to {} requestType={} requestId={}", toEmail, requestType, requestId);
            return;
        }
        send(
            toEmail,
            "HELIUM Governance Action Required: " + requestType,
            "<p>Hello " + displayName + ",</p><p>A new governance request of type <b>" + requestType + "</b> (ID: " + requestId + ") requires your approval.</p>"
        );
    }

    private void send(String to, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.error("Failed to send email to={} subject={}: Resend API key is missing", to, subject);
            return;
        }
        
        try {
            // For Resend Free Tier, you must send from onboarding@resend.dev unless you verify a domain.
            String from = "onboarding@resend.dev";

            Map<String, Object> payload = Map.of(
                "from", "Helium <" + from + ">",
                "to", to,
                "subject", subject,
                "html", htmlBody
            );

            restClient.post()
                .uri("/emails")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(payload)
                .retrieve()
                .toBodilessEntity();

            log.info("Resend API email sent to={} subject={}", to, subject);
        } catch (Exception e) {
            log.error("Failed to send Resend email to={} subject={}: {}", to, subject, e.getMessage(), e);
        }
    }
}

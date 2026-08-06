package com.helium.core.app.api.email;

import com.helium.core.authuser.application.EmailService;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Production email service backed by Spring's JavaMailSender.
 * Supports MailHog (dev), Gmail SMTP, Amazon SES, and SendGrid SMTP relay.
 * All sends are async to avoid blocking the request thread.
 *
 * Configuration (application.yml / env vars):
 *   spring.mail.host, spring.mail.port, spring.mail.username, spring.mail.password
 *   helium.email.from-address, helium.email.from-name, helium.email.base-url
 */
@Service
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    private final JavaMailSender mailSender;
    private final EmailProperties props;
    private final EmailTemplates templates;

    public SmtpEmailService(JavaMailSender mailSender, EmailProperties props, EmailTemplates templates) {
        this.mailSender = mailSender;
        this.props = props;
        this.templates = templates;
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
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(props.fromAddress(), props.fromName());
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(htmlBody, true);
            mailSender.send(message);
            log.debug("Email sent to={} subject={}", to, subject);
        } catch (MessagingException | java.io.UnsupportedEncodingException e) {
            log.error("Failed to send email to={} subject={}: {}", to, subject, e.getMessage(), e);
        }
    }
}

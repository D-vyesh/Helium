package com.helium.core.authuser.application;

/**
 * Port for sending transactional emails. Implementations are provided by the
 * infrastructure layer (SMTP / MailHog / SES / SendGrid).
 */
public interface EmailService {

    void sendVerificationEmail(String toEmail, String displayName, String verificationUrl);

    void sendPasswordResetEmail(String toEmail, String displayName, String resetUrl);

    void sendWelcomeEmail(String toEmail, String displayName);

    void sendSecurityAlertEmail(String toEmail, String displayName, String eventDescription, String ipAddress);

    void sendWithdrawalConfirmationEmail(
        String toEmail,
        String displayName,
        String confirmationUrl,
        String assetCode,
        String amount,
        String destinationAddress
    );

    void sendGovernanceNotificationEmail(String toEmail, String displayName, String requestType, String requestId);
}

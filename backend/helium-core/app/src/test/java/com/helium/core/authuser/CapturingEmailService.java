package com.helium.core.authuser;

import com.helium.core.authuser.application.EmailService;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
public class CapturingEmailService implements EmailService {
    private static final Map<String, String> VERIFICATION_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, String> PASSWORD_RESET_TOKENS = new ConcurrentHashMap<>();
    private static final Map<String, String> WITHDRAWAL_CONFIRMATION_TOKENS = new ConcurrentHashMap<>();

    @Override
    public void sendVerificationEmail(String toEmail, String displayName, String verificationUrl) {
        VERIFICATION_TOKENS.put(normalize(toEmail), tokenFrom(verificationUrl));
    }

    @Override
    public void sendPasswordResetEmail(String toEmail, String displayName, String resetUrl) {
        PASSWORD_RESET_TOKENS.put(normalize(toEmail), tokenFrom(resetUrl));
    }

    @Override
    public void sendWelcomeEmail(String toEmail, String displayName) {
    }

    @Override
    public void sendSecurityAlertEmail(String toEmail, String displayName, String eventDescription, String ipAddress) {
    }

    @Override
    public void sendWithdrawalConfirmationEmail(
        String toEmail,
        String displayName,
        String confirmationUrl,
        String assetCode,
        String amount,
        String destinationAddress
    ) {
        WITHDRAWAL_CONFIRMATION_TOKENS.put(normalize(toEmail), tokenFrom(confirmationUrl));
    }

    @Override
    public void sendGovernanceNotificationEmail(String toEmail, String displayName, String requestType, String requestId) {
    }

    public static String verificationToken(String email) {
        return requireToken(VERIFICATION_TOKENS, email);
    }

    public static String passwordResetToken(String email) {
        return requireToken(PASSWORD_RESET_TOKENS, email);
    }

    public static String withdrawalConfirmationToken(String email) {
        return requireToken(WITHDRAWAL_CONFIRMATION_TOKENS, email);
    }

    public static void clear() {
        VERIFICATION_TOKENS.clear();
        PASSWORD_RESET_TOKENS.clear();
        WITHDRAWAL_CONFIRMATION_TOKENS.clear();
    }

    private static String requireToken(Map<String, String> tokens, String email) {
        String token = tokens.get(normalize(email));
        if (token == null) {
            throw new AssertionError("no captured email token for " + email);
        }
        return token;
    }

    private static String tokenFrom(String url) {
        String query = URI.create(url).getRawQuery();
        if (query == null) {
            throw new AssertionError("email URL does not contain a token query parameter");
        }
        for (String part : query.split("&")) {
            int equals = part.indexOf('=');
            if (equals > 0 && "token".equals(part.substring(0, equals))) {
                return URI.create("http://localhost/?" + part).getQuery().substring("token=".length());
            }
        }
        throw new AssertionError("email URL does not contain a token query parameter");
    }

    private static String normalize(String email) {
        return email.trim().toLowerCase();
    }
}

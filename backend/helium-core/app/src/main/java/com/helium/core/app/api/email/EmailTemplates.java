package com.helium.core.app.api.email;

import org.springframework.stereotype.Component;

/**
 * HTML email templates for all transactional emails.
 * Uses inline styles for maximum email client compatibility.
 */
@Component
public class EmailTemplates {

    public String verificationHtml(String displayName, String verificationUrl) {
        return baseTemplate(
            "Verify your email address",
            """
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Hi %s,
            </p>
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Thank you for creating a HELIUM account. Please verify your email address
              to activate your account.
            </p>
            <p style="color:#f59e0b;font-size:14px;margin:0 0 24px">
              This link expires in <strong>24 hours</strong>.
            </p>
            """.formatted(escapeHtml(displayName)),
            "Verify Email Address",
            verificationUrl,
            """
            <p style="color:#64748b;font-size:13px;margin:24px 0 0">
              If you did not create a HELIUM account, you can safely ignore this email.
            </p>
            """
        );
    }

    public String passwordResetHtml(String displayName, String resetUrl) {
        return baseTemplate(
            "Reset your password",
            """
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Hi %s,
            </p>
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              We received a request to reset your HELIUM account password.
            </p>
            <p style="color:#f59e0b;font-size:14px;margin:0 0 24px">
              This link expires in <strong>30 minutes</strong> and can only be used once.
            </p>
            """.formatted(escapeHtml(displayName)),
            "Reset Password",
            resetUrl,
            """
            <p style="color:#64748b;font-size:13px;margin:24px 0 0">
              If you did not request a password reset, please contact support immediately.
              Your account may be at risk.
            </p>
            """
        );
    }

    public String welcomeHtml(String displayName) {
        return baseTemplate(
            "Welcome to HELIUM",
            """
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Hi %s,
            </p>
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Your HELIUM account is now active. You can start trading on the most
              secure institutional exchange.
            </p>
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              We strongly recommend enabling two-factor authentication (2FA) to protect
              your account.
            </p>
            """.formatted(escapeHtml(displayName)),
            "Go to Dashboard",
            "#",
            ""
        );
    }

    public String securityAlertHtml(String displayName, String eventDescription, String ipAddress) {
        return baseTemplate(
            "Security Alert",
            """
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Hi %s,
            </p>
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              A security event occurred on your HELIUM account:
            </p>
            <div style="background:#1e293b;border-left:4px solid #ef4444;padding:16px;margin:0 0 24px;border-radius:4px">
              <p style="color:#f1f5f9;font-size:15px;margin:0 0 8px"><strong>Event:</strong> %s</p>
              <p style="color:#94a3b8;font-size:14px;margin:0"><strong>IP Address:</strong> %s</p>
            </div>
            <p style="color:#f59e0b;font-size:14px;margin:0 0 24px">
              If this was not you, please reset your password and contact support immediately.
            </p>
            """.formatted(escapeHtml(displayName), escapeHtml(eventDescription), escapeHtml(ipAddress)),
            "Secure My Account",
            "#",
            ""
        );
    }

    public String withdrawalConfirmationHtml(
        String displayName,
        String confirmationUrl,
        String assetCode,
        String amount,
        String destinationAddress
    ) {
        return baseTemplate(
            "Confirm your withdrawal",
            """
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Hi %s,
            </p>
            <p style="color:#94a3b8;font-size:16px;line-height:1.6;margin:0 0 24px">
              Confirm this withdrawal only if you initiated it. The confirmation link expires in 15 minutes.
            </p>
            <div style="background:#0f172a;border:1px solid #334155;padding:16px;margin:0 0 24px;border-radius:4px">
              <p style="color:#f1f5f9;font-size:15px;margin:0 0 8px"><strong>Amount:</strong> %s %s</p>
              <p style="color:#94a3b8;font-size:13px;line-height:1.5;margin:0;word-break:break-all"><strong>Destination:</strong> %s</p>
            </div>
            """.formatted(
                escapeHtml(displayName),
                escapeHtml(amount),
                escapeHtml(assetCode),
                escapeHtml(destinationAddress)
            ),
            "Confirm Withdrawal",
            confirmationUrl,
            """
            <p style="color:#f59e0b;font-size:14px;margin:0">
              If you did not request this withdrawal, secure your account immediately.
            </p>
            """
        );
    }

    private String baseTemplate(String title, String bodyContent, String ctaText, String ctaUrl, String footer) {
        return """
            <!DOCTYPE html>
            <html lang="en">
            <head>
              <meta charset="UTF-8">
              <meta name="viewport" content="width=device-width,initial-scale=1">
              <title>%s</title>
            </head>
            <body style="margin:0;padding:0;background-color:#0f172a;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,sans-serif">
              <table width="100%%" cellpadding="0" cellspacing="0" style="background:#0f172a;padding:40px 20px">
                <tr>
                  <td align="center">
                    <table width="600" cellpadding="0" cellspacing="0" style="max-width:600px;width:100%%">
                      <!-- Header -->
                      <tr>
                        <td style="padding:0 0 32px">
                          <p style="margin:0;font-size:24px;font-weight:700;color:#06b6d4;letter-spacing:0.1em">
                            ⬡ HELIUM
                          </p>
                        </td>
                      </tr>
                      <!-- Card -->
                      <tr>
                        <td style="background:#1e293b;border-radius:12px;padding:40px;border:1px solid #334155">
                          <h1 style="color:#f1f5f9;font-size:24px;font-weight:700;margin:0 0 24px">%s</h1>
                          %s
                          <a href="%s"
                             style="display:inline-block;background:#06b6d4;color:#0f172a;font-weight:700;
                                    font-size:15px;padding:14px 32px;border-radius:8px;text-decoration:none;
                                    margin:0 0 24px">
                            %s
                          </a>
                          %s
                        </td>
                      </tr>
                      <!-- Footer -->
                      <tr>
                        <td style="padding:24px 0 0">
                          <p style="color:#475569;font-size:12px;margin:0;text-align:center">
                            © HELIUM Exchange · This is an automated message, please do not reply.
                          </p>
                        </td>
                      </tr>
                    </table>
                  </td>
                </tr>
              </table>
            </body>
            </html>
            """.formatted(
                escapeHtml(title), escapeHtml(title), bodyContent,
                ctaUrl, escapeHtml(ctaText), footer
            );
    }

    private static String escapeHtml(String input) {
        if (input == null) return "";
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;");
    }
}

import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

// Backend PasswordPolicy + AuthApiController require min 12 chars.
export const signupSchema = z.object({
  email: z.string().email("Invalid email address"),
  password: z.string().min(12, "Password must be at least 12 characters").max(200),
  confirmPassword: z.string().min(1, "Please confirm your password")
}).refine((data) => data.password === data.confirmPassword, {
  message: "Passwords do not match",
  path: ["confirmPassword"]
});

export const registerSchema = z.object({
  displayName: z.string().min(2).max(120),
  email: z.string().email(),
  password: z.string().min(12).max(200)
});

export const passwordResetSchema = z.object({
  email: z.string().email("Invalid email address")
});

export const resetPasswordSchema = z.object({
  token: z.string().min(8, "Invalid token"),
  newPassword: z.string().min(12, "Password must be at least 12 characters").max(200),
  confirmPassword: z.string().min(1, "Please confirm your password")
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: "Passwords do not match",
  path: ["confirmPassword"]
});

export const changePasswordSchema = z.object({
  currentPassword: z.string().min(1, "Current password is required"),
  newPassword: z.string().min(12, "Password must be at least 12 characters").max(200),
  confirmPassword: z.string().min(1, "Please confirm your password")
}).refine((data) => data.newPassword === data.confirmPassword, {
  message: "Passwords do not match",
  path: ["confirmPassword"]
});

export const emailVerificationSchema = z.object({
  token: z.string().min(8)
});

export const resendVerificationSchema = z.object({
  email: z.string().email("Invalid email address")
});

export const totpCodeSchema = z.object({
  code: z.string().length(6, "TOTP code must be 6 digits").regex(/^\d{6}$/, "TOTP code must be numeric")
});

export const totpChallengeSchema = z.object({
  code: z.string().length(6, "TOTP code must be 6 digits").regex(/^\d{6}$/, "TOTP code must be numeric")
});

export const backupCodeSchema = z.object({
  backupCode: z.string().min(9, "Invalid backup code format")
});

const decimalString = z
  .string()
  .min(1, "Required")
  .regex(/^\d+(\.\d+)?$/, "Must be a positive decimal number");

export const orderEntrySchema = z.object({
  side: z.enum(["BUY", "SELL"]),
  type: z.literal("LIMIT"),
  price: decimalString,
  quantity: decimalString
});

// Mirrors WalletApiController.WithdrawalRequest (clientRequestId is generated on submit).
export const withdrawalSchema = z.object({
  asset: z.string().min(2).max(32),
  network: z.string().min(2).max(40),
  amount: decimalString,
  destination: z.string().min(8).max(160),
  memo: z.string().max(120).optional().or(z.literal(""))
});

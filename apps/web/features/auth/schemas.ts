import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

// Backend PasswordPolicy + AuthApiController require min 12 chars.
export const registerSchema = z.object({
  displayName: z.string().min(2).max(120),
  email: z.string().email(),
  password: z.string().min(12).max(200)
});

export const passwordResetSchema = z.object({
  email: z.string().email()
});

export const emailVerificationSchema = z.object({
  token: z.string().min(8)
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

import { z } from "zod";

export const loginSchema = z.object({
  email: z.string().email(),
  password: z.string().min(1)
});

export const registerSchema = z.object({
  displayName: z.string().min(2).max(80),
  email: z.string().email(),
  password: z.string().min(12)
});

export const passwordResetSchema = z.object({
  email: z.string().email()
});

export const emailVerificationSchema = z.object({
  token: z.string().min(8)
});

export const orderEntrySchema = z.object({
  market: z.string().min(3),
  side: z.enum(["BUY", "SELL"]),
  type: z.literal("LIMIT"),
  price: z.string().min(1),
  quantity: z.string().min(1)
});

export const withdrawalSchema = z.object({
  asset: z.string().min(2),
  network: z.string().min(2),
  amount: z.string().min(1),
  destination: z.string().min(8)
});

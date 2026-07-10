"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FieldError } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import { errorMessage } from "@/lib/api/errors";
import type { LoginResponse, MfaChallengeResponse } from "@/lib/api/types";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import {
  backupCodeSchema,
  changePasswordSchema,
  emailVerificationSchema,
  loginSchema,
  passwordResetSchema,
  resendVerificationSchema,
  resetPasswordSchema,
  signupSchema,
  totpChallengeSchema,
  totpCodeSchema
} from "../schemas";
import { useAuthStore } from "../store";

type LoginValues = z.infer<typeof loginSchema>;
type SignupValues = z.infer<typeof signupSchema>;
type PasswordResetValues = z.infer<typeof passwordResetSchema>;
type ResetPasswordValues = z.infer<typeof resetPasswordSchema>;
type ChangePasswordValues = z.infer<typeof changePasswordSchema>;
type EmailVerificationValues = z.infer<typeof emailVerificationSchema>;
type ResendVerificationValues = z.infer<typeof resendVerificationSchema>;
type TotpCodeValues = z.infer<typeof totpCodeSchema>;
type TotpChallengeValues = z.infer<typeof totpChallengeSchema>;
type BackupCodeValues = z.infer<typeof backupCodeSchema>;

const inputClassName =
  "mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20";

export function LoginForm() {
  const router = useRouter();
  const startSession = useAuthStore((state) => state.startSession);
  const [mfaSessionToken, setMfaSessionToken] = useState<string | null>(null);
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const login = useMutation({
    mutationFn: heliumApi.login,
    onSuccess: (response) => {
      if ("mfaSessionToken" in response) {
        setMfaSessionToken((response as MfaChallengeResponse).mfaSessionToken);
        return;
      }
      startSession(response as LoginResponse);
      router.push("/dashboard");
    }
  });
  if (mfaSessionToken) return <TotpChallengeForm mfaSessionToken={mfaSessionToken} />;
  return (
    <AuthPanel title="Sign In" footer={
      <div className="space-y-1">
        <Link href="/password-reset" className="block text-cyan-200 hover:text-cyan-100">Forgot password?</Link>
        <p className="text-muted-foreground">New to HELIUM? <Link className="text-cyan-200 hover:text-cyan-100" href="/register">Create an account</Link></p>
      </div>
    }>
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => login.mutate(values))}>
        <label className="block text-sm">Email<input className={inputClassName} type="email" autoComplete="email" {...form.register("email")} /><FieldError message={form.formState.errors.email?.message} /></label>
        <label className="block text-sm">Password<input className={inputClassName} type="password" autoComplete="current-password" {...form.register("password")} /><FieldError message={form.formState.errors.password?.message} /></label>
        {login.isError ? <p className="text-sm text-red-300">{errorMessage(login.error)}</p> : null}
        <Button className="w-full" disabled={login.isPending} type="submit">{login.isPending ? "Signing in..." : "Sign in"}</Button>
      </form>
    </AuthPanel>
  );
}

export function TotpChallengeForm({ mfaSessionToken }: { mfaSessionToken: string }) {
  const router = useRouter();
  const startSession = useAuthStore((state) => state.startSession);
  const [useBackup, setUseBackup] = useState(false);
  const totpForm = useForm<TotpChallengeValues>({ resolver: zodResolver(totpChallengeSchema), defaultValues: { code: "" } });
  const backupForm = useForm<BackupCodeValues>({ resolver: zodResolver(backupCodeSchema), defaultValues: { backupCode: "" } });
  const totpChallenge = useMutation({
    mutationFn: (values: TotpChallengeValues) => heliumApi.totpChallenge(mfaSessionToken, values.code),
    onSuccess: (response) => { startSession(response as LoginResponse); router.push("/dashboard"); }
  });
  const backupChallenge = useMutation({
    mutationFn: (values: BackupCodeValues) => heliumApi.totpBackupCode(mfaSessionToken, values.backupCode),
    onSuccess: (response) => { startSession(response as LoginResponse); router.push("/dashboard"); }
  });
  return (
    <AuthPanel title="Two-Factor Authentication">
      {!useBackup ? (
        <form className="space-y-4" onSubmit={totpForm.handleSubmit((values) => totpChallenge.mutate(values))}>
          <p className="text-sm text-muted-foreground">Enter the 6-digit code from your authenticator app.</p>
          <label className="block text-sm">Authentication Code<input className={inputClassName} type="text" inputMode="numeric" maxLength={6} autoComplete="one-time-code" placeholder="000000" {...totpForm.register("code")} /><FieldError message={totpForm.formState.errors.code?.message} /></label>
          {totpChallenge.isError ? <p className="text-sm text-red-300">{errorMessage(totpChallenge.error)}</p> : null}
          <Button className="w-full" disabled={totpChallenge.isPending} type="submit">{totpChallenge.isPending ? "Verifying..." : "Verify"}</Button>
          <button type="button" className="w-full text-sm text-cyan-200 hover:text-cyan-100" onClick={() => setUseBackup(true)}>Use a backup code instead</button>
        </form>
      ) : (
        <form className="space-y-4" onSubmit={backupForm.handleSubmit((values) => backupChallenge.mutate(values))}>
          <p className="text-sm text-muted-foreground">Enter one of your backup codes (format: XXXX-XXXX).</p>
          <label className="block text-sm">Backup Code<input className={inputClassName} type="text" placeholder="XXXX-XXXX" {...backupForm.register("backupCode")} /><FieldError message={backupForm.formState.errors.backupCode?.message} /></label>
          {backupChallenge.isError ? <p className="text-sm text-red-300">{errorMessage(backupChallenge.error)}</p> : null}
          <Button className="w-full" disabled={backupChallenge.isPending} type="submit">{backupChallenge.isPending ? "Verifying..." : "Use Backup Code"}</Button>
          <button type="button" className="w-full text-sm text-cyan-200 hover:text-cyan-100" onClick={() => setUseBackup(false)}>Use authenticator app instead</button>
        </form>
      )}
    </AuthPanel>
  );
}

export function SignupForm() {
  const [submitted, setSubmitted] = useState(false);
  const form = useForm<SignupValues>({ resolver: zodResolver(signupSchema), defaultValues: { email: "", password: "", confirmPassword: "" } });
  const signup = useMutation({ mutationFn: heliumApi.signup, onSuccess: () => setSubmitted(true) });
  if (submitted) {
    return (
      <AuthPanel title="Check Your Email">
        <div className="space-y-4">
          <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4"><p className="text-sm text-emerald-300">Account created! We have sent a verification link to your email address.</p></div>
          <p className="text-sm text-muted-foreground">Click the link in the email to activate your account.</p>
          <p className="text-sm text-muted-foreground">Already verified? <Link className="text-cyan-200 hover:text-cyan-100" href="/login">Sign in</Link></p>
        </div>
      </AuthPanel>
    );
  }
  return (
    <AuthPanel title="Create Account" footer={<p className="text-muted-foreground">Already have an account? <Link className="text-cyan-200 hover:text-cyan-100" href="/login">Sign in</Link></p>}>
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => signup.mutate(values))}>
        <label className="block text-sm">Email<input className={inputClassName} type="email" autoComplete="email" {...form.register("email")} /><FieldError message={form.formState.errors.email?.message} /></label>
        <label className="block text-sm">Password<input className={inputClassName} type="password" autoComplete="new-password" {...form.register("password")} /><FieldError message={form.formState.errors.password?.message} /><span className="mt-1 block text-xs text-muted-foreground">Minimum 12 characters</span></label>
        <label className="block text-sm">Confirm Password<input className={inputClassName} type="password" autoComplete="new-password" {...form.register("confirmPassword")} /><FieldError message={form.formState.errors.confirmPassword?.message} /></label>
        {signup.isError ? <p className="text-sm text-red-300">{errorMessage(signup.error)}</p> : null}
        <Button className="w-full" disabled={signup.isPending} type="submit">{signup.isPending ? "Creating account..." : "Create account"}</Button>
      </form>
    </AuthPanel>
  );
}

export function RegisterForm() { return <SignupForm />; }

export function PasswordResetForm() {
  const [submitted, setSubmitted] = useState(false);
  const form = useForm<PasswordResetValues>({ resolver: zodResolver(passwordResetSchema), defaultValues: { email: "" } });
  const reset = useMutation({ mutationFn: (values: PasswordResetValues) => heliumApi.requestPasswordReset(values.email), onSuccess: () => setSubmitted(true) });
  if (submitted) {
    return (
      <AuthPanel title="Check Your Email">
        <div className="space-y-4">
          <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4"><p className="text-sm text-emerald-300">If an account exists for that email, we have sent a password reset link.</p></div>
          <p className="text-sm text-muted-foreground">The link expires in 30 minutes.</p>
          <Link className="block text-sm text-cyan-200 hover:text-cyan-100" href="/login">Back to sign in</Link>
        </div>
      </AuthPanel>
    );
  }
  return (
    <AuthPanel title="Forgot Password" footer={<Link href="/login" className="text-cyan-200 hover:text-cyan-100">Back to sign in</Link>}>
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => reset.mutate(values))}>
        <p className="text-sm text-muted-foreground">Enter your email and we will send you a reset link.</p>
        <label className="block text-sm">Email<input className={inputClassName} type="email" autoComplete="email" {...form.register("email")} /><FieldError message={form.formState.errors.email?.message} /></label>
        {reset.isError ? <p className="text-sm text-red-300">{errorMessage(reset.error)}</p> : null}
        <Button className="w-full" disabled={reset.isPending} type="submit">{reset.isPending ? "Sending..." : "Send reset link"}</Button>
      </form>
    </AuthPanel>
  );
}

export function ResetPasswordForm() {
  const params = useSearchParams();
  const router = useRouter();
  const token = params.get("token") ?? "";
  const [success, setSuccess] = useState(false);
  const form = useForm<ResetPasswordValues>({ resolver: zodResolver(resetPasswordSchema), defaultValues: { token, newPassword: "", confirmPassword: "" } });
  const reset = useMutation({
    mutationFn: (values: ResetPasswordValues) => heliumApi.confirmPasswordReset(values.token, values.newPassword),
    onSuccess: () => { setSuccess(true); setTimeout(() => router.push("/login"), 2000); }
  });
  if (success) return <AuthPanel title="Password Reset"><div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4"><p className="text-sm text-emerald-300">Password reset successfully. Redirecting to sign in...</p></div></AuthPanel>;
  return (
    <AuthPanel title="Reset Password">
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => reset.mutate(values))}>
        <input type="hidden" {...form.register("token")} />
        <label className="block text-sm">New Password<input className={inputClassName} type="password" autoComplete="new-password" {...form.register("newPassword")} /><FieldError message={form.formState.errors.newPassword?.message} /><span className="mt-1 block text-xs text-muted-foreground">Minimum 12 characters</span></label>
        <label className="block text-sm">Confirm New Password<input className={inputClassName} type="password" autoComplete="new-password" {...form.register("confirmPassword")} /><FieldError message={form.formState.errors.confirmPassword?.message} /></label>
        {reset.isError ? <p className="text-sm text-red-300">{errorMessage(reset.error)}</p> : null}
        <Button className="w-full" disabled={reset.isPending} type="submit">{reset.isPending ? "Resetting..." : "Reset password"}</Button>
      </form>
    </AuthPanel>
  );
}

export function EmailVerificationForm() {
  const params = useSearchParams();
  const router = useRouter();
  const form = useForm<EmailVerificationValues>({ resolver: zodResolver(emailVerificationSchema), defaultValues: { token: params.get("token") ?? "" } });
  const resendForm = useForm<ResendVerificationValues>({ resolver: zodResolver(resendVerificationSchema), defaultValues: { email: "" } });
  const verify = useMutation({ mutationFn: (values: EmailVerificationValues) => heliumApi.verifyEmail(values.token), onSuccess: () => { setTimeout(() => router.push("/login"), 1500); } });
  const resend = useMutation({ mutationFn: (values: ResendVerificationValues) => heliumApi.resendVerification(values.email) });
  return (
    <AuthPanel title="Verify Email">
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => verify.mutate(values))}>
        <p className="text-sm text-muted-foreground">Enter the verification token from your email.</p>
        <label className="block text-sm">Verification Token<input className={inputClassName} {...form.register("token")} /><FieldError message={form.formState.errors.token?.message} /></label>
        {verify.isSuccess ? <p className="text-sm text-emerald-300">Email verified! Redirecting to sign in...</p> : null}
        {verify.isError ? <p className="text-sm text-red-300">{errorMessage(verify.error)}</p> : null}
        <Button className="w-full" disabled={verify.isPending} type="submit">{verify.isPending ? "Verifying..." : "Verify email"}</Button>
      </form>
      <div className="mt-4 border-t border-border pt-4">
        <p className="text-sm text-muted-foreground">Did not receive the email?</p>
        <form className="mt-3 space-y-3" onSubmit={resendForm.handleSubmit((values) => resend.mutate(values))}>
          <label className="block text-sm">Email<input className={inputClassName} type="email" autoComplete="email" {...resendForm.register("email")} /><FieldError message={resendForm.formState.errors.email?.message} /></label>
          {resend.isSuccess ? <p className="text-sm text-emerald-300">If the account is unverified, a new email has been sent.</p> : null}
          {resend.isError ? <p className="text-sm text-red-300">{errorMessage(resend.error)}</p> : null}
          <Button variant="ghost" size="sm" className="text-cyan-200" disabled={resend.isPending} type="submit">{resend.isPending ? "Sending..." : "Resend verification email"}</Button>
        </form>
      </div>
    </AuthPanel>
  );
}

export function ChangePasswordForm({ onSuccess }: { onSuccess?: () => void }) {
  const form = useForm<ChangePasswordValues>({ resolver: zodResolver(changePasswordSchema), defaultValues: { currentPassword: "", newPassword: "", confirmPassword: "" } });
  const change = useMutation({ mutationFn: (values: ChangePasswordValues) => heliumApi.changePassword(values.currentPassword, values.newPassword), onSuccess: () => { form.reset(); onSuccess?.(); } });
  return (
    <form className="space-y-4" onSubmit={form.handleSubmit((values) => change.mutate(values))}>
      <label className="block text-sm">Current Password<input className={inputClassName} type="password" autoComplete="current-password" {...form.register("currentPassword")} /><FieldError message={form.formState.errors.currentPassword?.message} /></label>
      <label className="block text-sm">New Password<input className={inputClassName} type="password" autoComplete="new-password" {...form.register("newPassword")} /><FieldError message={form.formState.errors.newPassword?.message} /><span className="mt-1 block text-xs text-muted-foreground">Minimum 12 characters</span></label>
      <label className="block text-sm">Confirm New Password<input className={inputClassName} type="password" autoComplete="new-password" {...form.register("confirmPassword")} /><FieldError message={form.formState.errors.confirmPassword?.message} /></label>
      {change.isSuccess ? <p className="text-sm text-emerald-300">Password changed successfully.</p> : null}
      {change.isError ? <p className="text-sm text-red-300">{errorMessage(change.error)}</p> : null}
      <Button className="w-full" disabled={change.isPending} type="submit">{change.isPending ? "Changing..." : "Change password"}</Button>
    </form>
  );
}

export function TotpSetupForm({ onSuccess }: { onSuccess?: () => void }) {
  const [step, setStep] = useState<"idle" | "setup" | "done">("idle");
  const [setupData, setSetupData] = useState<{ secret: string; otpAuthUrl: string; qrCodeDataUrl: string } | null>(null);
  const [backupCodes, setBackupCodes] = useState<string[]>([]);
  const form = useForm<TotpCodeValues>({ resolver: zodResolver(totpCodeSchema), defaultValues: { code: "" } });
  const beginSetup = useMutation({ mutationFn: heliumApi.totpSetup, onSuccess: (data) => { setSetupData(data); setStep("setup"); } });
  const confirmSetup = useMutation({ mutationFn: (values: TotpCodeValues) => heliumApi.totpConfirm(values.code), onSuccess: (data) => { setBackupCodes(data.backupCodes); setStep("done"); onSuccess?.(); } });
  if (step === "idle") return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">Two-factor authentication adds an extra layer of security.</p>
      {beginSetup.isError ? <p className="text-sm text-red-300">{errorMessage(beginSetup.error)}</p> : null}
      <Button onClick={() => beginSetup.mutate()} disabled={beginSetup.isPending}>{beginSetup.isPending ? "Setting up..." : "Enable 2FA"}</Button>
    </div>
  );
  if (step === "setup" && setupData) return (
    <div className="space-y-4">
      <p className="text-sm text-muted-foreground">Scan this QR code with your authenticator app, or enter the secret key manually.</p>
      <div className="flex justify-center rounded-lg border border-border bg-white p-4">
        {/* eslint-disable-next-line @next/next/no-img-element */}
        <img src={setupData.qrCodeDataUrl} alt="Authenticator QR code" className="h-48 w-48" />
      </div>
      <div className="rounded-lg border border-border bg-black/20 p-4"><p className="mb-2 text-xs font-medium text-muted-foreground">Secret Key</p><code className="break-all text-xs text-cyan-200">{setupData.secret}</code></div>
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => confirmSetup.mutate(values))}>
        <label className="block text-sm">Enter the 6-digit code from your app to confirm<input className={inputClassName} type="text" inputMode="numeric" maxLength={6} placeholder="000000" autoComplete="one-time-code" {...form.register("code")} /><FieldError message={form.formState.errors.code?.message} /></label>
        {confirmSetup.isError ? <p className="text-sm text-red-300">{errorMessage(confirmSetup.error)}</p> : null}
        <Button className="w-full" disabled={confirmSetup.isPending} type="submit">{confirmSetup.isPending ? "Confirming..." : "Confirm & Enable 2FA"}</Button>
      </form>
    </div>
  );
  if (step === "done") return (
    <div className="space-y-4">
      <div className="rounded-lg border border-emerald-500/30 bg-emerald-500/10 p-4"><p className="text-sm font-medium text-emerald-300">Two-factor authentication enabled!</p></div>
      <div className="rounded-lg border border-amber-500/30 bg-amber-500/10 p-4">
        <p className="mb-3 text-sm font-medium text-amber-300">Save your backup codes - they will not be shown again</p>
        <div className="grid grid-cols-2 gap-2">{backupCodes.map((code) => <code key={code} className="rounded bg-black/30 px-2 py-1 text-xs text-slate-200">{code}</code>)}</div>
      </div>
    </div>
  );
  return null;
}

export function TotpDisableForm({ onSuccess }: { onSuccess?: () => void }) {
  const form = useForm<TotpCodeValues>({ resolver: zodResolver(totpCodeSchema), defaultValues: { code: "" } });
  const disable = useMutation({ mutationFn: (values: TotpCodeValues) => heliumApi.totpDisable(values.code), onSuccess: () => { form.reset(); onSuccess?.(); } });
  return (
    <form className="space-y-4" onSubmit={form.handleSubmit((values) => disable.mutate(values))}>
      <p className="text-sm text-muted-foreground">Enter your current 2FA code to disable two-factor authentication.</p>
      <label className="block text-sm">Authentication Code<input className={inputClassName} type="text" inputMode="numeric" maxLength={6} placeholder="000000" {...form.register("code")} /><FieldError message={form.formState.errors.code?.message} /></label>
      {disable.isSuccess ? <p className="text-sm text-emerald-300">2FA disabled.</p> : null}
      {disable.isError ? <p className="text-sm text-red-300">{errorMessage(disable.error)}</p> : null}
      <Button variant="danger" className="w-full" disabled={disable.isPending} type="submit">{disable.isPending ? "Disabling..." : "Disable 2FA"}</Button>
    </form>
  );
}

function AuthPanel({ title, children, footer }: Readonly<{ title: string; children: React.ReactNode; footer?: React.ReactNode }>) {
  return (
    <main className="flex min-h-screen items-center justify-center px-6 text-foreground">
      <Card className="w-full max-w-md">
        <CardContent className="p-6">
          <p className="text-micro font-semibold uppercase text-cyan-200/80">HELIUM Access</p>
          <h1 className="mt-2 text-display-md">{title}</h1>
          <div className="mt-6">{children}</div>
          {footer ? <div className="mt-4 text-sm">{footer}</div> : null}
        </CardContent>
      </Card>
    </main>
  );
}

"use client";

import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { FieldError } from "@/components/ui/state";
import { heliumApi } from "@/lib/api/client";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import type { z } from "zod";
import { emailVerificationSchema, loginSchema, passwordResetSchema, registerSchema } from "../schemas";
import { useAuthStore } from "../store";

type LoginValues = z.infer<typeof loginSchema>;
type RegisterValues = z.infer<typeof registerSchema>;
type PasswordResetValues = z.infer<typeof passwordResetSchema>;
type EmailVerificationValues = z.infer<typeof emailVerificationSchema>;

export function LoginForm() {
  const router = useRouter();
  const setUser = useAuthStore((state) => state.setUser);
  const form = useForm<LoginValues>({ resolver: zodResolver(loginSchema), defaultValues: { email: "", password: "" } });
  const login = useMutation({
    mutationFn: heliumApi.login,
    onSuccess: (user) => {
      setUser(user);
      router.push("/dashboard");
    }
  });

  return (
    <AuthPanel title="Login" footer={<Link href="/password-reset">Reset password</Link>}>
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => login.mutate(values))}>
        <label className="block text-sm">
          Email
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("email")} />
          <FieldError message={form.formState.errors.email?.message} />
        </label>
        <label className="block text-sm">
          Password
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" type="password" {...form.register("password")} />
          <FieldError message={form.formState.errors.password?.message} />
        </label>
        {login.isError ? <p className="text-sm text-red-300">Authentication failed.</p> : null}
        <Button className="w-full" disabled={login.isPending} type="submit">
          {login.isPending ? "Signing in" : "Sign in"}
        </Button>
      </form>
      <p className="mt-4 text-sm text-muted-foreground">
        New to HELIUM? <Link className="text-cyan-200" href="/register">Create an account</Link>
      </p>
    </AuthPanel>
  );
}

export function RegisterForm() {
  const router = useRouter();
  const setUser = useAuthStore((state) => state.setUser);
  const form = useForm<RegisterValues>({ resolver: zodResolver(registerSchema), defaultValues: { displayName: "", email: "", password: "" } });
  const register = useMutation({
    mutationFn: heliumApi.register,
    onSuccess: (user) => {
      setUser(user);
      router.push("/email-verification");
    }
  });

  return (
    <AuthPanel title="Register">
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => register.mutate(values))}>
        <label className="block text-sm">
          Display name
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("displayName")} />
          <FieldError message={form.formState.errors.displayName?.message} />
        </label>
        <label className="block text-sm">
          Email
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("email")} />
          <FieldError message={form.formState.errors.email?.message} />
        </label>
        <label className="block text-sm">
          Password
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" type="password" {...form.register("password")} />
          <FieldError message={form.formState.errors.password?.message} />
        </label>
        <Button className="w-full" disabled={register.isPending} type="submit">
          {register.isPending ? "Creating account" : "Create account"}
        </Button>
      </form>
    </AuthPanel>
  );
}

export function PasswordResetForm() {
  const form = useForm<PasswordResetValues>({ resolver: zodResolver(passwordResetSchema), defaultValues: { email: "" } });
  const reset = useMutation({ mutationFn: (values: PasswordResetValues) => heliumApi.requestPasswordReset(values.email) });

  return (
    <AuthPanel title="Password Reset">
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => reset.mutate(values))}>
        <label className="block text-sm">
          Email
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("email")} />
          <FieldError message={form.formState.errors.email?.message} />
        </label>
        {reset.isSuccess ? <p className="text-sm text-emerald-300">If the account exists, reset instructions are queued.</p> : null}
        <Button className="w-full" disabled={reset.isPending} type="submit">
          Send reset link
        </Button>
      </form>
    </AuthPanel>
  );
}

export function EmailVerificationForm() {
  const form = useForm<EmailVerificationValues>({ resolver: zodResolver(emailVerificationSchema), defaultValues: { token: "" } });
  const verify = useMutation({ mutationFn: (values: EmailVerificationValues) => heliumApi.verifyEmail(values.token) });

  return (
    <AuthPanel title="Email Verification">
      <form className="space-y-4" onSubmit={form.handleSubmit((values) => verify.mutate(values))}>
        <label className="block text-sm">
          Verification token
          <input className="mt-1 h-10 w-full rounded-md border border-border bg-black/20 px-3 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary/20" {...form.register("token")} />
          <FieldError message={form.formState.errors.token?.message} />
        </label>
        {verify.isSuccess ? <p className="text-sm text-emerald-300">Email verified.</p> : null}
        <Button className="w-full" disabled={verify.isPending} type="submit">
          Verify email
        </Button>
      </form>
    </AuthPanel>
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
          {footer ? <div className="mt-4 text-sm text-cyan-200">{footer}</div> : null}
        </CardContent>
      </Card>
    </main>
  );
}

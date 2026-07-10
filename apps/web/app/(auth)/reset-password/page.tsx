import { Suspense } from "react";
import { ResetPasswordForm } from "@/features/auth/components/auth-forms";

export default function ResetPasswordPage() {
  return (
    <Suspense fallback={<main className="grid min-h-screen place-items-center p-6 text-foreground">Loading</main>}>
      <ResetPasswordForm />
    </Suspense>
  );
}

import { Suspense } from "react";
import { EmailVerificationForm } from "@/features/auth/components/auth-forms";

export default function EmailVerificationPage() {
  return (
    <Suspense fallback={<main className="grid min-h-screen place-items-center p-6 text-foreground">Loading</main>}>
      <EmailVerificationForm />
    </Suspense>
  );
}

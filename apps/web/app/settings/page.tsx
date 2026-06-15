import { PageHeader } from "@/components/layout/app-shell";
import { ProtectedShell } from "@/components/layout/protected-shell";
import { SettingsPanel } from "@/features/settings/components/settings-panels";

export default function SettingsPage() {
  return (
    <ProtectedShell>
      <PageHeader title="Settings" detail="Account profile and security posture from the Auth/User module." />
      <SettingsPanel />
    </ProtectedShell>
  );
}

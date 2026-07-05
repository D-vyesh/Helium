"use client";

import type { LoginResponse, SessionUser } from "@/lib/api/types";
import { create } from "zustand";
import { persist } from "zustand/middleware";
import { clearAuthSession, onSessionCleared, setAuthTokens } from "./token-store";

type AuthState = {
  user: SessionUser | null;
  setUser: (user: SessionUser | null) => void;
  startSession: (login: LoginResponse) => void;
  endSession: () => void;
  hasRole: (roles: string[]) => boolean;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      setUser: (user) => set({ user }),
      startSession: (login) => {
        setAuthTokens({ accessToken: login.accessToken, refreshToken: login.refreshToken });
        set({ user: { ...login.user, roles: login.roles } });
      },
      endSession: () => {
        clearAuthSession();
        set({ user: null });
      },
      hasRole: (roles) => {
        const user = get().user;
        return Boolean(user?.roles.some((role) => roles.includes(role)));
      }
    }),
    {
      name: "helium-session-profile",
      partialize: (state) => ({ user: state.user })
    }
  )
);

// When the HTTP layer invalidates the session (failed refresh), drop the profile too.
if (typeof window !== "undefined") {
  onSessionCleared(() => {
    useAuthStore.setState({ user: null });
  });
}

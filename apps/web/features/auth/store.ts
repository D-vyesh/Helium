"use client";

import type { SessionUser, UserRole } from "@/lib/api/types";
import { create } from "zustand";
import { persist } from "zustand/middleware";

type AuthState = {
  user: SessionUser | null;
  setUser: (user: SessionUser | null) => void;
  hasRole: (roles: UserRole[]) => boolean;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set, get) => ({
      user: null,
      setUser: (user) => set({ user }),
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

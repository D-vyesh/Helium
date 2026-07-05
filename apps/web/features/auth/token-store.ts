/**
 * Token persistence decoupled from the zustand store so the low-level HTTP
 * client can read/write tokens without circular imports.
 */

const STORAGE_KEY = "helium-auth-tokens";

export type AuthTokens = {
  accessToken: string;
  refreshToken: string;
};

type SessionClearedListener = () => void;

const listeners = new Set<SessionClearedListener>();

export function onSessionCleared(listener: SessionClearedListener): () => void {
  listeners.add(listener);
  return () => listeners.delete(listener);
}

export function getAuthTokens(): AuthTokens | null {
  if (typeof window === "undefined") {
    return null;
  }
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<AuthTokens>;
    if (typeof parsed.accessToken !== "string" || typeof parsed.refreshToken !== "string") {
      return null;
    }
    return { accessToken: parsed.accessToken, refreshToken: parsed.refreshToken };
  } catch {
    return null;
  }
}

export function setAuthTokens(tokens: AuthTokens): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.setItem(STORAGE_KEY, JSON.stringify(tokens));
}

export function clearAuthSession(): void {
  if (typeof window === "undefined") {
    return;
  }
  window.localStorage.removeItem(STORAGE_KEY);
  listeners.forEach((listener) => listener());
}

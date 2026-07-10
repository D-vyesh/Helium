import { ApiConfigurationError, ApiError, NetworkError, type ProblemDetail } from "./errors";
import { getAuthTokens, setAuthTokens, clearAuthSession } from "@/features/auth/token-store";
import type { TokenResponse } from "./types";

export function apiBaseUrl(): string {
  const base = process.env.NEXT_PUBLIC_HELIUM_API_BASE_URL;
  if (!base) {
    throw new ApiConfigurationError();
  }
  return base.replace(/\/$/, "");
}

export function wsBaseUrl(): string {
  const explicit = process.env.NEXT_PUBLIC_HELIUM_WS_BASE_URL;
  if (explicit) {
    return explicit.replace(/\/$/, "");
  }
  return apiBaseUrl().replace(/^http/, "ws");
}

type RequestOptions = {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  headers?: Record<string, string>;
  /** Skip Authorization header (public endpoints). */
  anonymous?: boolean;
  /** Response is plain text (e.g. CSV export). */
  text?: boolean;
  /** Internal flag: do not attempt a token refresh on 401. */
  skipRefresh?: boolean;
};

async function parseProblem(response: Response): Promise<ProblemDetail | null> {
  try {
    const contentType = response.headers.get("content-type") ?? "";
    if (contentType.includes("json")) {
      return (await response.json()) as ProblemDetail;
    }
    const text = await response.text();
    return text ? { detail: text.slice(0, 500) } : null;
  } catch {
    return null;
  }
}

let refreshInFlight: Promise<boolean> | null = null;

async function tryRefreshSession(): Promise<boolean> {
  refreshInFlight ??= (async () => {
    const tokens = getAuthTokens();
    if (!tokens?.refreshToken) {
      return false;
    }
    try {
      const response = await fetch(`${apiBaseUrl()}/api/v1/auth/refresh`, {
        method: "POST",
        headers: { "content-type": "application/json" },
        credentials: "include",
        body: JSON.stringify({ refreshToken: tokens.refreshToken })
      });
      if (!response.ok) {
        clearAuthSession();
        return false;
      }
      const rotated = (await response.json()) as TokenResponse;
      setAuthTokens({ accessToken: rotated.accessToken, refreshToken: rotated.refreshToken });
      return true;
    } catch {
      return false;
    } finally {
      refreshInFlight = null;
    }
  })();
  return refreshInFlight;
}

export async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const url = `${apiBaseUrl()}${path}`;
  const headers: Record<string, string> = { ...(options.headers ?? {}) };
  if (options.body !== undefined) {
    headers["content-type"] = "application/json";
  }
  if (!options.anonymous) {
    const tokens = getAuthTokens();
    if (tokens?.accessToken) {
      headers.Authorization = `Bearer ${tokens.accessToken}`;
    }
  }

  let response: Response;
  try {
    response = await fetch(url, {
      method: options.method ?? "GET",
      headers,
      credentials: "include",
      body: options.body !== undefined ? JSON.stringify(options.body) : undefined
    });
  } catch (cause) {
    throw new NetworkError(cause);
  }

  if (response.status === 401 && !options.anonymous && !options.skipRefresh) {
    const refreshed = await tryRefreshSession();
    if (refreshed) {
      return request<T>(path, { ...options, skipRefresh: true });
    }
    clearAuthSession();
  }

  if (!response.ok) {
    throw new ApiError(response.status, await parseProblem(response));
  }

  if (response.status === 204) {
    return undefined as T;
  }

  if (options.text) {
    return (await response.text()) as T;
  }

  return (await response.json()) as T;
}

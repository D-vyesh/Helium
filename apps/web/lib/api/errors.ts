/**
 * Typed API errors. The frontend never fabricates data: every failed request
 * surfaces one of these errors to the UI layer.
 */

export type ProblemDetail = {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
};

const STATUS_MESSAGES: Record<number, string> = {
  400: "The request was rejected by the backend.",
  401: "Your session is not authenticated. Please sign in again.",
  403: "You do not have permission to perform this action.",
  404: "The requested resource was not found on the backend.",
  409: "The request conflicts with the current backend state.",
  422: "The backend could not process the submitted data.",
  429: "Too many requests. Please slow down and retry shortly.",
  500: "The backend encountered an internal error.",
  503: "The backend is temporarily unavailable."
};

export class ApiError extends Error {
  readonly status: number;
  readonly title: string;
  readonly detail: string;
  readonly problem: ProblemDetail | null;

  constructor(status: number, problem: ProblemDetail | null, fallbackDetail?: string) {
    const detail = problem?.detail ?? fallbackDetail ?? STATUS_MESSAGES[status] ?? `Request failed with status ${status}.`;
    super(detail);
    this.name = "ApiError";
    this.status = status;
    this.title = problem?.title ?? STATUS_MESSAGES[status] ?? "Request failed";
    this.detail = detail;
    this.problem = problem;
  }

  get isUnauthorized(): boolean {
    return this.status === 401;
  }

  get isForbidden(): boolean {
    return this.status === 403;
  }

  get isRetryable(): boolean {
    return this.status === 429 || this.status === 503 || this.status === 500;
  }
}

export class NetworkError extends Error {
  constructor(cause?: unknown) {
    super("The HELIUM backend is unreachable. Check your connection or backend availability.");
    this.name = "NetworkError";
    this.cause = cause;
  }
}

export class ApiConfigurationError extends Error {
  constructor() {
    super("NEXT_PUBLIC_HELIUM_API_BASE_URL is not configured. The frontend refuses to fabricate data without a backend.");
    this.name = "ApiConfigurationError";
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) return error.detail;
  if (error instanceof NetworkError || error instanceof ApiConfigurationError) return error.message;
  if (error instanceof Error) return error.message;
  return "An unexpected error occurred.";
}

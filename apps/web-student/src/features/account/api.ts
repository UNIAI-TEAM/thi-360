import type { ApiError, components } from "@thi360/api-client";
import { ApiStatusError, createBrowserApiClient, toApiStatusError } from "@/lib/api/client";

export type RegisterRequest = components["schemas"]["RegisterRequest"];
export type RegisterStartResponse = components["schemas"]["RegisterStartResponse"];
export type RegisterResendRequest = components["schemas"]["RegisterResendRequest"];
export type RegisterVerifyRequest = components["schemas"]["RegisterVerifyRequest"];
export type RegisterResponse = components["schemas"]["RegisterResponse"];
export type ProfileResponse = components["schemas"]["ProfileResponse"];
export type ProfileUpdateRequest = components["schemas"]["ProfileUpdateRequest"];
export type AccountDeletionCheckResponse = components["schemas"]["AccountDeletionCheckResponse"];
export type AccountDeletionRequest = components["schemas"]["AccountDeletionRequest"];
export type AccountDeletionBlocker = AccountDeletionCheckResponse["blockers"][number];

export type DeleteAccountResult =
  | { kind: "deleted" }
  | { kind: "notAllowed" }
  | { kind: "wrongPassword" }
  | { kind: "throttled" }
  | { kind: "blocked"; blockers: AccountDeletionBlocker[] };

const ACCOUNT_DELETION_BLOCKERS = new Set<AccountDeletionBlocker>([
  "ATTEMPT_IN_PROGRESS",
  "ORDER_BEING_CHECKED",
  "ORDER_PENDING_PAYMENT",
]);

function readDeletionBlockers(error: unknown): AccountDeletionBlocker[] {
  if (typeof error !== "object" || error === null || !("blockers" in error)) return [];
  const raw = (error as { blockers?: unknown }).blockers;
  if (!Array.isArray(raw)) return [];
  const blockers: AccountDeletionBlocker[] = [];
  for (const item of raw) {
    if (typeof item === "string" && ACCOUNT_DELETION_BLOCKERS.has(item as AccountDeletionBlocker)) {
      const blocker = item as AccountDeletionBlocker;
      if (!blockers.includes(blocker)) blockers.push(blocker);
    }
  }
  return blockers;
}

/**
 * Lỗi của các API mã OTP (`EmailCodeErrorResponse`): giống `ApiStatusError` thêm `retryAfter` (giây chờ gửi lại,
 * từ 429) và `remainingAttempts` (số lần nhập còn lại, từ 422 EMAIL_CODE_INVALID).
 */
export class EmailCodeStatusError extends ApiStatusError {
  readonly retryAfter?: number;
  readonly remainingAttempts?: number;

  constructor(base: ApiStatusError, options: { retryAfter?: number; remainingAttempts?: number } = {}) {
    super(base.status, base.message, {
      code: base.code,
      fieldErrors: base.fieldErrors,
      traceId: base.traceId,
    });
    this.name = "EmailCodeStatusError";
    this.retryAfter = options.retryAfter;
    this.remainingAttempts = options.remainingAttempts;
  }
}

function readNumberField(error: unknown, key: "retryAfter" | "remainingAttempts"): number | undefined {
  if (typeof error === "object" && error !== null && key in error) {
    const value = (error as Record<string, unknown>)[key];
    if (typeof value === "number" && Number.isFinite(value)) return value;
  }
  return undefined;
}

function toEmailCodeStatusError(status: number, error: unknown): EmailCodeStatusError {
  return new EmailCodeStatusError(toApiStatusError(status, error as never), {
    retryAfter: readNumberField(error, "retryAfter"),
    remainingAttempts: readNumberField(error, "remainingAttempts"),
  });
}

export async function startRegistration(body: RegisterRequest): Promise<RegisterStartResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/auth/register", { body });
  if (!response.ok || !data) {
    if (response.status === 429) {
      throw toEmailCodeStatusError(response.status, error);
    }
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function resendRegistrationCode(registrationId: string): Promise<RegisterStartResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/auth/register/resend", {
    body: { registrationId },
  });
  if (!response.ok || !data) {
    throw toEmailCodeStatusError(response.status, error);
  }
  return data;
}

export async function verifyRegistration(registrationId: string, code: string): Promise<RegisterResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/auth/register/verify", {
    body: { registrationId, code },
  });
  if (!response.ok || !data) {
    throw toEmailCodeStatusError(response.status, error);
  }
  return data;
}

export async function requestPasswordReset(email: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/password/forgot", {
    body: { email },
  });
  if (!response.ok) {
    throw toApiStatusError(response.status, error);
  }
}

export async function resetPasswordByToken(token: string, newPassword: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/auth/password/reset", {
    body: { token, newPassword },
  });
  if (!response.ok) {
    throw toApiStatusError(response.status, error);
  }
}

export const PROFILE_QUERY_KEY = ["profile", "me"] as const;

export async function loadProfile(): Promise<ProfileResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/profile");
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function updateProfile(body: ProfileUpdateRequest): Promise<ProfileResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PATCH("/api/v1/me/profile", { body });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

/**
 * Gửi hoặc gửi lại mã xác minh tới email hiện tại; lỗi giữ `retryAfter` của 429.
 *
 * Backend trả 202 không có thân. Proxy `/api` bỏ `Content-Length` nên `openapi-fetch` không nhận ra thân rỗng và gọi
 * `response.json()`, ném `SyntaxError` dù yêu cầu đã thành công; đọc dạng `text` để thân rỗng là hợp lệ.
 */
export async function sendVerificationCode(): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/me/email/verification", { parseAs: "text" });
  if (!response.ok) {
    throw toEmailCodeStatusError(response.status, error);
  }
}

/** Xác minh email hiện tại; lỗi giữ `remainingAttempts` của 422 EMAIL_CODE_INVALID. */
export async function verifyEmailCode(code: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/me/email/verify", {
    body: { code },
  });
  if (!response.ok) {
    throw toEmailCodeStatusError(response.status, error);
  }
}

export async function requestEmailChange(newEmail: string, currentPassword: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/me/email/change", {
    body: { newEmail, currentPassword },
    parseAs: "text",
  });
  if (!response.ok) {
    throw toEmailCodeStatusError(response.status, error);
  }
}

/** Xác nhận đổi email; lỗi giữ `remainingAttempts` của 422 và mã của 503 (đồng bộ Keycloak lỗi). */
export async function confirmEmailChange(code: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/me/email/change/confirm", {
    body: { code },
  });
  if (!response.ok) {
    throw toEmailCodeStatusError(response.status, error);
  }
}

export async function uploadAvatar(file: File): Promise<ProfileResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.PUT("/api/v1/me/avatar", {
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const formData = new FormData();
      formData.append("file", file);
      return formData;
    },
  });
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function removeAvatar(): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/me/avatar");
  if (!response.ok) {
    throw toApiStatusError(response.status, error);
  }
}

export const ACCOUNT_DELETION_CHECK_QUERY_KEY = ["account", "deletion-check"] as const;

export async function loadAccountDeletionCheck(): Promise<AccountDeletionCheckResponse> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/me/account/deletion-check");
  if (!response.ok || !data) {
    throw toApiStatusError(response.status, error);
  }
  return data;
}

export async function deleteAccount(body: AccountDeletionRequest): Promise<DeleteAccountResult> {
  const client = createBrowserApiClient();
  const { error, response } = await client.POST("/api/v1/me/account/deletion", { body });
  if (response.status === 204 || response.ok) {
    return { kind: "deleted" };
  }
  // Absorb 401 and 403 as values so QueryProvider does not redirect to /login (losing ?deleted=1) or /forbidden.
  if (response.status === 401) {
    return { kind: "deleted" };
  }
  if (response.status === 403) {
    return { kind: "notAllowed" };
  }
  const apiError = typeof error === "object" && error !== null ? (error as ApiError) : undefined;
  if (response.status === 400 && apiError?.code === "WRONG_OLD_PASSWORD") {
    return { kind: "wrongPassword" };
  }
  if (response.status === 429 && apiError?.code === "LOGIN_THROTTLED") {
    return { kind: "throttled" };
  }
  if (response.status === 409 && apiError?.code === "ACCOUNT_DELETION_BLOCKED") {
    const blockers = readDeletionBlockers(error);
    if (blockers.length > 0) {
      return { kind: "blocked", blockers };
    }
  }
  throw toApiStatusError(response.status, apiError);
}

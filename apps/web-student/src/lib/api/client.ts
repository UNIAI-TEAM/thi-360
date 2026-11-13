import { type ApiError, createApiClient, type FieldError } from "@thi360/api-client";
import { xsrfMiddleware } from "@thi360/web-kit/client";

export class ApiStatusError extends Error {
  readonly status: number;
  readonly code: string;
  readonly fieldErrors: FieldError[];
  readonly traceId?: string;

  constructor(
    status: number,
    message = "",
    options: { code?: string; fieldErrors?: FieldError[]; traceId?: string } = {},
  ) {
    super(message);
    this.name = "ApiStatusError";
    this.status = status;
    this.code = options.code ?? "INTERNAL_ERROR";
    this.fieldErrors = options.fieldErrors ?? [];
    this.traceId = options.traceId;
  }
}

export function toApiStatusError(status: number, error: ApiError | undefined): ApiStatusError {
  return new ApiStatusError(status, error?.message ?? "", {
    code: error?.code,
    fieldErrors: error?.fieldErrors,
    traceId: error?.traceId,
  });
}

export function createBrowserApiClient() {
  const client = createApiClient();
  client.use(xsrfMiddleware);
  return client;
}

import type { ApiError, components, FieldError, operations } from "@thi360/api-client";
import { createBrowserApiClient } from "@/lib/api/client";
import { ApiStatusError } from "@/lib/api/query-client";

export type ImportBatch = components["schemas"]["ImportBatchResponse"];
export type ImportBatchStatus = ImportBatch["status"];
export type ImportRow = components["schemas"]["ImportRowResponse"];
export type ImportRowStatus = ImportRow["status"];
export type ImportRowError = components["schemas"]["ImportRowError"];
export type PageResponseImportRow = components["schemas"]["PageResponseImportRowResponse"];

export type ListBatchRowsQuery = NonNullable<operations["listBatchRows"]["parameters"]["query"]>;
export type UploadBatchQuery = NonNullable<operations["uploadBatch"]["parameters"]["query"]>;

export interface DownloadedFile {
  blob: Blob;
  filename: string;
}

export const BULK_IMPORT_QUERY_KEYS = {
  all: ["bulk-import"] as const,
  detail: (id: string) => ["bulk-import", "detail", id] as const,
  rows: (id: string, query: ListBatchRowsQuery) => ["bulk-import", "rows", id, query] as const,
};

export class BulkImportApiError extends ApiStatusError {
  readonly code?: string;
  readonly fieldErrors: readonly FieldError[];
  readonly httpStatus: number;

  constructor(
    status: number,
    message = "",
    code?: string,
    fieldErrors: readonly FieldError[] = [],
  ) {
    super(status, message);
    this.name = "BulkImportApiError";
    this.httpStatus = status;
    this.code = code;
    this.fieldErrors = fieldErrors;
  }
}

export function toBulkImportApiError(status: number, error: unknown): BulkImportApiError {
  if (typeof error === "object" && error !== null) {
    const body = error as Partial<ApiError>;
    const code = typeof body.code === "string" ? body.code : undefined;
    const message = typeof body.message === "string" ? body.message : "";
    const fieldErrors = Array.isArray(body.fieldErrors) ? body.fieldErrors : [];
    return new BulkImportApiError(status, message, code, fieldErrors);
  }
  return new BulkImportApiError(status);
}

export function extractFilenameFromDisposition(
  disposition: string | null,
  fallback: string,
): string {
  if (!disposition) return fallback;
  const utf8Match = /filename\*=UTF-8''([^;\n]+)/i.exec(disposition);
  if (utf8Match?.[1]) {
    try {
      return decodeURIComponent(utf8Match[1].trim());
    } catch {
      return utf8Match[1].trim();
    }
  }
  const plainMatch = /filename="?([^";\n]+)"?/i.exec(disposition);
  if (plainMatch?.[1]) {
    return plainMatch[1].trim();
  }
  return fallback;
}

export async function downloadImportTemplate(): Promise<DownloadedFile> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/import-batches/template", {
    parseAs: "blob",
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return {
    blob: data,
    filename: extractFilenameFromDisposition(
      response.headers.get("content-disposition"),
      "mau-cap-tai-khoan-hang-loat.xlsx",
    ),
  };
}

export async function uploadImportBatch(
  file: File,
  mustChangePassword: boolean,
): Promise<ImportBatch> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/import-batches", {
    params: { query: { mustChangePassword } },
    body: { file: file as unknown as string },
    bodySerializer: () => {
      const formData = new FormData();
      formData.append("file", file);
      return formData;
    },
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return data;
}

export async function getImportBatch(id: string): Promise<ImportBatch> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/import-batches/{id}", {
    params: { path: { id } },
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return data;
}

export async function listImportBatchRows(
  id: string,
  query: ListBatchRowsQuery,
): Promise<PageResponseImportRow> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/import-batches/{id}/rows", {
    params: { path: { id }, query },
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return data;
}

export async function downloadImportErrors(id: string): Promise<DownloadedFile> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/import-batches/{id}/errors", {
    params: { path: { id } },
    parseAs: "blob",
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return {
    blob: data,
    filename: extractFilenameFromDisposition(
      response.headers.get("content-disposition"),
      `import-errors-${id}.xlsx`,
    ),
  };
}

export async function confirmImportBatch(
  id: string,
  idempotencyKey: string,
): Promise<ImportBatch> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.POST("/api/v1/admin/import-batches/{id}/confirm", {
    params: {
      path: { id },
      header: { "Idempotency-Key": idempotencyKey },
    },
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return data;
}

export async function downloadImportResult(id: string): Promise<DownloadedFile> {
  const client = createBrowserApiClient();
  const { data, error, response } = await client.GET("/api/v1/admin/import-batches/{id}/result", {
    params: { path: { id } },
    parseAs: "blob",
  });
  if (!response.ok || !data) throw toBulkImportApiError(response.status, error);
  return {
    blob: data,
    filename: extractFilenameFromDisposition(
      response.headers.get("content-disposition"),
      `import-result-${id}.xlsx`,
    ),
  };
}

export async function deleteImportResult(id: string): Promise<void> {
  const client = createBrowserApiClient();
  const { error, response } = await client.DELETE("/api/v1/admin/import-batches/{id}/result", {
    params: { path: { id } },
  });
  if (!response.ok) throw toBulkImportApiError(response.status, error);
}

export function saveBlobToDisk(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const link = document.createElement("a");
  link.href = url;
  link.download = filename;
  link.className = "sr-only";
  document.body.appendChild(link);
  link.click();
  link.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1_000);
}

export interface BulkImportApiDeps {
  downloadImportTemplate: typeof downloadImportTemplate;
  uploadImportBatch: typeof uploadImportBatch;
  getImportBatch: typeof getImportBatch;
  listImportBatchRows: typeof listImportBatchRows;
  downloadImportErrors: typeof downloadImportErrors;
  confirmImportBatch: typeof confirmImportBatch;
  downloadImportResult: typeof downloadImportResult;
  deleteImportResult: typeof deleteImportResult;
  saveBlobToDisk: typeof saveBlobToDisk;
}

export const defaultBulkImportApiDeps: BulkImportApiDeps = {
  downloadImportTemplate,
  uploadImportBatch,
  getImportBatch,
  listImportBatchRows,
  downloadImportErrors,
  confirmImportBatch,
  downloadImportResult,
  deleteImportResult,
  saveBlobToDisk,
};

import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { BulkImportApiError } from "../api";
import {
  IMPORT_COLUMN_KEYS,
  KNOWN_IMPORT_API_ERROR_CODES,
  KNOWN_IMPORT_FAILURE_CODES,
  KNOWN_IMPORT_ROW_ERROR_CODES,
  mapImportApiErrorCode,
  mapImportColumnKey,
  mapImportFailureCode,
  mapImportRowErrorCode,
  resolveImportApiErrorMessage,
} from "../utils";

const VIETNAMESE_DIACRITICS =
  /[àáảãạăằắẳẵặâầấẩẫậèéẻẽẹêềếểễệìíỉĩịòóỏõọôồốổỗộơờớởỡợùúủũụưừứửữựỳýỷỹỵđÀÁẢÃẠĂẰẮẲẴẶÂẦẤẨẪẬÈÉẺẼẸÊỀẾỂỄỆÌÍỈĨỊÒÓỎÕỌÔỒỐỔỖỘƠỜỚỞỠỢÙÚỦŨỤƯỪỨỬỮỰỲÝỶỸỴĐ]/;

describe("T11 bulk-import error & row code mappings", () => {
  it("mọi mã lỗi API, lỗi dòng, cột và lỗi worker đều có bản dịch ở cả vi.json và en.json", () => {
    const viApiCodes = vi.bulkImport.errorCodes as Record<string, string>;
    const enApiCodes = en.bulkImport.errorCodes as Record<string, string>;
    for (const code of KNOWN_IMPORT_API_ERROR_CODES) {
      expect(mapImportApiErrorCode(code)).toBe(code);
      expect(viApiCodes[code]?.trim().length).toBeGreaterThan(0);
      expect(enApiCodes[code]?.trim().length).toBeGreaterThan(0);
      expect(enApiCodes[code]).not.toMatch(VIETNAMESE_DIACRITICS);
    }

    const viRowCodes = vi.bulkImport.rowErrorCodes as Record<string, string>;
    const enRowCodes = en.bulkImport.rowErrorCodes as Record<string, string>;
    for (const code of KNOWN_IMPORT_ROW_ERROR_CODES) {
      expect(mapImportRowErrorCode(code)).toBe(code);
      expect(viRowCodes[code]?.trim().length).toBeGreaterThan(0);
      expect(enRowCodes[code]?.trim().length).toBeGreaterThan(0);
      expect(enRowCodes[code]).not.toMatch(VIETNAMESE_DIACRITICS);
    }

    const viFailureCodes = vi.bulkImport.failureCodes as Record<string, string>;
    const enFailureCodes = en.bulkImport.failureCodes as Record<string, string>;
    for (const code of KNOWN_IMPORT_FAILURE_CODES) {
      expect(mapImportFailureCode(code)).toBe(code);
      expect(viFailureCodes[code]?.trim().length).toBeGreaterThan(0);
      expect(enFailureCodes[code]?.trim().length).toBeGreaterThan(0);
      expect(enFailureCodes[code]).not.toMatch(VIETNAMESE_DIACRITICS);
    }

    const viColumns = vi.bulkImport.columns as Record<string, string>;
    const enColumns = en.bulkImport.columns as Record<string, string>;
    for (const col of IMPORT_COLUMN_KEYS) {
      expect(viColumns[col]?.trim().length).toBeGreaterThan(0);
      expect(enColumns[col]?.trim().length).toBeGreaterThan(0);
      expect(enColumns[col]).not.toMatch(VIETNAMESE_DIACRITICS);
    }
  });

  it("ánh xạ tên cột từ backend tiếng Việt hoặc tiếng Anh sang khóa cột chuẩn", () => {
    expect(mapImportColumnKey("Tên đăng nhập*")).toBe("USERNAME");
    expect(mapImportColumnKey("Mật khẩu")).toBe("PASSWORD");
    expect(mapImportColumnKey("Họ và tên*")).toBe("FULL_NAME");
    expect(mapImportColumnKey("Email")).toBe("EMAIL");
    expect(mapImportColumnKey("Số điện thoại")).toBe("PHONE");
    expect(mapImportColumnKey("Mã lớp")).toBe("CLASS_CODE");
    expect(mapImportColumnKey("Username")).toBe("USERNAME");
    expect(mapImportColumnKey("Class Code")).toBe("CLASS_CODE");
    expect(mapImportColumnKey("Unknown")).toBeNull();
  });

  it("resolveImportApiErrorMessage dịch theo mã lỗi và HTTP status, không dùng message thô từ backend", () => {
    const translate = (code: (typeof KNOWN_IMPORT_API_ERROR_CODES)[number]) =>
      vi.bulkImport.errorCodes[code];

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(409, "RAW BACKEND MESSAGE", "IMPORT_HAS_ERRORS"),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.IMPORT_HAS_ERRORS);

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(409, "RAW BACKEND MESSAGE", "IMPORT_SOURCE_GONE"),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.IMPORT_SOURCE_GONE);

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(413, "RAW BACKEND MESSAGE", "IMPORT_FILE_TOO_LARGE"),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.IMPORT_FILE_TOO_LARGE);

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(403, "RAW BACKEND MESSAGE", "IMPORT_CLASS_PERMISSION_REQUIRED"),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.IMPORT_CLASS_PERMISSION_REQUIRED);

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(409, "RAW BACKEND MESSAGE", "IDEMPOTENCY_KEY_REUSED"),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.IDEMPOTENCY_KEY_REUSED);

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(429, "RAW BACKEND MESSAGE", "RATE_LIMITED"),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.RATE_LIMITED);

    expect(
      resolveImportApiErrorMessage(
        new BulkImportApiError(400, "RAW BACKEND MESSAGE", "VALIDATION_FAILED", [
          { field: "file", code: "IMPORT_FORMULA_NOT_ALLOWED", message: "RAW" },
        ]),
        translate,
        "fallback",
      ),
    ).toBe(vi.bulkImport.errorCodes.IMPORT_FORMULA_NOT_ALLOWED);

    expect(
      resolveImportApiErrorMessage(new BulkImportApiError(413, "too large"), translate, "fallback"),
    ).toBe(vi.bulkImport.errorCodes.IMPORT_FILE_TOO_LARGE);

    expect(resolveImportApiErrorMessage(new Error("boom"), translate, "fallback")).toBe("fallback");
  });
});

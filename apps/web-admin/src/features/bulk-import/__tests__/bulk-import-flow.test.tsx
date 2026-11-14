import { setTimeout as sleep } from "node:timers/promises";
import { fireEvent, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  type BulkImportApiDeps,
  BulkImportApiError,
  defaultBulkImportApiDeps,
  type ImportBatch,
  type ImportRow,
} from "../api";
import { BulkImportBatchView } from "../components/bulk-import-batch-view";
import { BulkImportUploadView } from "../components/bulk-import-upload-view";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

const BATCH_ID = "00000000-0000-4000-8000-00000000b001";

function makeBatch(overrides: Partial<ImportBatch> = {}): ImportBatch {
  return {
    id: BATCH_ID,
    status: "VALIDATED",
    originalFilename: "students.xlsx",
    mustChangePassword: false,
    totalRows: 3,
    validRows: 3,
    invalidRows: 0,
    createdRows: 0,
    failedRows: 0,
    hasErrors: false,
    canConfirm: true,
    hasGeneratedPasswords: true,
    sourceAvailable: true,
    resultAvailable: false,
    createdAt: "2026-11-14T08:00:00Z",
    updatedAt: "2026-11-14T08:00:00Z",
    version: 0,
    ...overrides,
  };
}

function makeDeps(overrides: Partial<BulkImportApiDeps> = {}): BulkImportApiDeps {
  return {
    ...defaultBulkImportApiDeps,
    downloadImportTemplate: vi.fn<BulkImportApiDeps["downloadImportTemplate"]>(async () => ({
      blob: new Blob(["xlsx"]),
      filename: "mau-cap-tai-khoan-hang-loat.xlsx",
    })),
    uploadImportBatch: vi.fn<BulkImportApiDeps["uploadImportBatch"]>(async () => makeBatch()),
    getImportBatch: vi.fn<BulkImportApiDeps["getImportBatch"]>(async () => makeBatch()),
    listImportBatchRows: vi.fn<BulkImportApiDeps["listImportBatchRows"]>(async () => ({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    })),
    downloadImportErrors: vi.fn<BulkImportApiDeps["downloadImportErrors"]>(async () => ({
      blob: new Blob(["err"]),
      filename: `import-errors-${BATCH_ID}.xlsx`,
    })),
    confirmImportBatch: vi.fn<BulkImportApiDeps["confirmImportBatch"]>(async () =>
      makeBatch({ status: "CONFIRMED", canConfirm: false, confirmedAt: "2026-11-14T08:01:00Z" }),
    ),
    downloadImportResult: vi.fn<BulkImportApiDeps["downloadImportResult"]>(async () => ({
      blob: new Blob(["res"]),
      filename: `import-result-${BATCH_ID}.xlsx`,
    })),
    deleteImportResult: vi.fn<BulkImportApiDeps["deleteImportResult"]>(async () => undefined),
    saveBlobToDisk: vi.fn<BulkImportApiDeps["saveBlobToDisk"]>(),
    ...overrides,
  };
}

function renderUpload(
  deps: Partial<BulkImportApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
  locale: "vi" | "en" = "vi",
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <BulkImportUploadView deps={deps} />
    </SessionProvider>,
    {
      locale,
      messages: locale === "en" ? en : viMessages,
    },
  );
}

function renderBatch(
  deps: Partial<BulkImportApiDeps>,
  session: Session = TEST_ADMIN_SESSION,
  locale: "vi" | "en" = "vi",
) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <BulkImportBatchView batchId={BATCH_ID} deps={deps} />
    </SessionProvider>,
    {
      locale,
      messages: locale === "en" ? en : viMessages,
    },
  );
}

afterEach(() => {
  routerMock.push.mockReset();
  vi.restoreAllMocks();
});

describe("BulkImportUploadView (Step 1)", () => {
  it("chặn bước 1 khi thiếu quyền USER.MODIFY", async () => {
    const deps = makeDeps();
    renderUpload(deps, { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });
    expect(await screen.findByText("Bạn không có quyền vào trang này")).toBeInTheDocument();
  });

  it("chặn trang đợt khi thiếu quyền USER.MODIFY và không gọi API", async () => {
    const deps = makeDeps();
    renderBatch(deps, { ...TEST_ADMIN_SESSION, permissions: ["USER.VIEW"] });
    expect(await screen.findByText("Bạn không có quyền vào trang này")).toBeInTheDocument();
    expect(deps.getImportBatch).not.toHaveBeenCalled();
  });

  it("tải file mẫu, kiểm tra lỗi tệp theo mã, chọn checkbox bắt đổi mật khẩu và upload", async () => {
    const uploadMock = vi
      .fn<BulkImportApiDeps["uploadImportBatch"]>()
      .mockRejectedValueOnce(
        new BulkImportApiError(
          403,
          "Backend raw Vietnamese message should never appear",
          "IMPORT_CLASS_PERMISSION_REQUIRED",
        ),
      )
      .mockResolvedValueOnce(makeBatch());

    const deps = makeDeps({ uploadImportBatch: uploadMock });
    renderUpload(deps);

    await userEvent.click(await screen.findByRole("button", { name: "Tải file mẫu" }));
    await waitFor(() => {
      expect(deps.downloadImportTemplate).toHaveBeenCalledTimes(1);
      expect(deps.saveBlobToDisk).toHaveBeenCalledWith(
        expect.any(Blob),
        "mau-cap-tai-khoan-hang-loat.xlsx",
      );
    });

    const fileInput = screen.getByLabelText("Chọn tệp") as HTMLInputElement;

    // Tệp > 5 MB -> IMPORT_FILE_TOO_LARGE
    const hugeFile = new File(["x"], "huge.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    Object.defineProperty(hugeFile, "size", { value: 6 * 1024 * 1024 });
    fireEvent.change(fileInput, { target: { files: [hugeFile] } });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      viMessages.bulkImport.errorCodes.IMPORT_FILE_TOO_LARGE,
    );

    // Tệp hợp lệ + bật "Bắt đổi mật khẩu ở lần đăng nhập đầu"
    const validFile = new File(["hello"], "students.xlsx", {
      type: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    });
    fireEvent.change(fileInput, { target: { files: [validFile] } });
    expect(screen.getByText("students.xlsx")).toBeInTheDocument();

    await userEvent.click(screen.getByLabelText("Bắt đổi mật khẩu ở lần đăng nhập đầu"));
    await userEvent.click(screen.getByRole("button", { name: "Tải lên và kiểm tra" }));

    await waitFor(() => {
      expect(uploadMock).toHaveBeenCalledWith(validFile, true);
    });
    expect(await screen.findByRole("alert")).toHaveTextContent(
      viMessages.bulkImport.errorCodes.IMPORT_CLASS_PERMISSION_REQUIRED,
    );
    expect(screen.queryByText(/Backend raw Vietnamese/)).not.toBeInTheDocument();

    // Upload lại thành công -> chuyển hướng tới /tai-khoan/cap-hang-loat/[id]
    await userEvent.click(screen.getByRole("button", { name: "Tải lên và kiểm tra" }));
    await waitFor(() => {
      expect(routerMock.push).toHaveBeenCalledWith(`/tai-khoan/cap-hang-loat/${BATCH_ID}`);
    });
  });
});

describe("BulkImportBatchView (Steps 2, 3, 4)", () => {
  it("bước 2: hiển thị lỗi theo rowNo/column/code (dịch theo locale en/vi), lọc dòng lỗi, phân trang và tải file lỗi", async () => {
    const invalidRow: ImportRow = {
      id: "row-2",
      rowNo: 3,
      username: "bad user",
      fullName: "",
      classCode: "LOP-X",
      passwordGenerated: false,
      status: "INVALID",
      errors: [
        {
          rowNo: 3,
          column: "Tên đăng nhập",
          code: "USERNAME_INVALID",
          message: "Backend raw error message",
        },
        {
          rowNo: 3,
          column: "Họ và tên",
          code: "REQUIRED",
          message: "Backend raw required",
        },
        {
          rowNo: 3,
          column: "Mã lớp",
          code: "CLASS_INACTIVE",
          message: "Backend raw class inactive",
        },
      ],
    };

    const listRowsMock = vi.fn<BulkImportApiDeps["listImportBatchRows"]>(async () => ({
      items: [invalidRow],
      page: 0,
      size: 20,
      totalItems: 25,
      totalPages: 2,
    }));

    const downloadErrorsMock = vi
      .fn<BulkImportApiDeps["downloadImportErrors"]>()
      .mockResolvedValueOnce({
        blob: new Blob(["err"]),
        filename: `import-errors-${BATCH_ID}.xlsx`,
      })
      .mockRejectedValueOnce(new BulkImportApiError(409, "raw", "IMPORT_SOURCE_GONE"));

    const deps = makeDeps({
      getImportBatch: vi.fn(async () =>
        makeBatch({ totalRows: 25, validRows: 24, invalidRows: 1, hasErrors: true, canConfirm: false }),
      ),
      listImportBatchRows: listRowsMock,
      downloadImportErrors: downloadErrorsMock,
    });

    // Render với locale = "en" để xác minh không lọt chữ tiếng Việt từ backend
    renderBatch(deps, TEST_ADMIN_SESSION, "en");

    expect(await screen.findByText("bad user")).toBeInTheDocument();
    expect(screen.getByText(/Username: Invalid username/)).toBeInTheDocument();
    expect(screen.getByText(/Full name: Full name is required/)).toBeInTheDocument();
    expect(screen.getByText(/Class code: Class is inactive/)).toBeInTheDocument();
    expect(screen.queryByText(/Backend raw/)).not.toBeInTheDocument();
    expect(screen.queryByText(/Tên đăng nhập/)).not.toBeInTheDocument();

    const confirmBtn = screen.getByRole("button", { name: "Confirm creating 24 accounts" });
    expect(confirmBtn).toBeDisabled();

    // Lọc chỉ hiện dòng lỗi
    await userEvent.click(screen.getByLabelText("Show error rows only"));
    await waitFor(() => {
      expect(listRowsMock).toHaveBeenLastCalledWith(
        BATCH_ID,
        expect.objectContaining({ status: "INVALID", page: 0, size: 20 }),
      );
    });

    // Phân trang sang trang 2
    await userEvent.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => {
      expect(listRowsMock).toHaveBeenLastCalledWith(
        BATCH_ID,
        expect.objectContaining({ page: 1, size: 20 }),
      );
    });

    // Tải file lỗi, rồi kiểm tra IMPORT_SOURCE_GONE
    const dlErrBtn = screen.getByRole("button", { name: "Download error file (1 rows)" });
    await userEvent.click(dlErrBtn);
    await waitFor(() => {
      expect(deps.saveBlobToDisk).toHaveBeenCalledWith(
        expect.any(Blob),
        `import-errors-${BATCH_ID}.xlsx`,
      );
    });

    await userEvent.click(dlErrBtn);
    expect(await screen.findByRole("alert")).toHaveTextContent(
      en.bulkImport.errorCodes.IMPORT_SOURCE_GONE,
    );
  });

  it("bước 3: giữ nguyên Idempotency-Key khi thử lại cùng lần xác nhận và quay về bước 2 nếu gặp IMPORT_HAS_ERRORS", async () => {
    const confirmMock = vi
      .fn<BulkImportApiDeps["confirmImportBatch"]>()
      .mockRejectedValueOnce(new BulkImportApiError(500, "err", "INTERNAL_ERROR"))
      .mockResolvedValueOnce(
        makeBatch({ status: "CONFIRMED", canConfirm: false, confirmedAt: "2026-11-14T08:02:00Z" }),
      );

    const deps = makeDeps({
      getImportBatch: vi.fn(async () => makeBatch({ totalRows: 2, validRows: 2, invalidRows: 0 })),
      confirmImportBatch: confirmMock,
    });

    renderBatch(deps);

    const continueBtn = await screen.findByRole("button", { name: "Xác nhận cấp 2 tài khoản" });
    await userEvent.click(continueBtn);

    expect(await screen.findByText("Kiểm tra lần cuối")).toBeInTheDocument();

    const finalConfirmBtn = screen.getByRole("button", { name: "Xác nhận cấp 2 tài khoản" });
    await userEvent.click(finalConfirmBtn);

    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(1));
    const firstKey = confirmMock.mock.calls[0]![1];
    expect(firstKey).toBeTruthy();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      viMessages.bulkImport.errorCodes.INTERNAL_ERROR,
    );

    // Thử lại lần nữa: phải dùng đúng Idempotency-Key cũ
    await userEvent.click(screen.getByRole("button", { name: "Xác nhận cấp 2 tài khoản" }));
    await waitFor(() => expect(confirmMock).toHaveBeenCalledTimes(2));
    expect(confirmMock.mock.calls[1]![1]).toBe(firstKey);

    expect(await screen.findByText("Đang tạo tài khoản")).toBeInTheDocument();
  });

  it("bước 4: polling mỗi 2 giây khi đang xử lý và dừng khi đợt tới trạng thái cuối", async () => {
    const getBatchMock = vi
      .fn<BulkImportApiDeps["getImportBatch"]>()
      .mockResolvedValueOnce(
        makeBatch({ status: "PROCESSING", totalRows: 4, validRows: 4, createdRows: 1, failedRows: 0 }),
      )
      .mockResolvedValueOnce(
        makeBatch({ status: "PROCESSING", totalRows: 4, validRows: 4, createdRows: 3, failedRows: 0 }),
      )
      .mockResolvedValue(
        makeBatch({
          status: "COMPLETED",
          totalRows: 4,
          validRows: 4,
          createdRows: 4,
          failedRows: 0,
          resultAvailable: true,
        }),
      );

    renderBatch(makeDeps({ getImportBatch: getBatchMock }));

    expect(await screen.findByText("1 / 4 dòng")).toBeInTheDocument();
    expect(getBatchMock).toHaveBeenCalledTimes(1);

    expect(await screen.findByText("3 / 4 dòng", undefined, { timeout: 4_000 })).toBeInTheDocument();
    expect(getBatchMock).toHaveBeenCalledTimes(2);

    expect(
      await screen.findByText("Kết quả từng dòng", undefined, { timeout: 4_000 }),
    ).toBeInTheDocument();
    expect(getBatchMock).toHaveBeenCalledTimes(3);

    await sleep(2_600);
    expect(getBatchMock).toHaveBeenCalledTimes(3);
  }, 20_000);

  it("bước 4: dừng polling khi rời trang (unmount)", async () => {
    const processingForever = vi.fn<BulkImportApiDeps["getImportBatch"]>(async () =>
      makeBatch({ status: "PROCESSING", totalRows: 4, validRows: 4, createdRows: 1, failedRows: 0 }),
    );
    const { unmount } = renderBatch(makeDeps({ getImportBatch: processingForever }));
    expect(await screen.findByText("1 / 4 dòng")).toBeInTheDocument();
    expect(processingForever).toHaveBeenCalledTimes(1);

    unmount();
    await sleep(2_600);
    expect(processingForever).toHaveBeenCalledTimes(1);
  }, 20_000);

  it("bước 4 kết quả: cảnh báo rời trang (beforeunload + in-app) khi có mật khẩu hệ thống sinh chưa tải, xóa cảnh báo sau khi tải hoặc xóa file", async () => {
    const failedRow: ImportRow = {
      id: "row-fail",
      rowNo: 2,
      username: "student09",
      fullName: "Học viên 09",
      classCode: "LOP-B",
      passwordGenerated: true,
      status: "FAILED",
      errors: [],
      failureCode: "IDENTITY_PROVIDER_ERROR",
      failureReason: "Raw backend reason",
    };

    const deps = makeDeps({
      getImportBatch: vi.fn(async () =>
        makeBatch({
          status: "COMPLETED_WITH_ERRORS",
          totalRows: 2,
          validRows: 2,
          createdRows: 1,
          failedRows: 1,
          hasGeneratedPasswords: true,
          resultAvailable: true,
        }),
      ),
      listImportBatchRows: vi.fn(async () => ({
        items: [failedRow],
        page: 0,
        size: 20,
        totalItems: 1,
        totalPages: 1,
      })),
    });

    renderBatch(deps);

    expect(await screen.findByText("student09")).toBeInTheDocument();
    expect(
      screen.getByText(viMessages.bulkImport.failureCodes.IDENTITY_PROVIDER_ERROR),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Raw backend reason/)).not.toBeInTheDocument();

    // 1. beforeunload chặn khi chưa tải file kết quả
    const beforeUnloadEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(beforeUnloadEvent);
    expect(beforeUnloadEvent.defaultPrevented).toBe(true);

    // 2. Bấm link in-app "Về danh sách tài khoản" -> mở ConfirmDialog cảnh báo rời trang
    await userEvent.click(screen.getByRole("link", { name: "Về danh sách tài khoản" }));
    const leaveDialog = await screen.findByRole("dialog");
    expect(
      within(leaveDialog).getByText("Rời trang khi chưa tải file kết quả?"),
    ).toBeInTheDocument();
    await userEvent.click(within(leaveDialog).getByRole("button", { name: "Ở lại trang" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(routerMock.push).not.toHaveBeenCalled();

    // Thử lại bấm link rồi chọn "Vẫn rời trang"
    await userEvent.click(screen.getByRole("link", { name: "Về danh sách tài khoản" }));
    const leaveDialog2 = await screen.findByRole("dialog");
    await userEvent.click(within(leaveDialog2).getByRole("button", { name: "Vẫn rời trang" }));
    await waitFor(() => expect(routerMock.push).toHaveBeenCalledWith("/tai-khoan"));

    // 3. Tải file kết quả -> gỡ cảnh báo rời trang
    await userEvent.click(screen.getByRole("button", { name: "Tải file kết quả" }));
    await waitFor(() => {
      expect(deps.downloadImportResult).toHaveBeenCalledWith(BATCH_ID);
      expect(deps.saveBlobToDisk).toHaveBeenCalledWith(
        expect.any(Blob),
        `import-result-${BATCH_ID}.xlsx`,
      );
    });

    const afterDownloadEvent = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(afterDownloadEvent);
    expect(afterDownloadEvent.defaultPrevented).toBe(false);

    // 4. Xóa file kết quả -> ConfirmDialog -> deleteImportResult
    await userEvent.click(screen.getByRole("button", { name: "Xóa file kết quả" }));
    const deleteDialog = await screen.findByRole("dialog");
    expect(within(deleteDialog).getByText("Xóa file kết quả?")).toBeInTheDocument();
    await userEvent.click(within(deleteDialog).getByRole("button", { name: "Xóa file kết quả" }));
    await waitFor(() => expect(deps.deleteImportResult).toHaveBeenCalledWith(BATCH_ID));
    expect(await screen.findByText("File kết quả đã được xóa khỏi hệ thống.")).toBeInTheDocument();
  });
});

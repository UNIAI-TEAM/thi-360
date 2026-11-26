import { onlineManager, QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { focusRingInset, UiStringsProvider } from "@thi360/ui";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { NextIntlClientProvider } from "next-intl";
import type { ComponentType, ReactNode } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

import { SessionProvider } from "@/features/session/session-provider";
import en from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { installContainerWidth, PHONE_CONTENT_WIDTH } from "@/test/container-width";
import { fail, fakeApi, type FakeResult, ok, withRealApi } from "@/test/fake-api";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { APP_TIME_ZONE, INTL_FORMATS } from "@thi360/web-kit/preferences";

import { bundleKeys } from "./keys";

import {
  BUNDLE_ID_1,
  bundleMockPush,
  EXAM_HOA_ID,
  EXAM_KIND_OPTIONS,
  EXAM_LY_ID,
  EXAM_SPT_TOAN_ID,
  EXAM_TOAN_ID,
  FULL_BUNDLE_PERMISSIONS,
  KIND_SP2E_ID,
  makeBundleDetail,
  makeCandidate,
  renderBundlesUi,
  resetBundleNavigation,
} from "./test/bundle-fixtures";
import { BundleCreatePage, BundleEditPage } from "./components/bundle-form-page";

vi.mock("@/lib/api/client", async () => {
  const fake = await import("@/test/fake-api");
  return { createBrowserApiClient: fake.fakeBrowserApiClient };
});
vi.mock("next/navigation", async () => {
  const fixtures = await import("./test/bundle-fixtures");
  return fixtures.createBundleNextNavMock();
});
withRealApi();

describe("BundleCreatePage & BundleEditPage (AC-5, AC-6, BS 2)", () => {
  let pushedRoutes: string[];

  beforeEach(() => {
    fakeApi.reset();
    resetBundleNavigation("/bundles/new", "");
    pushedRoutes = [];
    bundleMockPush.fn = (href: string) => pushedRoutes.push(href);
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
  });

  afterEach(() => {
    onlineManager.setOnline(true);
    vi.unstubAllGlobals();
    vi.restoreAllMocks();
  });

  it("tạo bộ đề: hiển thị ứng viên theo môn (kèm số câu, thời lượng, điểm, nhãn Bài ôn), kiểm tra 2-12 bài, đổi thứ tự lên/xuống và gửi CreateBundleRequest", async () => {
    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([
        makeCandidate({
          examId: EXAM_TOAN_ID,
          code: "SP2E-TOAN-01",
          name: "SP2E Toán – Đề 1",
          buildMode: "STANDARD",
          subjectCode: "TOAN",
          subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
          questionCount: { min: 40, max: 40 },
          durationMinutes: { min: 90, max: 90 },
          maxPoints: { min: 10, max: 10 },
        }),
        makeCandidate({
          examId: EXAM_LY_ID,
          code: "SP2E-LY-REV",
          name: "SP2E Vật lí – Ôn tập",
          buildMode: "REVIEW",
          subjectItemId: "sub-ly",
          subjectCode: "LY",
          subjectName: { nameVi: "Vật lí", nameEn: "" },
          questionCount: { min: 28, max: 30 },
          durationMinutes: { min: 60, max: 60 },
          maxPoints: { min: 9.5, max: 10 },
        }),
        makeCandidate({
          examId: EXAM_HOA_ID,
          code: "SP2E-HOA-01",
          name: "SP2E Hóa học – Đề 1",
          buildMode: "STANDARD",
          subjectItemId: "sub-hoa",
          subjectCode: "HOA",
          subjectName: { nameVi: "Hóa học", nameEn: "Chemistry" },
          questionCount: { min: 50, max: 50 },
          durationMinutes: { min: 75, max: 75 },
          maxPoints: { min: 12, max: 12 },
        }),
      ]),
    );

    const createdRequests: Record<string, unknown>[] = [];
    fakeApi.post("/api/v1/admin/bundles", (call) => {
      createdRequests.push(call.options?.body as Record<string, unknown>);
      return ok(makeBundleDetail({ id: BUNDLE_ID_1, code: "BO-SP2E-NEW" }), { status: 201 });
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleCreatePage />);

    // Select exam kind SP2E
    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }));

    // Candidates loaded with metrics and REVIEW badge
    const candidatesTable = await screen.findByRole("table", {
      name: "Danh sách bài thi đang bán cùng kỳ thi",
    });
    expect(within(candidatesTable).getByText("SP2E Toán – Đề 1")).toHaveAttribute("data-exam-content");
    expect(within(candidatesTable).getByText("SP2E-TOAN-01")).toBeInTheDocument();
    expect(within(candidatesTable).getByText("40 câu")).toBeInTheDocument();
    expect(within(candidatesTable).getByText("90 phút")).toBeInTheDocument();
    expect(within(candidatesTable).getByText("10 điểm")).toBeInTheDocument();
    expect(within(candidatesTable).getByText("28–30 câu")).toBeInTheDocument();
    expect(within(candidatesTable).getByText("9,5–10 điểm")).toBeInTheDocument();
    expect(within(candidatesTable).getByText("Bài ôn")).toBeInTheDocument();

    // Fill code & name, pick only 1 exam -> validation blocks (< 2 exams)
    await user.type(screen.getByLabelText(/Mã bộ/), "BO-SP2E-NEW");
    await user.type(screen.getByLabelText(/Tên bộ/), "Bộ đề mới SP2E");
    await user.click(screen.getByRole("checkbox", { name: "Chọn SP2E-TOAN-01" }));
    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));
    expect(await screen.findByText(/Bộ đề phải chọn từ 2 đến 12 bài thi/)).toBeInTheDocument();
    expect(createdRequests).toHaveLength(0);

    // Pick second exam & reorder Up
    await user.click(screen.getByRole("checkbox", { name: "Chọn SP2E-LY-REV" }));
    const orderList = screen.getByRole("list", { name: "Thứ tự bài thi trong bộ" });
    expect(within(orderList).getAllByRole("listitem")).toHaveLength(2);
    await user.click(within(orderList).getByRole("button", { name: "Đưa SP2E-LY-REV lên" }));

    // Price, attempts, benefits, result view
    await user.type(screen.getByLabelText(/Giá bộ/), "299000");
    await user.type(screen.getByLabelText(/Số lượt mỗi bài/), "3");
    await user.click(screen.getByRole("radio", { name: /Điểm, đáp án và lời giải/ }));

    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));

    await waitFor(() => {
      expect(createdRequests).toHaveLength(1);
    });
    expect(createdRequests[0]).toMatchObject({
      code: "BO-SP2E-NEW",
      name: "Bộ đề mới SP2E",
      examKindItemId: KIND_SP2E_ID,
      examIds: [EXAM_LY_ID, EXAM_TOAN_ID],
      listPriceVnd: 299000,
      attemptsPerExam: 3,
      resultView: "FULL_SOLUTION",
    });
    expect(pushedRoutes).toContain(`/bundles/${BUNDLE_ID_1}`);
  });

  it("đổi kỳ thi khi đã chọn bài mở hộp thoại xác nhận trước khi xóa bài đã chọn", async () => {
    fakeApi.get("/api/v1/admin/bundles/candidates", (call) => {
      const kind = (call.options?.params as { query?: { examKind?: string } } | undefined)?.query?.examKind;
      if (kind === KIND_SP2E_ID) {
        return ok([
          makeCandidate({ examId: EXAM_TOAN_ID, name: "SP2E Toán – Đề 1" }),
          makeCandidate({ examId: EXAM_LY_ID, code: "SP2E-LY-01", name: "SP2E Vật lí – Đề 1" }),
        ]);
      }
      return ok([
        makeCandidate({
          examId: EXAM_SPT_TOAN_ID,
          code: "SPT-TOAN-01",
          name: "SPT Toán – Đề 1",
        }),
      ]);
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleCreatePage />);

    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }));
    await user.click(await screen.findByRole("checkbox", { name: "Chọn SP2E-TOAN-01" }));

    // Try switching to SPT -> dialog opens
    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội" }));

    const dialog = await screen.findByRole("dialog", { name: /Đổi kỳ thi sang/ });
    // Cancel keeps SP2E & selected exam
    await user.click(within(dialog).getByRole("button", { name: /Giữ/ }));
    expect(screen.getByRole("list", { name: "Thứ tự bài thi trong bộ" })).toHaveTextContent("SP2E Toán – Đề 1");

    // Re-open and confirm -> clears selected exams & switches to SPT
    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội" }));
    const confirmDialog = await screen.findByRole("dialog", { name: /Đổi kỳ thi sang/ });
    await user.click(within(confirmDialog).getByRole("button", { name: "Đổi và bỏ bài đã chọn" }));

    await waitFor(() => {
      expect(screen.getByText("SPT Toán – Đề 1")).toBeInTheDocument();
    });
    expect(screen.queryByRole("list", { name: "Thứ tự bài thi trong bộ" })).toBeNull();
  });

  it("chế độ en dùng pickName cho subjectName (nameEn và fallback về nameVi khi rỗng)", async () => {
    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([
        makeCandidate({
          examId: EXAM_TOAN_ID,
          subjectCode: "TOAN",
          subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
        }),
        makeCandidate({
          examId: EXAM_LY_ID,
          code: "SP2E-LY-01",
          name: "SP2E Vật lí – Đề 1",
          subjectItemId: "sub-ly",
          subjectCode: "LY",
          subjectName: { nameVi: "Vật lí", nameEn: "" },
        }),
      ]),
    );

    const user = userEvent.setup();
    renderBundlesUi(<BundleCreatePage />, { locale: "en" });

    await user.click(screen.getByRole("combobox", { name: /Exam kind/ }));
    await user.click(await screen.findByRole("option", { name: "HNUE2 Assessment" }));

    const table = await screen.findByRole("table", {
      name: "Eligible on-sale exams for this exam kind",
    });
    const mathHeader = within(table).getByText("Mathematics");
    expect(mathHeader.closest("[data-exam-content]")).toBeNull();
    const fallbackHeader = within(table).getByText("Vật lí");
    expect(fallbackHeader.closest("[data-exam-content]")).toBeNull();
  });

  it("AC-6 & UNAVAILABLE: chỉnh sửa gửi expectedRevision, hiện cảnh báo bài ngừng bán, và khi 412 tự nạp lại bản mới + giữ banner role='alert'", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let getCount = 0;
    const putBodies: Record<string, unknown>[] = [];

    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      getCount += 1;
      if (getCount === 1) {
        return ok(
          makeBundleDetail({
            id: BUNDLE_ID_1,
            name: "Bộ đề cũ",
            revision: 3,
            status: "PUBLISHED",
            saleState: "UNAVAILABLE",
            items: [
              {
                position: 1,
                examId: EXAM_TOAN_ID,
                code: "SP2E-TOAN-01",
                name: "SP2E Toán – Đề 1",
                scope: "SUBJECT",
                buildMode: "STANDARD",
                saleState: "ON_SALE",
                questionCount: { min: 40, max: 40 },
                durationMinutes: { min: 90, max: 90 },
                maxPoints: { min: 10, max: 10 },
              },
              {
                position: 2,
                examId: EXAM_LY_ID,
                code: "SP2E-LY-01",
                name: "SP2E Vật lí – Đã ngừng",
                scope: "SUBJECT",
                buildMode: "STANDARD",
                saleState: "DISCONTINUED",
                questionCount: { min: 28, max: 28 },
                durationMinutes: { min: 60, max: 60 },
                maxPoints: { min: 10, max: 10 },
              },
            ],
          }),
        );
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          name: "Bộ đề từ người khác",
          revision: 4,
          status: "PUBLISHED",
          saleState: "ON_SALE",
        }),
      );
    });

    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([makeCandidate({ examId: EXAM_TOAN_ID, name: "SP2E Toán – Đề 1" })]),
    );

    fakeApi.put("/api/v1/admin/bundles/{id}", (call) => {
      putBodies.push(call.options?.body as Record<string, unknown>);
      return fail(412, { code: "BUNDLE_REVISION_CONFLICT" });
    });

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    // Unavailable banner names discontinued item
    expect(await screen.findByText("Bộ đề đang không bán được.")).toBeInTheDocument();
    expect(screen.getAllByText("SP2E Vật lí – Đã ngừng").length).toBeGreaterThanOrEqual(1);

    const nameInput = screen.getByLabelText(/Tên bộ/);
    await user.clear(nameInput);
    await user.type(nameInput, "Tên sửa local");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    await waitFor(() => {
      expect(putBodies).toHaveLength(1);
      expect(putBodies[0]?.expectedRevision).toBe(3);
    });

    // Reloaded from server automatically & keeps visible role="alert" conflict banner
    await waitFor(() => {
      expect(getCount).toBe(2);
      expect(screen.getByLabelText(/Tên bộ/)).toHaveValue("Bộ đề từ người khác");
    });
    const alerts = screen.getAllByRole("alert");
    expect(
      alerts.some((el) =>
        el.textContent?.includes("Bộ đề vừa được người khác cập nhật."),
      ),
    ).toBe(true);
  });

  it("F-1, F-2, V-4: picker desktop 596px dựng table, mobile 326px dựng card, aria-label dùng mã và nhãn Tóm tắt không gãy dòng", async () => {
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(makeBundleDetail({ id: BUNDLE_ID_1, name: "Bộ đề tiếng Việt dài", status: "DRAFT" })),
    );
    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([
        makeCandidate({ examId: EXAM_TOAN_ID, code: "SP2E-TOAN-01", name: "SP2E Toán – Đề 1" }),
        makeCandidate({
          examId: EXAM_LY_ID,
          code: "SP2E-LY-01",
          name: "SP2E Vật lí – Đề 1",
          subjectItemId: "sub-ly",
          subjectCode: "LY",
          subjectName: { nameVi: "Vật lí", nameEn: "Physics" },
        }),
      ]),
    );

    const resizeTo = installContainerWidth(596);
    const { container } = renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />, { locale: "en" });

    const heading = await screen.findByRole("heading", { level: 1, name: "Bộ đề tiếng Việt dài" });
    expect(heading.querySelector("[data-exam-content]")).not.toBeNull();

    // Desktop 596px container renders candidate table (threshold 500)
    const table = await screen.findByRole("table", { name: "Eligible on-sale exams for this exam kind" });
    expect(within(table).getByRole("checkbox", { name: "Select SP2E-TOAN-01" })).toBeInTheDocument();

    // Order controls and checkboxes use ASCII codes instead of Vietnamese names
    const orderList = screen.getByRole("list", { name: "Order of exams in bundle" });
    expect(within(orderList).getByRole("button", { name: "Move SP2E-TOAN-01 up" })).toBeInTheDocument();
    expect(within(orderList).getByRole("button", { name: "Move SP2E-TOAN-01 down" })).toBeInTheDocument();
    expect(within(orderList).getByRole("button", { name: "Remove SP2E-TOAN-01 from bundle" })).toBeInTheDocument();

    // Summary <dt> keeps shrink-0 whitespace-nowrap
    const summaryHeading = screen.getByRole("heading", { level: 2, name: "Summary" });
    const examKindDt = within(summaryHeading.closest("aside")!).getByText("Exam kind");
    expect(examKindDt).toHaveClass("shrink-0", "whitespace-nowrap");

    // Verify no Vietnamese diacritics appear outside [data-exam-content] (text or aria-label);
    // ignore hidden Radix <select aria-hidden="true"> options where SPT test fixture intentionally uses empty nameEn fallback.
    const clone = container.cloneNode(true) as HTMLElement;
    clone.querySelectorAll("[data-exam-content], select[aria-hidden='true']").forEach((el) => el.remove());
    expect(clone.textContent).not.toMatch(/[ăâđêôơưàáạảãèéẹẻẽìíịỉĩòóọỏõùúụủũỳýỵỷỹ]/i);
    for (const el of clone.querySelectorAll("[aria-label]")) {
      expect(el.getAttribute("aria-label")).not.toMatch(/[ăâđêôơưàáạảãèéẹẻẽìíịỉĩòóọỏõùúụủũỳýỵỷỹ]/i);
    }

    // Narrow mobile container (326px < 500px) switches candidate picker to cards
    resizeTo(Math.max(326, PHONE_CONTENT_WIDTH - 32));
    await waitFor(() => {
      expect(screen.queryByRole("table", { name: "Eligible on-sale exams for this exam kind" })).toBeNull();
    });
    expect(screen.getByRole("checkbox", { name: "Select SP2E-TOAN-01" })).toBeInTheDocument();
  });

  it("R1-18: lỗi submit lưu lỗi gốc để đổi ngôn ngữ tự dịch lại cảnh báo đang hiện", async () => {
    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([
        makeCandidate({ examId: EXAM_TOAN_ID, code: "SP2E-TOAN-01" }),
        makeCandidate({ examId: EXAM_LY_ID, code: "SP2E-LY-01", subjectItemId: "sub-ly", subjectCode: "LY" }),
      ]),
    );
    fakeApi.post("/api/v1/admin/bundles", () => fail(500, { code: "INTERNAL_ERROR" }));

    const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
    const SessionWrapper = SessionProvider as ComponentType<{
      loadSession?: () => Promise<typeof TEST_ADMIN_SESSION>;
      children?: ReactNode;
    }>;
    const renderAtLocale = (locale: "vi" | "en") => (
      <NextIntlClientProvider
        locale={locale === "en" ? "en-US" : "vi-VN"}
        messages={locale === "en" ? en : viMessages}
        timeZone={APP_TIME_ZONE}
        formats={INTL_FORMATS}
      >
        <UiStringsProvider locale={locale}>
          <QueryClientProvider client={queryClient}>
            <SessionWrapper
              loadSession={async () => ({
                ...TEST_ADMIN_SESSION,
                permissions: [...FULL_BUNDLE_PERMISSIONS],
              })}
            >
              <BundleCreatePage />
            </SessionWrapper>
          </QueryClientProvider>
        </UiStringsProvider>
      </NextIntlClientProvider>
    );

    const user = userEvent.setup();
    const { rerender } = render(renderAtLocale("vi"));

    await user.click(await screen.findByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }));
    await user.type(screen.getByLabelText(/Mã bộ/), "BO-ERR-01");
    await user.type(screen.getByLabelText(/Tên bộ/), "Bộ đề lỗi");
    await user.click(await screen.findByRole("checkbox", { name: "Chọn SP2E-TOAN-01" }));
    await user.click(screen.getByRole("checkbox", { name: "Chọn SP2E-LY-01" }));
    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));

    expect(
      await screen.findByText("Hệ thống gặp sự cố tạm thời. Vui lòng thử lại sau."),
    ).toBeInTheDocument();

    rerender(renderAtLocale("en"));
    expect(
      await screen.findByText("Temporary server failure. Please try again shortly."),
    ).toBeInTheDocument();
  });

  it("R1-19 & R1-20: gửi lỗi thiếu Kỳ thi hoặc thiếu bài thi đưa focus tới đúng điều khiển, và bấm mục Cần xử lý đưa focus tới ô tương ứng", async () => {
    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([
        makeCandidate({ examId: EXAM_TOAN_ID, code: "SP2E-TOAN-01" }),
        makeCandidate({ examId: EXAM_LY_ID, code: "SP2E-LY-01", subjectItemId: "sub-ly", subjectCode: "LY" }),
      ]),
    );

    const user = userEvent.setup();
    renderBundlesUi(<BundleCreatePage />);

    // 1. Submit empty form -> focuses Exam Kind trigger
    const saveDraftBtn = await screen.findByRole("button", { name: "Lưu nháp" });
    await user.click(saveDraftBtn);
    const examKindCombo = screen.getByRole("combobox", { name: /Kỳ thi/ });
    expect(document.activeElement).toBe(examKindCombo);

    // 2. Select exam kind, fill code & name, leave examIds < 2 -> submit focuses first candidate checkbox
    await user.click(examKindCombo);
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }));
    const firstCheckbox = await screen.findByRole("checkbox", { name: "Chọn SP2E-TOAN-01" });
    await user.type(screen.getByLabelText(/Mã bộ/), "BO-FOCUS-01");
    await user.type(screen.getByLabelText(/Tên bộ/), "Bộ đề focus");
    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));
    expect(document.activeElement).toBe(firstCheckbox);

    // 3. Checklist links in Summary aside move focus to actual controls
    await user.click(screen.getByRole("link", { name: "Chưa có mô tả bộ đề." }));
    expect(document.activeElement).toBe(screen.getByLabelText("Mô tả"));

    await user.click(screen.getByRole("link", { name: "Chưa đặt giá bộ đề lớn hơn 0 đồng." }));
    expect(document.activeElement).toBe(screen.getByLabelText("Giá bộ"));

    await user.click(screen.getByRole("link", { name: "Chưa đặt số lượt mỗi bài (1–100)." }));
    expect(document.activeElement).toBe(screen.getByLabelText("Số lượt mỗi bài"));

    await user.click(screen.getByRole("link", { name: /Chọn ít nhất 2 bài/ }));
    expect(document.activeElement).toBe(firstCheckbox);
  });

  it("R1-21/22/23 & R1-40: xử lý lỗi tải kỳ thi, ngoại tuyến/lỗi/rỗng danh sách ứng viên, và trạng thái tải/404/500/DISCONTINUED/BUNDLE_CODE_TAKEN", async () => {
    // 1. examKindsQuery error + Retry, candidatesQuery offline + empty + error + Retry, BUNDLE_CODE_TAKEN
    let kindCalls = 0;
    fakeApi.get("/api/v1/catalog/options", () => {
      kindCalls += 1;
      if (kindCalls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(EXAM_KIND_OPTIONS);
    });
    let candCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/candidates", () => {
      candCalls += 1;
      if (candCalls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      if (candCalls === 2) return ok([]);
      return ok([
        makeCandidate({ examId: EXAM_TOAN_ID, code: "SP2E-TOAN-01" }),
        makeCandidate({ examId: EXAM_LY_ID, code: "SP2E-LY-01", subjectItemId: "sub-ly", subjectCode: "LY" }),
      ]);
    });
    fakeApi.post("/api/v1/admin/bundles", () => fail(409, { code: "BUNDLE_CODE_TAKEN" }));

    const user = userEvent.setup();
    const { unmount } = renderBundlesUi(<BundleCreatePage />);

    expect(await screen.findByText("Không tải được danh sách kỳ thi.")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(screen.queryByText("Không tải được danh sách kỳ thi.")).toBeNull();
    });

    // Offline before selecting exam kind -> candidates shows offline notice instead of empty
    act(() => {
      onlineManager.setOnline(false);
    });
    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }));
    expect(
      await screen.findByText("Thiết bị đang ngoại tuyến nên tạm dừng tải danh sách bài thi."),
    ).toBeInTheDocument();
    expect(screen.queryByText(/Kỳ thi này chưa có bài thi đang bán phù hợp/)).toBeNull();

    // Online resumes paused query -> 500 error -> Retry -> empty
    act(() => {
      onlineManager.setOnline(true);
    });
    expect(await screen.findByText("Hệ thống gặp sự cố tạm thời. Vui lòng thử lại sau.")).toBeInTheDocument();

    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    expect(await screen.findByText(/Kỳ thi này chưa có bài thi đang bán phù hợp/)).toBeInTheDocument();

    // Re-trigger fetch by toggling exam kind to SPT then back to SP2E
    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội" }));

    await user.click(await screen.findByRole("checkbox", { name: "Chọn SP2E-TOAN-01" }));
    await user.click(screen.getByRole("checkbox", { name: "Chọn SP2E-LY-01" }));
    const codeInput = screen.getByLabelText(/Mã bộ/);
    await user.type(codeInput, "BO-TAKEN");
    await user.type(screen.getByLabelText(/Tên bộ/), "Bộ đề trùng mã");
    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));

    expect(await screen.findByText("Mã bộ đề này đã tồn tại trên hệ thống.")).toBeInTheDocument();
    expect(codeInput).toHaveAttribute("aria-invalid", "true");
    expect(document.activeElement).toBe(codeInput);
    unmount();

    // 2. BundleEditPage loading state
    fakeApi.pending("/api/v1/admin/bundles/{id}");
    const { container: loadingContainer, unmount: unmountLoading } = renderBundlesUi(
      <BundleEditPage bundleId={BUNDLE_ID_1} />,
    );
    expect(loadingContainer.querySelector('[aria-busy="true"]')).not.toBeNull();
    unmountLoading();

    // 3. BundleEditPage 404 state
    fakeApi.get("/api/v1/admin/bundles/{id}", () => fail(404, { code: "BUNDLE_NOT_FOUND" }));
    const { unmount: unmount404 } = renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);
    expect(await screen.findByText("Không tìm thấy bộ đề")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Quay lại danh sách" })).toHaveAttribute("href", "/bundles");
    unmount404();

    // 4. BundleEditPage 500 + Retry -> recovers to DISCONTINUED lockout even with EXAM.MODIFY + EXAM.PUBLISH
    let editCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      editCalls += 1;
      if (editCalls === 1) return fail(500, { code: "INTERNAL_ERROR" });
      return ok(makeBundleDetail({ id: BUNDLE_ID_1, status: "DISCONTINUED", saleState: "DISCONTINUED" }));
    });
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />, {
      permissions: ["EXAM.VIEW", "EXAM.MODIFY", "EXAM.PUBLISH"],
    });
    expect(await screen.findByText("Không tải được bộ đề")).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    expect(
      await screen.findByText("Bộ đề đã ngừng bán nên không thể chỉnh sửa thêm."),
    ).toBeInTheDocument();
    expect(screen.getByLabelText(/Tên bộ/)).toBeDisabled();
    expect(screen.queryByRole("button", { name: "Lưu thay đổi" })).toBeNull();
    expect(screen.queryByRole("button", { name: /Công bố/ })).toBeNull();
    expect(screen.queryByRole("button", { name: "Ngừng bán" })).toBeNull();
  });

  it("R1-26/27 & R1-48: refetch lỗi sau 412 giữ nguyên dữ liệu đang sửa + hiện nút Thử lại, đồng thời hiển thị cả hai banner UNAVAILABLE và chưa công bố", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let getCalls = 0;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      getCalls += 1;
      if (getCalls === 1) {
        return ok(
          makeBundleDetail({
            id: BUNDLE_ID_1,
            name: "Bộ đề gốc",
            revision: 3,
            status: "PUBLISHED",
            saleState: "UNAVAILABLE",
            hasUnpublishedChanges: true,
            servingVersion: 2,
          }),
        );
      }
      if (getCalls === 2) {
        return fail(500, { code: "INTERNAL_ERROR" });
      }
      return ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          name: "Bộ đề từ người khác",
          revision: 4,
          status: "PUBLISHED",
          saleState: "ON_SALE",
        }),
      );
    });
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));
    fakeApi.put("/api/v1/admin/bundles/{id}", () => fail(412, { code: "BUNDLE_REVISION_CONFLICT" }));

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    // R1-48: both unavailable and unpublished-changes banners appear
    expect(await screen.findByText("Bộ đề đang không bán được.")).toBeInTheDocument();
    expect(screen.getByText(/Đang bán phiên bản 2 · Có thay đổi chưa công bố/)).toBeInTheDocument();

    const nameInput = screen.getByLabelText(/Tên bộ/);
    await user.clear(nameInput);
    await user.type(nameInput, "Tên sửa chưa lưu");
    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    // Refetch failed: form keeps unsaved input and shows ONE coherent conflict-refetch-failed alert (no contradictory "has been reloaded" copy)
    expect(
      await screen.findByText(
        "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất.",
      ),
    ).toBeInTheDocument();
    expect(
      screen.getByText(/Thay đổi chưa lưu vẫn được giữ nguyên\. Bấm Thử lại để nạp bản mới nhất/),
    ).toBeInTheDocument();
    expect(
      screen.queryByText(
        "Bản mới nhất từ máy chủ đã được nạp lại; thay đổi chưa lưu của bạn không ghi đè dữ liệu máy chủ.",
      ),
    ).toBeNull();
    expect(screen.queryByText("Không tải được bộ đề")).toBeNull();
    expect(screen.getByLabelText(/Tên bộ/)).toHaveValue("Tên sửa chưa lưu");

    // Click Retry -> succeeds, loads server revision 4, and transitions cleanly to reloaded conflict banner
    await user.click(screen.getByRole("button", { name: "Thử lại" }));
    await waitFor(() => {
      expect(screen.getByLabelText(/Tên bộ/)).toHaveValue("Bộ đề từ người khác");
    });
    expect(
      screen.queryByText(
        "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất.",
      ),
    ).toBeNull();
    expect(screen.getByText("Bộ đề vừa được người khác cập nhật.")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Bản mới nhất từ máy chủ đã được nạp lại; thay đổi chưa lưu của bạn không ghi đè dữ liệu máy chủ.",
      ),
    ).toBeInTheDocument();
  });

  it("R1-28, R1-31, R1-32, R1-51 & gộp nhóm môn trùng: beforeunload khi bẩn, region cuộn bảng, vùng chạm checkbox, focus + LiveStatus khi xóa, fieldErrors có chỉ số và không trùng tiêu đề môn", async () => {
    fakeApi.get("/api/v1/admin/bundles/candidates", () =>
      ok([
        makeCandidate({
          examId: EXAM_TOAN_ID,
          code: "SP2E-TOAN-01",
          name: "SP2E Toán – Đề 1",
          scope: "SUBJECT",
          subjectItemId: "sub-toan-subject",
          subjectCode: "TOAN",
          subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
        }),
        makeCandidate({
          examId: EXAM_LY_ID,
          code: "SP2E-TOAN-P1",
          name: "SP2E Toán – Phần 1",
          scope: "PART",
          subjectItemId: "sub-toan-part",
          subjectCode: "TOAN",
          subjectName: { nameVi: "Toán học", nameEn: "Mathematics" },
        }),
      ]),
    );
    fakeApi.post("/api/v1/admin/bundles", () =>
      fail(422, {
        code: "VALIDATION_FAILED",
        fieldErrors: [{ field: "examIds[0]", message: "invalid item" }],
      }),
    );

    const user = userEvent.setup();
    renderBundlesUi(<BundleCreatePage />);

    // Clean form does not cancel beforeunload
    const cleanUnload = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(cleanUnload);
    expect(cleanUnload.defaultPrevented).toBe(false);

    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    await user.click(await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }));

    // R1-31: candidate table horizontal scroll wrapper has region + name + tabIndex=0 + focusRingInset
    const scrollRegion = await screen.findByRole("region", {
      name: "Danh sách bài thi đang bán cùng kỳ thi",
    });
    expect(scrollRegion).toHaveAttribute("tabindex", "0");
    expect(scrollRegion).toHaveClass(...focusRingInset.split(/\s+/));

    // Visual-r2: PART & SUBJECT with same subject render ONE "Toán học" header, not two
    const table = within(scrollRegion).getByRole("table", {
      name: "Danh sách bài thi đang bán cùng kỳ thi",
    });
    expect(within(table).getAllByText("Toán học")).toHaveLength(1);

    // R1-31: candidate checkboxes expand coarse-pointer hit area
    const cb1 = within(table).getByRole("checkbox", { name: "Chọn SP2E-TOAN-01" });
    const cb2 = within(table).getByRole("checkbox", { name: "Chọn SP2E-TOAN-P1" });
    expect(cb1).toHaveClass("pointer-coarse:after:absolute", "pointer-coarse:after:-inset-3");

    // Pick both exams -> dirty form triggers beforeunload
    await user.click(cb1);
    await user.click(cb2);
    const dirtyUnload = new Event("beforeunload", { cancelable: true });
    window.dispatchEvent(dirtyUnload);
    expect(dirtyUnload.defaultPrevented).toBe(true);

    // R1-51: server fieldErrors with indexed path examIds[0]
    await user.type(screen.getByLabelText(/Mã bộ/), "BO-IDX-01");
    await user.type(screen.getByLabelText(/Tên bộ/), "Bộ đề indexed");
    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));
    const orderList = screen.getByRole("list", { name: "Thứ tự bài thi trong bộ" });
    const errorId = orderList.getAttribute("aria-describedby");
    expect(errorId).toBeTruthy();
    expect(document.getElementById(errorId!)).toHaveAttribute("role", "alert");

    // R1-32: keyboard removal moves focus to surviving item's Remove button (never document.body), then heading when empty
    const removeFirst = within(orderList).getByRole("button", { name: "Bỏ SP2E-TOAN-01 khỏi bộ" });
    removeFirst.focus();
    await user.keyboard("{Enter}");

    const removeSecond = within(orderList).getByRole("button", { name: "Bỏ SP2E-TOAN-P1 khỏi bộ" });
    expect(document.activeElement).toBe(removeSecond);
    expect(document.activeElement).not.toBe(document.body);
    expect(
      screen.getByText("Đã bỏ SP2E-TOAN-01 khỏi bộ đề (còn 1 bài)."),
    ).toBeInTheDocument();

    await user.keyboard("{Enter}");
    const orderHeading = screen.getByRole("heading", { level: 3, name: "Thứ tự trong bộ" });
    expect(document.activeElement).toBe(orderHeading);
    expect(
      screen.getByText("Đã bỏ SP2E-TOAN-P1 khỏi bộ đề (còn 0 bài)."),
    ).toBeInTheDocument();
  });

  it("R2-9/10/11: liên kết Cần xử lý có pointer-coarse:min-h-11 và ở chế độ chỉ đọc không chặn điều hướng neo (preventDefault)", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "DRAFT",
          description: null,
          listPriceVnd: null,
          attemptsPerExam: null,
        }),
      ),
    );
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));

    const user = userEvent.setup();
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />, {
      permissions: ["EXAM.VIEW"],
    });

    const descLink = await screen.findByRole("link", { name: "Chưa có mô tả bộ đề." });
    const priceLink = screen.getByRole("link", { name: "Chưa đặt giá bộ đề lớn hơn 0 đồng." });
    const attemptsLink = screen.getByRole("link", { name: "Chưa đặt số lượt mỗi bài (1–100)." });

    for (const link of [descLink, priceLink, attemptsLink]) {
      expect(link).toHaveClass(
        "relative",
        "inline-flex",
        "items-center",
        "pointer-coarse:min-h-11",
      );

      let defaultPrevented: boolean | undefined;
      const listener = (event: Event) => {
        defaultPrevented = event.defaultPrevented;
      };
      document.addEventListener("click", listener, { once: true });
      await user.click(link);
      expect(defaultPrevented).toBe(false);
    }

    expect(screen.getByLabelText("Mô tả")).toBeDisabled();
    expect(document.activeElement).not.toBe(screen.getByLabelText("Mô tả"));
  });

  it("R2-12: mở /bundles/new khi ngoại tuyến hiện cảnh báo ngoại tuyến cho Kỳ thi và khôi phục khi bấm Thử lại lúc có mạng", async () => {
    let kindCalls = 0;
    fakeApi.get("/api/v1/catalog/options", () => {
      kindCalls += 1;
      return ok(EXAM_KIND_OPTIONS);
    });
    onlineManager.setOnline(false);

    const user = userEvent.setup();
    renderBundlesUi(<BundleCreatePage />);

    const offlineAlert = await screen.findByRole("alert");
    expect(offlineAlert).toHaveTextContent(
      "Thiết bị đang ngoại tuyến nên tạm dừng tải danh sách kỳ thi.",
    );
    const retryBtn = within(offlineAlert).getByRole("button", { name: "Thử lại" });
    expect(kindCalls).toBe(0);

    await user.click(retryBtn);
    expect(kindCalls).toBe(0);
    expect(
      screen.getByText("Thiết bị đang ngoại tuyến nên tạm dừng tải danh sách kỳ thi."),
    ).toBeInTheDocument();

    act(() => {
      onlineManager.setOnline(true);
    });
    await user.click(screen.getByRole("button", { name: "Thử lại" }));

    await waitFor(() => {
      expect(
        screen.queryByText("Thiết bị đang ngoại tuyến nên tạm dừng tải danh sách kỳ thi."),
      ).toBeNull();
    });
    await user.click(screen.getByRole("combobox", { name: /Kỳ thi/ }));
    expect(
      await screen.findByRole("option", { name: "Đánh giá năng lực ĐH Sư phạm Hà Nội 2" }),
    ).toBeInTheDocument();
  });

  it("R3-3 & R3-7: khi banner refetch lỗi sau 412 đang mở, nút Thử lại khóa khi đang refetch và nền refetch/invalidate lên revision mới tự chuyển sang banner đã nạp lại và mở khóa nút Công bố", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let getCalls = 0;
    let resolvePendingRefetch: ((res: FakeResult) => void) | null = null;
    let returnFreshRevision = false;

    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      getCalls += 1;
      if (getCalls === 1) {
        return ok(
          makeBundleDetail({
            id: BUNDLE_ID_1,
            name: "Bộ đề gốc",
            revision: 3,
            status: "DRAFT",
          }),
        );
      }
      if (getCalls === 2) {
        return fail(500, { code: "INTERNAL_ERROR" });
      }
      if (getCalls === 3) {
        return new Promise<FakeResult>((resolve) => {
          resolvePendingRefetch = resolve;
        });
      }
      if (returnFreshRevision) {
        return ok(
          makeBundleDetail({
            id: BUNDLE_ID_1,
            name: "Bộ đề cập nhật nền",
            revision: 4,
            status: "DRAFT",
          }),
        );
      }
      return fail(500, { code: "INTERNAL_ERROR" });
    });
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));
    fakeApi.put("/api/v1/admin/bundles/{id}", () => fail(412, { code: "BUNDLE_REVISION_CONFLICT" }));

    const user = userEvent.setup();
    const { client } = renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    const nameInput = await screen.findByLabelText(/Tên bộ/);
    await user.clear(nameInput);
    await user.type(nameInput, "Tên sửa chưa lưu");
    expect(screen.getByRole("button", { name: "Công bố" })).toBeDisabled();

    await user.click(screen.getByRole("button", { name: "Lưu thay đổi" }));

    const failedTitle = await screen.findByText(
      "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất.",
    );
    const failedBanner = failedTitle.closest('[role="alert"]') as HTMLElement;
    expect(failedBanner).toBeTruthy();

    // R3-7: clicking Retry passes loading={query.isRefetching}
    const retryBtn = within(failedBanner).getByRole("button", { name: "Thử lại" });
    await user.click(retryBtn);
    await waitFor(() => {
      expect(retryBtn).toHaveAttribute("aria-busy", "true");
    });

    await act(async () => {
      resolvePendingRefetch?.(fail(500, { code: "INTERNAL_ERROR" }));
    });
    await waitFor(() => {
      expect(retryBtn).not.toHaveAttribute("aria-busy");
    });

    // R3-3: background invalidateQueries/reconnect resolves with revision 4 -> banner transitions to reloaded and Publish unlocks
    returnFreshRevision = true;
    await act(async () => {
      await client.invalidateQueries({ queryKey: bundleKeys.detail(BUNDLE_ID_1) });
    });

    await waitFor(() => {
      expect(screen.getByLabelText(/Tên bộ/)).toHaveValue("Bộ đề cập nhật nền");
    });
    expect(
      screen.queryByText(
        "Bộ đề vừa được người khác cập nhật nhưng chưa nạp lại được bản mới nhất.",
      ),
    ).toBeNull();
    expect(screen.getByText("Bộ đề vừa được người khác cập nhật.")).toBeInTheDocument();
    expect(
      screen.getByText(
        "Bản mới nhất từ máy chủ đã được nạp lại; thay đổi chưa lưu của bạn không ghi đè dữ liệu máy chủ.",
      ),
    ).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Công bố" })).not.toBeDisabled();
    expect(screen.queryByText("Lưu thay đổi trước khi công bố")).toBeNull();
  });

  it("R3-7: banner lỗi inline (query.isError && query.data) hiển thị loading={query.isRefetching} trên nút Thử lại", async () => {
    resetBundleNavigation(`/bundles/${BUNDLE_ID_1}`, "");
    let getCalls = 0;
    let resolveRetry!: (res: FakeResult) => void;
    fakeApi.get("/api/v1/admin/bundles/{id}", () => {
      getCalls += 1;
      if (getCalls === 1) {
        return ok(makeBundleDetail({ id: BUNDLE_ID_1, name: "Bộ đề gốc", revision: 3 }));
      }
      if (getCalls === 2) {
        return fail(500, { code: "INTERNAL_ERROR" });
      }
      return new Promise<FakeResult>((resolve) => {
        resolveRetry = resolve;
      });
    });
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([makeCandidate()]));

    const user = userEvent.setup();
    const { client } = renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);
    await screen.findByRole("heading", { name: "Bộ đề gốc" });

    await act(async () => {
      await client.invalidateQueries({ queryKey: bundleKeys.detail(BUNDLE_ID_1) });
    });

    const inlineTitle = await screen.findByText("Không tải được bộ đề");
    const inlineAlert = inlineTitle.closest('[role="alert"]') as HTMLElement;
    const retryBtn = within(inlineAlert).getByRole("button", { name: "Thử lại" });
    await user.click(retryBtn);

    await waitFor(() => {
      expect(retryBtn).toHaveAttribute("aria-busy", "true");
    });

    await act(async () => {
      resolveRetry(ok(makeBundleDetail({ id: BUNDLE_ID_1, name: "Bộ đề gốc", revision: 3 })));
    });
    await waitFor(() => {
      expect(screen.queryByText("Không tải được bộ đề")).toBeNull();
    });
  });
});

describe("Đầu trang tạo và chi tiết bộ đề (UNI-2825)", () => {
  beforeEach(() => {
    fakeApi.reset();
    resetBundleNavigation("/bundles/new", "");
    fakeApi.get("/api/v1/catalog/options", () => ok(EXAM_KIND_OPTIONS));
    fakeApi.get("/api/v1/admin/bundles/candidates", () => ok([]));
  });

  function expectBackLinkAboveTitle(heading: HTMLElement, label: string, href: string) {
    const header = heading.closest('[data-slot="detail-header"]');
    expect(header).not.toBeNull();
    const back = within(header as HTMLElement).getByRole("link", { name: label });
    expect(back).toHaveAttribute("href", href);
    expect(back.compareDocumentPosition(heading) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    return header as HTMLElement;
  }

  it("trang tạo: lối Danh sách bộ đề về /bundles nằm trên tiêu đề", async () => {
    renderBundlesUi(<BundleCreatePage />);

    const heading = await screen.findByRole("heading", { level: 1, name: "Tạo bộ đề" });
    expectBackLinkAboveTitle(heading, "Danh sách bộ đề", "/bundles");
    expect(screen.queryByRole("link", { name: "Quay lại danh sách" })).not.toBeInTheDocument();
  });

  it("trang bộ đề: chip trạng thái cạnh tiêu đề, mã ở dòng phụ, Ngừng bán trước Công bố lại, không còn nút Quay lại", async () => {
    fakeApi.get("/api/v1/admin/bundles/{id}", () =>
      ok(
        makeBundleDetail({
          id: BUNDLE_ID_1,
          status: "PUBLISHED",
          saleState: "ON_SALE",
          hasUnpublishedChanges: true,
        }),
      ),
    );
    renderBundlesUi(<BundleEditPage bundleId={BUNDLE_ID_1} />);

    const heading = await screen.findByRole("heading", { level: 1, name: "Bộ đề SP2E khối A00" });
    const header = expectBackLinkAboveTitle(heading, "Danh sách bộ đề", "/bundles");
    expect(heading.parentElement).toHaveTextContent("Đang bán");
    expect(heading.parentElement).toHaveTextContent("Có thay đổi chưa công bố");
    expect(within(header).getByText("BO-SP2E-001")).toHaveAttribute("data-exam-content");

    const actions = header.querySelector('[data-slot="page-actions"]') as HTMLElement;
    const buttons = within(actions).getAllByRole("button");
    expect(buttons.map((b) => b.textContent)).toEqual(["Ngừng bán", "Công bố lại"]);
    expect(screen.queryByRole("link", { name: "Quay lại danh sách" })).not.toBeInTheDocument();
  });
});

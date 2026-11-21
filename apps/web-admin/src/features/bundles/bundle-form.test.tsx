import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { fail, fakeApi, ok, withRealApi } from "@/test/fake-api";

import {
  BUNDLE_ID_1,
  bundleMockPush,
  EXAM_HOA_ID,
  EXAM_KIND_OPTIONS,
  EXAM_LY_ID,
  EXAM_SPT_TOAN_ID,
  EXAM_TOAN_ID,
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
    await user.click(screen.getByRole("checkbox", { name: "Chọn SP2E Toán – Đề 1" }));
    await user.click(screen.getByRole("button", { name: "Lưu nháp" }));
    expect(await screen.findByText(/Bộ đề phải chọn từ 2 đến 12 bài thi/)).toBeInTheDocument();
    expect(createdRequests).toHaveLength(0);

    // Pick second exam & reorder Up
    await user.click(screen.getByRole("checkbox", { name: "Chọn SP2E Vật lí – Ôn tập" }));
    const orderList = screen.getByRole("list", { name: "Thứ tự bài thi trong bộ" });
    expect(within(orderList).getAllByRole("listitem")).toHaveLength(2);
    await user.click(within(orderList).getByRole("button", { name: "Đưa SP2E Vật lí – Ôn tập lên" }));

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
    await user.click(await screen.findByRole("checkbox", { name: "Chọn SP2E Toán – Đề 1" }));

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
});

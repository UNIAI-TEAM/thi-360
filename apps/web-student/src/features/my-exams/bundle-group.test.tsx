import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import type { MyExamCardData } from "@/features/my-exams/api";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { STUDENT_SESSION } from "@/test/storefront-fixtures";
import { groupMyExams } from "./group-my-exams";
import { MyExamsView } from "./my-exams-view";

function makeCard(overrides: Partial<MyExamCardData> = {}): MyExamCardData {
  return {
    entitlementId: "ent-1",
    code: "SP2E-TOAN-01",
    name: "SP2E Toán – Đề luyện số 1",
    examKind: { code: "SP2E", nameVi: "ĐHSP Hà Nội 2", nameEn: "HNUE2" },
    format: "SUBJECT",
    scopeLabel: { nameVi: "Toán", nameEn: "Math" },
    buildMode: "STANDARD",
    coverUrl: null,
    questionCount: { min: 40, max: 40 },
    durationMinutes: { min: 90, max: 90 },
    attemptsLeft: 2,
    attemptsTotal: 2,
    saleState: "ON_SALE",
    state: "CAN_START",
    activeAttemptId: null,
    activeAttemptState: null,
    resultAttemptId: null,
    hasPendingFulfillment: false,
    pendingOrderCode: null,
    bundles: [],
    actions: ["START", "BUY_MORE"],
    ...overrides,
  };
}

describe("groupMyExams & MyExamsView bundle grouping (AC-14)", () => {
  it("nhóm các bài theo bộ đề, mỗi bài giữ đủ nút hành động độc lập và bài mua lẻ nằm ngoài nhóm bộ", async () => {
    const bundleA = { bundleId: "bnd-a", code: "BO-SP2E-A00", name: "Bộ đề SP2E khối A00" };
    const mathInBundle = makeCard({
      entitlementId: "ent-math",
      code: "SP2E-TOAN-01",
      name: "SP2E Toán – Đề luyện số 1",
      state: "IN_PROGRESS",
      attemptsLeft: 1,
      activeAttemptId: "att-math-1",
      resultAttemptId: "att-math-res",
      bundles: [bundleA],
      actions: ["CONTINUE", "VIEW_RESULT", "HISTORY", "BUY_MORE"],
    });
    const physInBundle = makeCard({
      entitlementId: "ent-phys",
      code: "SP2E-LY-01",
      name: "SP2E Vật lí – Đề luyện số 1",
      scopeLabel: { nameVi: "Vật lí", nameEn: "Physics" },
      state: "CAN_START",
      attemptsLeft: 2,
      resultAttemptId: "att-phys-res",
      bundles: [bundleA],
      actions: ["START", "VIEW_RESULT", "HISTORY", "BUY_MORE"],
    });
    const standaloneExam = makeCard({
      entitlementId: "ent-standalone",
      code: "HSA-FULL-03",
      name: "Đề thi thử ĐGNL HSA số 3",
      examKind: { code: "HSA", nameVi: "Đánh giá năng lực HSA", nameEn: "HSA" },
      format: "FULL",
      scopeLabel: null,
      bundles: [],
      actions: ["START", "BUY_MORE"],
    });

    renderWithProviders(
      <MyExamsView
        session={STUDENT_SESSION}
        loadExams={async () => ({ items: [mathInBundle, physInBundle, standaloneExam] })}
      />,
    );

    const bundleHeading = await screen.findByRole("heading", { level: 3, name: "Bộ đề SP2E khối A00" });
    expect(bundleHeading).toHaveAttribute("data-exam-content");
    expect(bundleHeading).toHaveClass("wrap-break-word");
    expect(bundleHeading).toHaveAttribute("tabindex", "-1");
    const bundleSection = bundleHeading.closest("section")!;
    const bundleCode = within(bundleSection).getByText("BO-SP2E-A00");
    expect(bundleCode).toHaveAttribute("data-exam-content");
    expect(bundleCode).toHaveClass("wrap-anywhere");
    expect(within(bundleSection).getByText("2 bài")).toBeInTheDocument();
    expect(within(bundleSection).getByRole("link", { name: "Xem bộ đề" })).toHaveAttribute("href", "/exams/BO-SP2E-A00");

    // R1-35 / R1-36: phân cấp heading h2 (mục) -> h3 (nhóm bộ) -> h4 (thẻ bài thi trong bộ), bài lẻ giữ h3 dưới h2
    expect(screen.getByRole("heading", { level: 2, name: "Bài đã mua" })).toBeInTheDocument();
    expect(within(bundleSection).getByRole("heading", { level: 4, name: "SP2E Toán – Đề luyện số 1" })).toBeInTheDocument();
    expect(within(bundleSection).getByRole("heading", { level: 4, name: "SP2E Vật lí – Đề luyện số 1" })).toBeInTheDocument();
    expect(within(bundleSection).queryByRole("heading", { level: 3, name: "SP2E Toán – Đề luyện số 1" })).toBeNull();

    // Thẻ 1 (Toán): Tiếp tục, Xem kết quả, Lịch sử độc lập
    expect(within(bundleSection).getByRole("link", { name: "Tiếp tục" })).toHaveAttribute("href", "/attempts/att-math-1");
    // Thẻ 2 (Lý): Bắt đầu, Xem kết quả, Lịch sử độc lập
    expect(within(bundleSection).getByRole("link", { name: "Bắt đầu" })).toHaveAttribute("href", "/prepare/ent-phys");
    expect(within(bundleSection).getAllByRole("link", { name: "Xem kết quả" })).toHaveLength(2);
    expect(within(bundleSection).getAllByRole("link", { name: "Lịch sử" })).toHaveLength(2);

    // Bài mua lẻ nằm ngoài nhóm bộ, dùng SectionTitle có thanh dọc xanh 4px
    expect(within(bundleSection).queryByRole("heading", { name: "Đề thi thử ĐGNL HSA số 3" })).toBeNull();
    const standaloneHeading = screen.getByRole("heading", { level: 2, name: "Bài mua lẻ" });
    expect(standaloneHeading).toBeInTheDocument();
    expect(standaloneHeading.parentElement?.querySelector(".bg-primary-600")).not.toBeNull();
    expect(within(standaloneHeading.parentElement!).getByText("1 bài")).toBeInTheDocument();
    expect(screen.getByRole("heading", { level: 3, name: "Đề thi thử ĐGNL HSA số 3" })).toBeInTheDocument();
  });

  it("bài nằm trong 2 bộ: thẻ đầy đủ chỉ dựng 1 lần ở bộ đầu tiên (mới nhất), bộ thứ 2 ghi dòng dẫn chiếu", async () => {
    const newestBundle = { bundleId: "bnd-new", code: "BO-SP2E-NEW", name: "Bộ đề SP2E Mới" };
    const olderBundle = { bundleId: "bnd-old", code: "BO-SP2E-OLD", name: "Bộ đề SP2E Cũ" };

    const sharedExam = makeCard({
      entitlementId: "ent-shared",
      code: "SP2E-TOAN-01",
      name: "SP2E Toán – Đề luyện số 1",
      bundles: [newestBundle, olderBundle],
      actions: ["START"],
    });
    const olderOnlyExam = makeCard({
      entitlementId: "ent-old-only",
      code: "SP2E-HOA-01",
      name: "SP2E Hóa học – Đề luyện số 1",
      scopeLabel: { nameVi: "Hóa học", nameEn: "Chemistry" },
      bundles: [olderBundle],
      actions: ["START"],
    });

    const grouped = groupMyExams([sharedExam, olderOnlyExam]);
    expect(grouped.bundleGroups).toHaveLength(2);
    expect(grouped.bundleGroups[0]!.cards.map((c) => c.code)).toEqual(["SP2E-TOAN-01"]);
    expect(grouped.bundleGroups[1]!.cards.map((c) => c.code)).toEqual(["SP2E-HOA-01"]);
    expect(grouped.bundleGroups[1]!.crossReferences).toEqual([
      { examCode: "SP2E-TOAN-01", examName: "SP2E Toán – Đề luyện số 1", primaryBundle: newestBundle },
    ]);

    renderWithProviders(
      <MyExamsView session={STUDENT_SESSION} loadExams={async () => ({ items: [sharedExam, olderOnlyExam] })} />,
    );

    expect(await screen.findAllByRole("heading", { name: "SP2E Toán – Đề luyện số 1" })).toHaveLength(1);

    const oldGroup = screen.getByRole("heading", { level: 3, name: "Bộ đề SP2E Cũ" }).closest("section")!;
    expect(within(oldGroup).getByText("2 bài")).toBeInTheDocument();
    expect(
      within(oldGroup).getByText(
        (_content, node) =>
          node?.tagName === "LI" &&
          node.textContent === "Bài SP2E Toán – Đề luyện số 1 đang hiện ở nhóm Bộ đề SP2E Mới",
      ),
    ).toBeInTheDocument();
    expect(within(oldGroup).getByText("SP2E Toán – Đề luyện số 1")).toHaveAttribute("data-exam-content");
    const crossRefLink = within(oldGroup).getByRole("link", { name: "Bộ đề SP2E Mới" });
    expect(crossRefLink).toHaveAttribute("href", "#bundle-group-bnd-new");
    expect(crossRefLink).toHaveAttribute("data-exam-content");
    expect(crossRefLink).toHaveClass("focus-visible:ring-primary-text", "pointer-coarse:after:-inset-x-2");
    expect(screen.getByRole("heading", { level: 3, name: "Bộ đề SP2E Mới" })).toHaveAttribute(
      "id",
      "bundle-group-bnd-new",
    );
  });

  it("giao diện tiếng Anh không lẫn chữ giao diện tiếng Việt ngoài data-exam-content", async () => {
    const newestBundle = { bundleId: "bnd-new", code: "BO-SP2E-NEW", name: "Bộ đề SP2E Mới" };
    const olderBundle = { bundleId: "bnd-old", code: "BO-SP2E-OLD", name: "Bộ đề SP2E Cũ" };
    const sharedExam = makeCard({ bundles: [newestBundle, olderBundle] });
    const standalone = makeCard({ entitlementId: "ent-s", code: "HSA-1", name: "Đề lẻ HSA", bundles: [] });

    const { container } = renderWithProviders(
      <MyExamsView session={STUDENT_SESSION} loadExams={async () => ({ items: [sharedExam, standalone] })} />,
      { locale: "en", messages: enMessages },
    );

    expect(await screen.findByText("Individual exams")).toBeInTheDocument();
    expect(screen.getAllByRole("link", { name: "View bundle" })).toHaveLength(2);
    expect(screen.getByText(/is shown under/)).toBeInTheDocument();

    const clone = container.cloneNode(true) as HTMLElement;
    clone.querySelectorAll("[data-exam-content]").forEach((node) => node.remove());
    expect(clone.textContent).not.toMatch(/[ăâđêôơưàáạảãèéẹẻẽìíịỉĩòóọỏõùúụủũỳýỵỷỹ]/i);
  });
});

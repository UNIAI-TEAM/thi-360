import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { createExam } from "../api";
import { ExamListView } from "../components/exam-list-view";
import { ExamsBackend } from "./fake-exams-backend";
import { renderExamsUi } from "./render-exams-ui";

// Danh mục là dữ liệu của lane Nội dung; ở nhánh thật chưa có client, dùng danh mục mẫu.
vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture: getContentReference } = await import("@/test/fixtures/content-reference");
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

beforeEach(async () => {
  routerMock.push.mockReset();
  new ExamsBackend().install();
  window.history.replaceState(null, "", "/exams");
  await createExam({
    name: "BCA thu",
    code: "BCA-F-901",
    kind: "BCA",
    scope: "FULL",
    description: null,
  });
  await createExam({
    name: "THPTQG Tin hoc thu",
    code: "THPTQG-F-TIN-901",
    kind: "THPTQG",
    scope: "SUBJECT",
    subjectCode: "INFORMATICS",
    description: null,
  });
});

function rowOf(table: HTMLElement, name: string) {
  const row = within(table).getByRole("link", { name }).closest("tr");
  if (!row) throw new Error(`khong thay dong ${name}`);
  return within(row);
}

describe("danh sách bài thi: bảy kỳ thi", () => {
  it("cột Lựa chọn, thời lượng chung, huy hiệu Có tự luận", async () => {
    renderExamsUi(<ExamListView />);
    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    expect(within(table).getByRole("columnheader", { name: "Lựa chọn" })).toBeInTheDocument();
    await within(table).findByRole("link", { name: "BCA thu" });

    const bca = rowOf(table, "BCA thu");
    expect(bca.getByText("BCA")).toBeInTheDocument();
    expect(bca.getByText("4 tổ hợp")).toBeInTheDocument();
    // Danh sách của máy chủ mang cách tính thời gian: BCA dùng thời lượng chung.
    expect(bca.getByText("81 câu · 180 phút chung")).toBeInTheDocument();
    expect(bca.getByText("Có tự luận")).toBeInTheDocument();

    const informatics = rowOf(table, "THPTQG Tin hoc thu");
    // Danh sách của máy chủ mang timing và môn của bài: thời lượng chung.
    expect(informatics.getByText("28 câu · 50 phút chung")).toBeInTheDocument();

    const hsa = rowOf(table, "HSA FULL · Đề luyện số 1");
    expect(hsa.getByText("Tiếng Anh")).toBeInTheDocument();
    expect(hsa.queryByText("Có tự luận")).not.toBeInTheDocument();
  });

  it("lọc theo bảy kỳ thi của danh mục và theo môn", async () => {
    const user = userEvent.setup();
    renderExamsUi(<ExamListView />);
    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    await within(table).findByRole("link", { name: "BCA thu" });

    await user.click(screen.getByRole("combobox", { name: "Kỳ thi" }));
    const kindOptions = await screen.findAllByRole("option");
    expect(kindOptions.map((option) => option.textContent)).toEqual([
      "Tất cả",
      "HSA",
      "QDA",
      "BCA",
      "V-ACT",
      "SPT",
      "SP2E",
      "THPTQG",
    ]);
    await user.keyboard("{Escape}");

    await user.click(screen.getByRole("combobox", { name: "Môn" }));
    await user.click(await screen.findByRole("option", { name: "Tin học" }));
    await waitFor(() => expect(within(table).queryByRole("link", { name: "BCA thu" })).not.toBeInTheDocument());
    expect(within(table).getByRole("link", { name: "THPTQG Tin hoc thu" })).toBeInTheDocument();
    expect(within(table).queryByRole("link", { name: "HSA FULL · Đề luyện số 1" })).not.toBeInTheDocument();
  }, 20_000);
});

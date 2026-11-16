import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { createExam } from "../api";
import { ExamListView } from "../components/exam-list-view";
import { examStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

beforeEach(async () => {
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

afterEach(() => {
  examStore.reset();
  routerMock.push.mockReset();
});

function rowOf(table: HTMLElement, name: string) {
  const row = within(table).getByRole("link", { name }).closest("tr");
  if (!row) throw new Error(`khong thay dong ${name}`);
  return within(row);
}

describe("danh sách bài thi: bảy kỳ thi", () => {
  it("cột Lựa chọn, thời lượng chung, huy hiệu Có tự luận", async () => {
    renderWithProviders(<ExamListView />);
    const table = await screen.findByRole("table", { name: "Danh sách bài thi" });
    expect(within(table).getByRole("columnheader", { name: "Lựa chọn" })).toBeInTheDocument();
    await within(table).findByRole("link", { name: "BCA thu" });

    const bca = rowOf(table, "BCA thu");
    expect(bca.getByText("BCA")).toBeInTheDocument();
    expect(bca.getByText("4 tổ hợp")).toBeInTheDocument();
    expect(bca.getByText("81 câu · 180 phút chung")).toBeInTheDocument();
    expect(bca.getByText("Có tự luận")).toBeInTheDocument();

    const informatics = rowOf(table, "THPTQG Tin hoc thu");
    expect(informatics.getByText("Tin học · 2 nhánh")).toBeInTheDocument();
    expect(informatics.getByText("28 câu · 50 phút chung")).toBeInTheDocument();

    const hsa = rowOf(table, "HSA FULL · Đề luyện số 1");
    expect(hsa.getByText("Tiếng Anh + 3 tổ hợp")).toBeInTheDocument();
    expect(hsa.queryByText("Có tự luận")).not.toBeInTheDocument();
  });

  it("lọc theo bảy kỳ thi của danh mục và theo môn", async () => {
    const user = userEvent.setup();
    renderWithProviders(<ExamListView />);
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
  });
});

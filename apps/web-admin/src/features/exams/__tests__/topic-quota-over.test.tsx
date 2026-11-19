import { screen, within } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import { renderWithProviders } from "@/test/render";
import { TopicQuotaTable } from "../components/topic-quota-table";
import type { TopicQuota } from "../types";

vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));

const reference = referenceFixture as unknown as ContentReference;
const axes = { grades: ["GRADE_10"], difficulties: ["RECOGNITION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };
const quota = (id: string, count: number): TopicQuota => ({ id, name: `Chủ đề ${id}`, nodeIds: [], count, enforcement: "REQUIRED", basis: "QUESTION" });

function renderTable(quotas: TopicQuota[], total: number) {
  return renderWithProviders(
    <TopicQuotaTable id="s1" quotas={quotas} onChange={() => undefined} total={total} levelName="Toán học" subjectCode="MATH" reference={reference} axes={axes} labels={labels} />,
  );
}

describe("TopicQuotaTable: dòng tổng khi vượt (UNI-1702 C2)", () => {
  it("quá số câu: dòng Tổng màu lỗi kèm chữ 'Vượt N câu'", () => {
    renderTable([quota("a", 8), quota("b", 6)], 10);
    const totalRow = screen.getByRole("rowheader", { name: "Tổng" }).closest("tr") as HTMLElement;
    expect(within(totalRow).getByText("Vượt 4 câu")).toBeInTheDocument();
    expect(totalRow.querySelector("td")).toHaveClass("text-danger");
  });

  it("không vượt: dòng Tổng không có chữ 'Vượt'", () => {
    renderTable([quota("a", 4), quota("b", 3)], 10);
    const totalRow = screen.getByRole("rowheader", { name: "Tổng" }).closest("tr") as HTMLElement;
    expect(within(totalRow).queryByText(/Vượt/)).not.toBeInTheDocument();
    expect(totalRow.querySelector("td")).not.toHaveClass("text-danger");
  });
});

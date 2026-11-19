import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import { renderWithProviders } from "@/test/render";
import { TopicQuotaTable } from "../components/topic-quota-table";
import type { TopicQuota } from "../types";

const reference = referenceFixture as unknown as ContentReference;
const axes = { grades: ["GRADE_10"], difficulties: ["RECOGNITION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };
const quota: TopicQuota = { id: "q1", name: "Xác suất", nodeIds: [], count: 3, enforcement: "REQUIRED", basis: "QUESTION" };

function table(entry?: "COUNT_ONLY") {
  return (
    <TopicQuotaTable id="s1" quotas={[quota]} onChange={() => undefined} total={30} levelName="Toán học" subjectCode="MATH" reference={reference} axes={axes} labels={labels} entry={entry} />
  );
}

describe("TopicQuotaTable: cách nhập (R-9, AD-89 D2)", () => {
  it("mặc định có nút Số câu / Tỷ lệ (MD, MT)", () => {
    renderWithProviders(table());
    expect(screen.getByRole("radio", { name: "Tỷ lệ" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Số câu" })).toBeInTheDocument();
  });

  it("COUNT_ONLY ẩn nút chuyển, vẫn nhập số câu", () => {
    renderWithProviders(table("COUNT_ONLY"));
    expect(screen.queryByRole("radio", { name: "Tỷ lệ" })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Số câu" })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Số câu ít nhất · Xác suất/ })).toBeInTheDocument();
  });
});

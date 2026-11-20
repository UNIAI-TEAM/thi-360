import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import referenceFixture from "@/test/fixtures/content-reference.json";
import type { ContentReference } from "@/features/content/types";
import { renderWithProviders } from "@/test/render";
import { TopicQuotaTable } from "../components/topic-quota-table";
import type { TopicQuota } from "../types";

const reference = referenceFixture as unknown as ContentReference;
const axes = { grades: ["GRADE_10"], difficulties: ["RECOGNITION"] };
const labels = { grade: (code: string) => code, difficulty: (code: string) => code };
const quota: TopicQuota = { id: "q1", name: "Xác suất", nodeIds: [], count: 3, enforcement: "REQUIRED", basis: "QUESTION" };

function table(entry?: "COUNT_ONLY", hideGrade?: boolean) {
  return (
    <TopicQuotaTable id="s1" quotas={[quota]} onChange={() => undefined} total={30} levelName="Toán học" subjectCode="MATH" reference={reference} axes={axes} labels={labels} entry={entry} hideGrade={hideGrade} />
  );
}

describe("TopicQuotaTable: cách nhập (R-9, AD-89 D2)", () => {
  it("mặc định có nút Số câu / Tỷ lệ (MD, MT)", () => {
    renderWithProviders(table());
    expect(screen.getByRole("radio", { name: "Tỷ lệ" })).toBeInTheDocument();
    expect(screen.getByRole("radio", { name: "Số câu" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Giới hạn lớp" })).toBeInTheDocument();
    expect(screen.getByText("Mọi lớp")).toBeInTheDocument();
    const colSpans = Array.from(document.querySelectorAll("td[colspan='4']"));
    expect(colSpans).toHaveLength(2);
  });

  it("COUNT_ONLY ẩn nút chuyển, vẫn nhập số câu", () => {
    renderWithProviders(table("COUNT_ONLY"));
    expect(screen.queryByRole("radio", { name: "Tỷ lệ" })).not.toBeInTheDocument();
    expect(screen.queryByRole("radio", { name: "Số câu" })).not.toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Số câu ít nhất · Xác suất/ })).toBeInTheDocument();
  });

  it("hideGrade ẩn cột Giới hạn lớp, giữ colSpan 3 và giữ nguyên gradeCodes đã có (R1-4, AD-118)", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const withGrade: TopicQuota = { ...quota, gradeCodes: ["GRADE_10"] };
    renderWithProviders(
      <TopicQuotaTable id="s1" quotas={[withGrade]} onChange={onChange} total={30} levelName="Lịch sử" subjectCode="SU" reference={reference} axes={axes} labels={labels} hideGrade />,
    );
    expect(screen.queryByRole("columnheader", { name: "Giới hạn lớp" })).not.toBeInTheDocument();
    expect(screen.queryByText("Mọi lớp")).not.toBeInTheDocument();
    expect(document.querySelectorAll("td[colspan='3']")).toHaveLength(2);
    expect(document.querySelectorAll("td[colspan='4']")).toHaveLength(0);

    await user.type(screen.getByRole("textbox", { name: "Tên chủ đề của chỉ tiêu 1" }), " mới");
    expect(onChange).toHaveBeenCalled();
    expect(onChange.mock.calls.at(-1)![0][0].gradeCodes).toEqual(["GRADE_10"]);
  });
});

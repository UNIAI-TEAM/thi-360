import { describe, expect, it } from "vitest";
import { pointsRows, quotaScope, type PointsRow } from "../exam-overview";
import { sum } from "../matrix";
import type { ExamDetail, ExamPart } from "../types";
import type { ExamSection } from "../types";
import { QDA_COMBINATIONS, QDA_PARTS } from "./coded-fixtures";
import { EXAMS_FIXTURE } from "./exam-fixture";

function sample(id: string): ExamDetail {
  const exam = EXAMS_FIXTURE.exams.find((item) => item.id === id);
  if (!exam) throw new Error(`missing ${id}`);
  return structuredClone(exam);
}

const subjects = (rows: readonly PointsRow[]) => rows.map((row) => [row.subjectCode, row.count]);

describe("Tổng quan bài tính một đường hợp lệ của mỗi phần (R1-04..R1-07)", () => {
  it("R1-04: QDA phần 3 theo mã tính các môn của MỘT mã (2 môn × 25), tổng 150 câu và 150 điểm", () => {
    const rows = pointsRows(QDA_PARTS, QDA_COMBINATIONS);
    expect(subjects(rows)).toEqual([
      ["MATH", 50],
      ["LITERATURE", 50],
      ["PHYSICS", 25],
      ["CHEMISTRY", 25],
    ]);
    expect(sum(rows.map((row) => row.count))).toBe(150);
    expect(sum(rows.map((row) => row.totalPoints))).toBe(150);
    expect(quotaScope(QDA_PARTS, {}, QDA_COMBINATIONS).count).toBe(150);
  });

  it("R1-04: chưa có mã nào thì lấy môn đầu tiên cho đủ số câu của phần (không đếm cả bảy môn)", () => {
    expect(subjects(pointsRows(QDA_PARTS, [])).slice(2)).toEqual([
      ["PHYSICS", 25],
      ["CHEMISTRY", 25],
    ]);
  });

  it("R1-05: HSA Khoa học hoặc Tiếng Anh vẫn có hàng điểm và mẫu số của chỉ tiêu Tiếng Anh là 50, không phải 0", () => {
    const { parts } = sample("ex-hsa-f-001");
    const rows = pointsRows(parts);
    expect(subjects(rows)).toEqual([
      ["MATH", 50],
      ["LITERATURE", 50],
      ["ENGLISH", 50],
    ]);
    expect(sum(rows.map((row) => row.totalPoints))).toBe(150);
    expect(quotaScope(parts, { subjectCodes: ["ENGLISH"] })).toEqual({ count: 50, subjectCodes: ["ENGLISH"] });
  });

  it("R1-05: Khoa học đứng đầu: chỉ ba môn được chọn theo countsByPick 16/17/17, nhóm phần còn lại nhận phần chênh (R1-07)", () => {
    const { parts } = sample("ex-hsa-f-001");
    const part = parts[2]!;
    const science: ExamPart = { ...part, options: [...part.options].reverse() };
    const rows = pointsRows([science]);
    expect(subjects(rows)).toEqual([
      ["PHYSICS", 16],
      ["CHEMISTRY", 17],
      ["BIOLOGY", 17],
    ]);
    expect(sum(rows.map((row) => row.totalPoints))).toBe(50);
    // Chỉ tiêu của riêng môn Tiếng Anh vẫn tính trên đường Tiếng Anh, không bị ba môn Khoa học che mất.
    expect(quotaScope([science], { subjectCodes: ["ENGLISH"] }).count).toBe(50);
    expect(quotaScope([science], { subjectCodes: ["PHYSICS"] })).toEqual({ count: 16, subjectCodes: ["PHYSICS"] });
  });

  it("R1-06: THPTQG Tin học tính môn chung cộng MỘT nhánh, không cộng hai nhánh loại trừ nhau", () => {
    const { parts } = sample("ex-thptqg-tin-001");
    const rows = pointsRows(parts);
    expect(sum(rows.map((row) => row.count))).toBe(24 + 2 + 2);
    expect(quotaScope(parts, {}).count).toBe(28);
  });

  it("R1-07: nhóm lấy phần còn lại của môn tính phần chênh, không tính 0 câu", () => {
    const { parts } = sample("ex-hsa-f-001");
    const part = parts[2]!;
    const sciencePath = part.options.find((option) => option.kind === "SUBJECT_CHOICE")!;
    const physics = sciencePath.sections[0]!;
    expect(physics.blocks.map((block) => block.questionCount)).toEqual([10, null]);
    const rows = pointsRows([{ ...part, options: [sciencePath] }]);
    expect(rows[0]).toMatchObject({ subjectCode: "PHYSICS", count: 16 });
  });
});

describe("Nhóm tạm bỏ qua không lấy phần số câu của môn (R2-06)", () => {
  it("nhóm Tự luận 10 câu bị bỏ qua đứng trước nhóm 30 câu của môn 30 câu: môn vẫn tính 30 câu, không phải 20", () => {
    const base = sample("ex-hsa-f-001").parts[0]!;
    const template = base.sections[0]!.blocks[0]!;
    const section: ExamSection = {
      ...base.sections[0]!,
      questionCount: 30,
      blocks: [
        { ...template, id: "essay", questionType: "ESSAY", questionCount: 10, omitted: true },
        { ...template, id: "choice", questionType: "SINGLE_CHOICE", questionCount: 30 },
      ],
    };
    const rows = pointsRows([{ ...base, mode: "FIXED", options: [], sections: [section] }]);
    expect(rows).toHaveLength(1);
    expect(rows[0]).toMatchObject({ count: 30, essayPoints: 0 });
  });
});

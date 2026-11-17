import type { components } from "@thi360/api-client";
import { describe, expect, it } from "vitest";
import { mapExamDetail, mapExamTemplate, mapPart, toApiParts } from "../api-mapping";

type Template = components["schemas"]["ExamTemplateResponse"];

const block = { id: null, name: "Trac nghiem", questionType: "SINGLE_CHOICE", questionCount: 15, requiredPointsPerQuestion: 1 };

function template(parts: unknown[]): Template {
  return {
    active: true,
    buildMode: "STANDARD",
    isDefault: true,
    code: "BCA_FULL",
    kindCode: "BCA",
    scope: "FULL",
    timing: "SHARED",
    durationMinutes: 180,
    structure: { parts } as unknown as Template["structure"],
  };
}

describe("mapExamTemplate", () => {
  it("lấy số câu và khối của tổ hợp từ countsByPick và blocks của lựa chọn", () => {
    const mapped = mapExamTemplate(
      template([
        {
          name: "Phan 3",
          questionCount: 15,
          participation: { mode: "CHOICE", choose: 1 },
          sections: [],
          options: [{ kind: "COMBINATION", code: "CA1", countsByPick: [15], sections: [], blocks: [block] }],
        },
      ]),
    );
    const part = mapped.parts[0]!;
    expect(part.mode).toBe("CHOICE");
    expect(part.options?.[0]).toMatchObject({ kind: "COMBINATION", code: "CA1", subjectCounts: [15] });
    expect(part.options?.[0]?.blocks?.[0]).toMatchObject({ questionCount: 15, matrix: null, answerTypeLocked: false });
  });

  it("phương án SUBJECT_CHOICE giữ môn, số môn chọn và countsByPick", () => {
    const mapped = mapExamTemplate(
      template([
        {
          name: "Phan 3",
          questionCount: 50,
          participation: { mode: "CHOICE", choose: 1 },
          sections: [],
          options: [
            {
              kind: "SUBJECT_CHOICE",
              code: "SCIENCE",
              chooseSubjects: 3,
              countsByPick: [17, 17, 16],
              sections: [{ subjectCode: "LY", questionCount: null, choiceGroup: "NATURAL", blocks: [] }],
            },
          ],
        },
      ]),
    );
    const option = mapped.parts[0]!.options![0]!;
    expect(option.subjectChoice).toMatchObject({ chooseSubjects: 3, countsByPick: [17, 17, 16] });
    expect(option.subjectChoice?.sections[0]).toMatchObject({ subjectCode: "LY", questionCount: null, choiceGroup: "NATURAL" });
  });
});

describe("mapExamDetail thời lượng (F-4)", () => {
  const detail = (timing: string, durationMinutes: number) =>
    ({
      id: "ex-1",
      code: "E1",
      name: "E1",
      kind: "HSA",
      scope: "FULL",
      status: "DRAFT",
      revision: 1,
      hasUnpublishedChanges: false,
      questionCount: 2,
      hasEnglishOption: false,
      combinationCount: 0,
      timing,
      durationMinutes,
      check: { status: "UNCHECKED", issueCount: 0 },
      versions: [],
      parts: [
        { id: "p1", name: "P1", questionCount: 1, durationMinutes: 100, participation: { mode: "ALL" }, sections: [] },
        { id: "p2", name: "P2", questionCount: 1, durationMinutes: 100, participation: { mode: "ALL" }, sections: [] },
      ],
    }) as unknown as Parameters<typeof mapExamDetail>[0];

  it("thời gian theo phần: tổng các phần, bỏ số đã lưu của mẫu", () => {
    expect(mapExamDetail(detail("PER_PART", 195)).durationMinutes).toBe(200);
  });

  it("thời lượng chung: giữ số của bài", () => {
    expect(mapExamDetail(detail("SHARED", 180)).durationMinutes).toBe(180);
  });
});

describe("mapPart và toApiParts", () => {
  it("điền mặc định cho trường máy chủ bỏ và giữ expertConfirmed chỉ khi có", () => {
    const part = mapPart(
      {
        participation: { mode: "CHOICE", choose: 1 },
        options: [
          { kind: "BRANCH", code: "CA1", expertConfirmed: false },
          { kind: "BRANCH", code: "CA2" },
        ],
        sections: [{ subjectCode: "MATH", blocks: [{ name: "A" }] }],
      },
      0,
    );
    expect(part.mode).toBe("CHOICE");
    expect(part.options[0]!.expertConfirmed).toBe(false);
    expect("expertConfirmed" in part.options[1]!).toBe(false);
    expect(part.sections[0]!.blocks[0]).toMatchObject({ tagCodes: [], matrix: null, answerTypeLocked: false });
    expect(part.sections[0]!.matrix).toBeNull();
  });

  it("khi gửi bỏ null và mode của phần", () => {
    const part = mapPart({ name: "P", participation: { mode: "ALL" }, sections: [{ subjectCode: "MATH", knowledgeScope: { nodeIds: ["node"] }, blocks: [{ name: "A" }] }] }, 0);
    const [sent] = toApiParts([part]) as unknown as Array<Record<string, unknown>>;
    expect(sent).not.toHaveProperty("mode");
    expect(JSON.stringify(sent)).not.toContain("null");
    expect(sent!.participation).toEqual({ mode: "ALL" });
    expect(JSON.stringify(sent)).not.toContain('"match"');
    expect(JSON.stringify(sent)).not.toContain('"taskTypeCode"');
  });
});

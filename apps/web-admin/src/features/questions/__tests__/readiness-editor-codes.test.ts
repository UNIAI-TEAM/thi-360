import { describe, expect, it } from "vitest";
import en from "@/messages/en.json";
import vi from "@/messages/vi.json";
import { fromApiReadiness } from "../readiness-mapping";

const CODES = [
  "BLANK_CHILD_MISMATCH",
  "BLANK_OUTSIDE_SET_PASSAGE",
  "UNDERLINE_LABEL_DUPLICATE",
  "UNDERLINE_LABEL_MISPLACED",
  "IMAGE_ALT_MISSING",
] as const;

describe("năm mã sẵn sàng của trình soạn nội dung (spec UNI-2638 mục 6)", () => {
  it("bốn mã chặn thành điều kiện chưa đạt gắn câu con; IMAGE_ALT_MISSING chỉ là cảnh báo", () => {
    const result = fromApiReadiness({
      ready: false,
      errors: [
        { code: "BLANK_CHILD_MISMATCH", path: "body.passage" },
        { code: "BLANK_OUTSIDE_SET_PASSAGE", path: "body.children[1].content" },
        { code: "UNDERLINE_LABEL_DUPLICATE", path: "content" },
        { code: "UNDERLINE_LABEL_MISPLACED", path: "body.children[0].content" },
      ],
      warnings: [{ code: "IMAGE_ALT_MISSING", path: "body.children[1].solution" }],
    });
    expect(result.ready).toBe(false);
    const structure = result.rules.find((rule) => rule.rule === "STRUCTURE")!;
    expect(structure.passed).toBe(false);
    expect(structure.issues).toEqual([
      { code: "BLANK_CHILD_MISMATCH", field: "content" },
      { code: "BLANK_OUTSIDE_SET_PASSAGE", field: "content", child: 2 },
      { code: "UNDERLINE_LABEL_DUPLICATE", field: "content" },
      { code: "UNDERLINE_LABEL_MISPLACED", field: "content", child: 1 },
    ]);
    // Không mã nào rơi về READINESS_OTHER.
    expect(result.rules.flatMap((rule) => rule.issues).some((issue) => issue.code === "READINESS_OTHER")).toBe(false);
    expect(result.warnings).toEqual([{ code: "IMAGE_ALT_MISSING", field: "content", child: 2 }]);
  });

  it("mỗi mã có chữ tiếng Việt và tiếng Anh", () => {
    for (const messages of [vi, en]) {
      const issues = messages.questions.statusDialogs.issues as Record<string, string>;
      for (const code of CODES) expect(issues[code], code).toBeTruthy();
    }
  });
});

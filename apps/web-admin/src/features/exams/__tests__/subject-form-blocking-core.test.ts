import { afterEach, describe, expect, it } from "vitest";
import { formBlockingIssues, registerFormBlockingRuleForTest, type BlockingIssue } from "../subject-forms/blocking";
import { findFormTargetElement, focusFormTarget, formTarget } from "../subject-forms/navigation";
import { createRawInputStore } from "../subject-forms/raw-input-store";
import type { SubjectFormPins } from "../subject-forms/selection";
import { part, scienceSection, standardExam } from "./subject-form-builders";

/** Lõi của chặn lưu (`blocking.ts`), kho nhập thô và `navigation.ts`: hàm thuần, chưa qua `StructureEditor`. */
const restores: Array<() => void> = [];
afterEach(() => {
  while (restores.length) restores.pop()!();
});

const pins = (entries: Record<string, "MATRIX" | "ROWS" | "DEFAULT">): SubjectFormPins =>
  Object.fromEntries(Object.entries(entries).map(([id, form]) => [id, { form, notice: false }]));

describe("Kho nhập thô (FTM R-15)", () => {
  it("mỗi ô một mục; ghi đè, xóa theo ô, xóa hết; hasRawInputs và snapshot đổi tham chiếu khi đổi", () => {
    const store = createRawInputStore();
    const seen: number[] = [];
    const unsubscribe = store.subscribe(() => seen.push(store.getSnapshot().length));
    expect(store.hasRawInputs()).toBe(false);
    const before = store.getSnapshot();
    store.set({ sectionId: "s1", blockId: "b1", field: "count", text: "25%", code: "SET_NOT_WHOLE" });
    store.set({ sectionId: "s1", blockId: "b1", field: "count", text: "26%", code: "SET_NOT_WHOLE" });
    store.set({ sectionId: "s2", blockId: null, field: "structure", text: "x", code: "PERCENT_TOTAL" });
    expect(store.hasRawInputs()).toBe(true);
    expect(store.list()).toHaveLength(2);
    expect(store.get("s1", "b1", "count")?.text).toBe("26%");
    expect(store.getSnapshot()).not.toBe(before);
    store.clear("s1", "b1", "count");
    expect(store.get("s1", "b1", "count")).toBeNull();
    expect(store.list()).toHaveLength(1);
    store.clearAll();
    expect(store.hasRawInputs()).toBe(false);
    unsubscribe();
    store.set({ sectionId: "s3", blockId: null, field: "x", text: "", code: "c" });
    expect(seen).toEqual([1, 1, 2, 1, 0]);
  });

  it("xóa ô không có mục hoặc clearAll khi rỗng không báo thay đổi", () => {
    const store = createRawInputStore();
    let calls = 0;
    store.subscribe(() => (calls += 1));
    store.clear("s", null, "f");
    store.clearAll();
    expect(calls).toBe(0);
  });
});

describe("Lỗi chặn lưu (blocking.ts)", () => {
  const rule = (code: string, sectionId: string) => (target: { id: string }): BlockingIssue[] =>
    target.id === sectionId ? [{ code, sectionId, blockId: null, field: "structure" }] : [];

  it("chỉ xét môn ghim MT/DS trên bản nháp của mọi môn, không xét môn MD", () => {
    const parts = [part("p", [scienceSection("md"), scienceSection("mt"), scienceSection("ds")])];
    restores.push(registerFormBlockingRuleForTest((target) => rule("BAD", "md")(target)));
    restores.push(registerFormBlockingRuleForTest((target) => rule("BAD", "mt")(target)));
    restores.push(registerFormBlockingRuleForTest((target) => rule("BAD", "ds")(target)));
    const issues = formBlockingIssues({ exam: standardExam("QDA"), draft: parts, pinnedForms: pins({ md: "DEFAULT", mt: "MATRIX", ds: "ROWS" }), rawInputs: [] });
    expect(issues.map((issue) => issue.sectionId)).toEqual(["mt", "ds"]);
  });

  it("nhập thô của môn MT/DS chặn lưu; mục của môn MD, môn không còn trong bản nháp không tính", () => {
    const parts = [part("p", [scienceSection("a"), scienceSection("md")])];
    const rawInputs = [
      { sectionId: "a", blockId: "a-set", field: "count", text: "25%", code: "SET_NOT_WHOLE" },
      { sectionId: "md", blockId: null, field: "x", text: "1", code: "X" },
      { sectionId: "gone", blockId: null, field: "x", text: "1", code: "X" },
    ];
    const issues = formBlockingIssues({ exam: standardExam("QDA"), draft: parts, pinnedForms: pins({ a: "ROWS", md: "DEFAULT", gone: "ROWS" }), rawInputs });
    expect(issues).toEqual([{ code: "SET_NOT_WHOLE", sectionId: "a", blockId: "a-set", field: "count" }]);
  });

  it("thứ tự theo bản nháp: lỗi luật rồi nhập thô của từng môn; mục luật chỉ chạy khi gọi, rule thử được gỡ", () => {
    const parts = [part("p", [scienceSection("a"), scienceSection("b")])];
    const unregister = registerFormBlockingRuleForTest((target) => rule("R", "b")(target));
    const rawInputs = [{ sectionId: "a", blockId: null, field: "f", text: "", code: "RAW" }];
    const issues = formBlockingIssues({ exam: standardExam("QDA"), draft: parts, pinnedForms: pins({ a: "MATRIX", b: "MATRIX" }), rawInputs });
    expect(issues.map((issue) => issue.code)).toEqual(["RAW", "R"]);
    unregister();
    expect(formBlockingIssues({ exam: standardExam("QDA"), draft: parts, pinnedForms: pins({ a: "MATRIX", b: "MATRIX" }), rawInputs: [] })).toEqual([]);
  });
});

describe("Điều hướng theo biểu mẫu (navigation.ts)", () => {
  const sel = (form: "MATRIX" | "ROWS" | "DEFAULT" | "REVIEW") => ({ form, notice: false });

  it("MT: ô của môn (mặc định Cấu trúc câu); DS: dòng; MD, ON: đích cũ; không có nhóm: chỉ mở môn", () => {
    expect(formTarget(sel("MATRIX"), { sectionId: "s", blockId: "b" })).toEqual({ kind: "MATRIX_FIELD", field: { sectionId: "s", name: "STRUCTURE" } });
    expect(formTarget(sel("MATRIX"), { sectionId: "s", field: "SET_SOURCE" })).toEqual({ kind: "MATRIX_FIELD", field: { sectionId: "s", name: "SET_SOURCE" } });
    expect(formTarget(sel("ROWS"), { sectionId: "s", blockId: "b" })).toEqual({ kind: "ROW", blockId: "b" });
    expect(formTarget(sel("ROWS"), { sectionId: "s" })).toBeNull();
    expect(formTarget(sel("DEFAULT"), { sectionId: "s", blockId: "b" })).toEqual({ kind: "LEGACY_BLOCK", blockId: "b" });
    expect(formTarget(sel("REVIEW"), { sectionId: "s", blockId: "b" })).toEqual({ kind: "LEGACY_BLOCK", blockId: "b" });
    expect(formTarget(sel("DEFAULT"), { sectionId: "s" })).toBeNull();
  });

  it("đặt focus vào ô của MT và dòng của DS; ô không nhận focus thì vào điều khiển bên trong; thiếu ô thì không làm gì", () => {
    document.body.innerHTML = `<div id="root"><div data-form-field="STRUCTURE"><input id="t" /></div><section data-form-row="b1"><button id="e">Sua</button></section><p data-form-field="LABEL" id="p">x</p></div>`;
    const root = document.getElementById("root")!;
    expect(focusFormTarget(root, { kind: "MATRIX_FIELD", field: { sectionId: "s", name: "STRUCTURE" } })).toBe(true);
    expect(document.activeElement?.id).toBe("t");
    expect(focusFormTarget(root, { kind: "ROW", blockId: "b1" })).toBe(true);
    expect(document.activeElement?.id).toBe("e");
    expect(focusFormTarget(root, { kind: "MATRIX_FIELD", field: { sectionId: "s", name: "LABEL" } })).toBe(true);
    expect(document.activeElement?.id).toBe("p");
    expect(focusFormTarget(root, { kind: "ROW", blockId: "missing" })).toBe(false);
    expect(findFormTargetElement(root, { kind: "LEGACY_BLOCK", blockId: "b1" })).toBeNull();
    document.body.innerHTML = "";
  });
});

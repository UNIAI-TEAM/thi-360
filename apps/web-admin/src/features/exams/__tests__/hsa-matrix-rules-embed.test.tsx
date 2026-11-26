import { screen, waitFor, within } from "@testing-library/react";
import userEvent, { type UserEvent } from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import type { ContentReference } from "@/features/content/types";
import {
  boot,
  openStructure,
  putCalls,
  sectionsOf,
  wireBlock,
  wirePart,
  wireSection,
  type Wire,
} from "./subject-form-harness";
import { structureInput } from "./subject-matrix-harness";

const { asToan } = vi.hoisted(() => ({
  asToan: (ref: unknown) => JSON.parse(JSON.stringify(ref).replaceAll('"MATH"', '"TOAN"')) as ContentReference,
}));

vi.mock(import("@/features/content/api"), async (importOriginal) => {
  const actual = await importOriginal();
  const { getContentReferenceFixture } = await import("@/test/fixtures/content-reference");
  const getContentReference = async (locale?: Parameters<typeof getContentReferenceFixture>[0]) =>
    asToan(await getContentReferenceFixture(locale));
  return { ...actual, getContentReference, defaultContentApiDeps: { getContentReference } };
});

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

afterEach(() => routerMock.push.mockReset());

function hsaMathSection(over: Wire = {}, singleOver: Wire = {}): Wire {
  return wireSection(
    "sec-toan",
    "TOAN",
    [
      wireBlock("b-single", { name: "Trắc nghiệm độc lập", questionType: "SINGLE_CHOICE", questionCount: 32, templateGroupCode: "HSA_MATH_SINGLE", ...singleOver }),
      wireBlock("b-short", { name: "Điền đáp án", questionType: "SHORT_ANSWER", questionCount: 15, templateGroupCode: "HSA_MATH_SINGLE" }),
      wireBlock("b-set", { name: "Chùm cuối", questionType: "QUESTION_SET", setCount: 1, setSize: 3, questionCount: 3, templateGroupCode: "HSA_MATH_CONTEXT" }),
    ],
    over,
  );
}

async function addMathRule(user: UserEvent, { type, scope, exactCount }: { type: "COUNT" | "SOURCE"; scope: RegExp; exactCount?: number }) {
  await user.click(screen.getByTestId("mapid-rule-add"));
  const dialog = await screen.findByRole("dialog", { name: "Thêm quy tắc" });
  const scopeBox = within(dialog).getByTestId("mapid-rule-scope");
  await user.click(within(scopeBox).getByRole("combobox"));
  await user.click(await screen.findByRole("option", { name: scope }));
  await user.click(within(dialog).getByTestId(`mapid-rule-type-${type}`));

  await user.click(within(dialog).getByTestId("mapid-rule-mapid-picker"));
  const picker = await screen.findByRole("dialog", { name: "Chọn kiến thức (MAPID)" });
  await user.type(within(picker).getByRole("searchbox", { name: "Tìm kiến thức" }), "0D1");
  await user.click(await within(picker).findByRole("checkbox", { name: /0D1 Mệnh đề và tập hợp/ }));
  await user.click(within(picker).getByRole("button", { name: "Áp dụng (1)" }));

  if (exactCount !== undefined) {
    await user.click(within(within(dialog).getByTestId("mapid-rule-comparator")).getByRole("radio", { name: "Bằng" }));
    const count = within(dialog).getByTestId("mapid-rule-count");
    await user.clear(count);
    await user.type(count, String(exactCount));
  }
  await user.click(within(dialog).getByTestId("mapid-rule-save"));
}

describe("HSA MT nhúng nguyên vẹn HSA-D/HSA-G Quy tắc MAPID (CR-2H AC-6, AC-7, AC-8, AC-12, AC-13)", () => {
  it("embedsHsaDRules: hiện MapidModeField, MapidRulesTable và Phạm vi nguồn theo vai trò; ẩn KnowledgeScopeField, TopicQuotaTable và Nguồn chùm", async () => {
    const user = userEvent.setup();
    boot({ kind: "HSA", parts: [wirePart([hsaMathSection({ mapidMode: "ALL" })])] });
    openStructure("?section=sec-toan");
    expect(await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();

    expect(screen.getByTestId("mapid-mode-all")).toBeChecked();
    expect(screen.getByTestId("mapid-mode-matrix")).toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 3, name: "Phạm vi và chỉ tiêu chủ đề" })).not.toBeInTheDocument();
    expect(screen.queryByRole("heading", { level: 4, name: "Nguồn chùm" })).not.toBeInTheDocument();

    await user.click(screen.getByTestId("mapid-mode-matrix"));
    expect(await screen.findByTestId("mapid-rules-table")).toBeInTheDocument();
    await addMathRule(user, { type: "SOURCE", scope: /^Chùm cuối/ });
    const roleScope = screen.getByTestId("mapid-group-scope-b-set");
    expect(roleScope).toHaveTextContent(/Chỉ lấy từ Mệnh đề và tập hợp/);
    expect(screen.getByTestId("mapid-group-scope-b-single")).toHaveTextContent(/Không giới hạn MAPID/);
  });

  it("sameRulesAsDefaultForm: thao tác hệt nhau ở MT và MD sinh mapidMode và mapidRules giống hệt nhau trong thân PUT", async () => {
    const runActions = async (expectMatrix: boolean) => {
      const user = userEvent.setup();
      const { server } = boot({
        kind: "HSA",
        parts: [wirePart([hsaMathSection({}, expectMatrix ? {} : { tagCodes: ["MD"] })])],
      });
      const view = openStructure("?section=sec-toan");
      try {
        await screen.findByTestId("mapid-mode-matrix");
        if (expectMatrix) expect(screen.getByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeInTheDocument();
        else expect(screen.queryByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" })).toBeNull();
        await user.click(screen.getByTestId("mapid-mode-matrix"));
        await screen.findByTestId("mapid-rules-table");
        await addMathRule(user, { type: "COUNT", scope: /^Điền đáp án/, exactCount: 3 });
        await addMathRule(user, { type: "SOURCE", scope: /^Chùm cuối/ });
        await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
        await waitFor(() => expect(putCalls(server)).toHaveLength(1));
        const sec = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!;
        return { mapidMode: sec.mapidMode, mapidRules: sec.mapidRules };
      } finally {
        view.unmount();
      }
    };

    const mtResult = await runActions(true);
    const mdResult = await runActions(false);
    expect(mtResult.mapidMode).toBe(mdResult.mapidMode);
    expect(mtResult.mapidRules).toHaveLength(2);
    expect(mtResult.mapidRules).toEqual(
      (mdResult.mapidRules as Wire[]).map((rule, idx) => ({ ...rule, id: (mtResult.mapidRules as Wire[])[idx]!.id })),
    );
  }, 20_000);

  it("percentRulesFollowStructure & zeroKeepsRuleTarget: đổi Điền đáp án 15 -> 12 và 15 -> 0 -> 12 tính lại count của quy tắc % và giữ nguyên groupId", async () => {
    const user = userEvent.setup();
    const parts = [
      wirePart([
        hsaMathSection({
          mapidMode: "MATRIX",
          mapidRules: [{ id: "r-pct", type: "COUNT", groupId: "b-short", nodeIds: ["kn-0D1"], comparator: "AT_LEAST", count: 3, percent: 20 }],
        }),
      ]),
    ];
    const { server } = boot({ kind: "HSA", parts });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
    const fill = () => structureInput("Điền đáp án");

    await user.clear(fill());
    await user.type(fill(), "12");
    expect(screen.getByTestId("mapid-rule-row-r-pct")).toHaveTextContent(/2\/12/);

    await user.clear(fill());
    await user.type(fill(), "0");
    expect(screen.getByTestId("mapid-rule-row-r-pct")).toBeInTheDocument();

    await user.clear(fill());
    await user.type(fill(), "12");
    await user.click(screen.getByRole("button", { name: "Tiếp tục → Thông tin bán" }));
    await waitFor(() => expect(putCalls(server)).toHaveLength(1));
    const savedRules = sectionsOf(putCalls(server)[0]!.body)["sec-toan"]!.mapidRules as Wire[];
    expect(savedRules[0]).toMatchObject({ id: "r-pct", groupId: "b-short", percent: 20, count: 2 });
  });

  it("keyboardNavigationInEmbeddedRules: mở và đóng hộp thoại quy tắc trong MT bằng bàn phím, tiêu điểm trả lại đúng chỗ", async () => {
    const user = userEvent.setup();
    boot({ kind: "HSA", parts: [wirePart([hsaMathSection({ mapidMode: "MATRIX", mapidRules: [] })])] });
    openStructure("?section=sec-toan");
    await screen.findByRole("heading", { level: 3, name: "Cấu trúc thành phần câu hỏi" });
    const addBtn = await screen.findByTestId("mapid-rule-add");
    addBtn.focus();
    await user.keyboard("{Enter}");
    const dialog = await screen.findByRole("dialog", { name: "Thêm quy tắc" });
    await user.keyboard("{Escape}");
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(dialog).not.toBeInTheDocument();
    await waitFor(() => expect(addBtn).toHaveFocus());
  });
});

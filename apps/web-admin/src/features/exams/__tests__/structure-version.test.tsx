import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderWithProviders } from "@/test/render";
import { createExam } from "../api";
import { ExamStructureView } from "../components/exam-structure-view";
import { examStore } from "../mock/store";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({ useRouter: () => routerMock }));

afterEach(() => {
  examStore.reset();
  routerMock.push.mockReset();
});

function openStructure(examId: string, search = "") {
  window.history.replaceState(null, "", `/exams/${examId}/structure${search}`);
  return renderWithProviders(<ExamStructureView examId={examId} />);
}

describe("Cấu trúc: phiên bản năm", () => {
  it("SP2E Toán: chọn phiên bản 2025 hiện danh sách phần sẽ nạp lại, chỉ thay sau khi xác nhận", async () => {
    const exam = await createExam({ name: "SP2E thử", code: "SP2E-S-902", kind: "SP2E", scope: "SUBJECT", subjectCode: "MATH", description: null });
    expect(exam.templateCode).toBe("SP2E_MATH_2026");
    const user = userEvent.setup();
    openStructure(exam.id);
    const picker = await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ });
    expect(picker).toHaveTextContent("SP2E 2026 · Toán · 40 câu");

    await user.click(picker);
    await user.click(await screen.findByRole("option", { name: /SP2E 2025 · Toán · 22 câu/ }));
    const dialog = await screen.findByRole("dialog", { name: /Nạp lại cấu trúc theo SP2E 2025/ });
    expect(within(dialog).getByText("Các phần sẽ được nạp lại (1)")).toBeInTheDocument();
    expect(within(dialog).getByText("1. Toán")).toBeInTheDocument();
    expect(within(dialog).getByText("22 câu · chưa có thời lượng")).toBeInTheDocument();
    // Chưa xác nhận: cấu trúc chưa đổi.
    expect(examStore.get(exam.id)?.templateCode).toBe("SP2E_MATH_2026");

    await user.click(within(dialog).getByRole("button", { name: "Nạp lại cấu trúc" }));
    await waitFor(() => expect(examStore.get(exam.id)?.templateCode).toBe("SP2E_MATH_2025"));
    expect(examStore.get(exam.id)).toMatchObject({ totalQuestions: 22, structureYear: 2025 });
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
  }, 20_000);

  it("hủy hộp xác nhận thì giữ nguyên cấu trúc và bản nháp chưa lưu", async () => {
    const exam = await createExam({ name: "SP2E thử", code: "SP2E-S-903", kind: "SP2E", scope: "SUBJECT", subjectCode: "MATH", description: null });
    const user = userEvent.setup();
    openStructure(exam.id);
    await user.click(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ }));
    await user.click(await screen.findByRole("option", { name: /SP2E 2025/ }));
    const dialog = await screen.findByRole("dialog");
    await user.click(within(dialog).getByRole("button", { name: "Giữ cấu trúc hiện tại" }));
    await waitFor(() => expect(screen.queryByRole("dialog")).not.toBeInTheDocument());
    expect(examStore.get(exam.id)?.templateCode).toBe("SP2E_MATH_2026");
  }, 20_000);

  it("chỉ một phiên bản thì ô bị khóa; không có mẫu khớp (HSA Theo phần) thì không hiện ô", async () => {
    const { unmount } = openStructure("ex-qda-f-001");
    expect(await screen.findByRole("combobox", { name: /Phiên bản cấu trúc/ })).toBeDisabled();
    unmount();
    const part = await createExam({ name: "HSA Toán", code: "HSA-P-902", kind: "HSA", scope: "PART", partCode: "MATH", maxScore: 50, description: null });
    openStructure(part.id, `?part=${examStore.require(part.id).parts[0]!.id}`);
    await screen.findByRole("textbox", { name: /Số câu phải làm/ });
    expect(screen.queryByRole("combobox", { name: /Phiên bản cấu trúc/ })).not.toBeInTheDocument();
  });
});

describe("Cấu trúc: phần thi", () => {
  it("phần tự chọn có quy tắc tham gia; số câu trên đề không được ít hơn số câu phải làm", async () => {
    const user = userEvent.setup();
    openStructure("ex-qda-f-001", "?part=part-49");
    expect(await screen.findByRole("heading", { name: "Quy tắc tham gia" })).toBeInTheDocument();
    const choose = screen.getByRole("textbox", { name: "Số phương án phải chọn" });
    expect(choose).toHaveValue("1");
    await user.clear(choose);
    await user.type(choose, "9");
    expect((await screen.findAllByText(/số phương án phải chọn là 9/)).length).toBeGreaterThan(0);

    const paper = screen.getByRole("textbox", { name: /Số câu trên đề/ });
    const required = screen.getByRole("textbox", { name: /Số câu phải làm/ });
    expect(paper).toHaveValue("50");
    expect(required).toHaveValue("50");
    await user.clear(required);
    await user.type(required, "40");
    // Số câu trên đề đi theo khi hai số đang bằng nhau.
    expect(paper).toHaveValue("40");
    await user.clear(paper);
    await user.type(paper, "30");
    expect((await screen.findAllByText(/số câu trên đề \(30\) ít hơn số câu phải làm \(40\)/)).length).toBeGreaterThan(0);
  });

  it("điểm tối đa của phần để trống được; nhập thì lưu vào phần", async () => {
    const user = userEvent.setup();
    openStructure("ex-qda-f-001", "?part=part-42");
    const points = await screen.findByRole("textbox", { name: /Điểm tối đa của phần/ });
    expect(points).toHaveValue("");
    await user.type(points, "50");
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() => expect(examStore.get("ex-qda-f-001")?.parts.find((part) => part.id === "part-42")?.maxPoints).toBe(50));
  });

  it("Điểm mỗi câu yêu cầu của khối không bắt buộc: xóa ô thì lưu null", async () => {
    const user = userEvent.setup();
    const blockId = examStore.require("ex-hsa-f-001").parts[0]!.sections[0]!.blocks[0]!.id;
    openStructure("ex-hsa-f-001", `?block=${blockId}`);
    const points = await screen.findByRole("textbox", { name: /Điểm mỗi câu yêu cầu/ });
    await user.clear(points);
    expect(screen.getByText(/Không bắt buộc\. Để trống thì câu nào cũng vào nguồn/)).toBeInTheDocument();
    await user.click(screen.getByRole("button", { name: "Lưu" }));
    await waitFor(() =>
      expect(
        examStore.get("ex-hsa-f-001")?.parts[0]?.sections[0]?.blocks.find((block) => block.id === blockId)?.requiredPointsPerQuestion,
      ).toBeNull(),
    );
  });
});

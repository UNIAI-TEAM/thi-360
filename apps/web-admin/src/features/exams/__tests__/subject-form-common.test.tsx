import { fireEvent, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import en from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import { CountPercentInput } from "../subject-forms/count-percent-input";
import { EnforcementControl } from "../subject-forms/enforcement-control";
import { FullFormNotice } from "../subject-forms/full-form-notice";
import { LockReason, type LockReasonCode } from "../subject-forms/lock-reason";
import { SubjectFormHeader } from "../subject-forms/subject-form-header";

/**
 * Thành phần dùng chung của biểu mẫu theo môn (CR-1 AC-11): chữ đúng ở vi và en, khóa nào cũng có lý do bằng chữ, quy đổi tỷ lệ
 * theo R-9. Ngoài tệp này: `source-scan.test` (không chữ Việt trong mã), `messages.test` (đủ khóa vi/en), `focus-styles.test`.
 */
const english = { locale: "en", messages: en } as const;

describe("Dòng chữ MD (R-2)", () => {
  it("một dòng chữ ở cả hai ngôn ngữ, không nút", () => {
    const view = renderWithProviders(<FullFormNotice reason="LIMITS_VIOLATED" />);
    expect(screen.getByTestId("full-form-notice")).toHaveTextContent("Môn này đang dùng thiết lập đầy đủ vì có thiết lập mà biểu mẫu gọn không hiển thị được.");
    expect(screen.getByTestId("full-form-notice")).toHaveAttribute("data-reason", "LIMITS_VIOLATED");
    expect(screen.queryByRole("button")).toBeNull();
    view.unmount();
    renderWithProviders(<FullFormNotice />, english);
    expect(screen.getByTestId("full-form-notice")).toHaveTextContent("This subject uses the full setup because it has settings the compact form cannot show.");
  });
});

describe("Lý do khóa (R-15)", () => {
  const codes: LockReasonCode[] = ["answerType", "multiSubject", "omitted", "promptBasis", "rowCounts", "rowHasConditions", "rowNoSetSize", "rowOmitted", "templateTotal"];

  it("mỗi mã khóa có chữ ở vi và en, không phải khóa dịch thô", () => {
    for (const locale of [{}, english]) {
      const view = renderWithProviders(
        <>
          {codes.map((code) => (
            <LockReason key={code} code={code} />
          ))}
        </>,
        locale,
      );
      for (const code of codes) {
        const node = view.container.querySelector(`[data-lock-reason="${code}"]`);
        expect(node?.textContent?.trim().length).toBeGreaterThan(5);
        expect(node?.textContent).not.toContain("exams.subjectForm");
      }
      view.unmount();
    }
  });

  it("chữ lý do đúng theo đặc tả (R-7, R-8)", () => {
    renderWithProviders(<LockReason code="rowCounts" />);
    expect(screen.getByText("Theo mẫu đề chuẩn, không đổi số lượng")).toBeInTheDocument();
  });
});

describe("Mức áp dụng theo kỳ (R-8)", () => {
  const chip = (text: string) => screen.getByText(text);

  it("HSA: chip Bắt buộc không có nút; BCA: chip Tham khảo không có nút", () => {
    const view = renderWithProviders(<EnforcementControl mode="FIXED_REQUIRED" name="Toán" value="REQUIRED" />);
    expect(chip("Bắt buộc")).toBeInTheDocument();
    expect(screen.queryByRole("switch")).toBeNull();
    view.unmount();
    renderWithProviders(<EnforcementControl mode="FIXED_REFERENCE" name="Toán" value="REFERENCE" />);
    expect(chip("Tham khảo")).toBeInTheDocument();
    expect(screen.queryByRole("switch")).toBeNull();
  });

  it("QDA, THPTQG: chiều đã khai là Bắt buộc, chiều để trống ghi Chưa khai báo", () => {
    const view = renderWithProviders(<EnforcementControl mode="DECLARED_REQUIRED" name="Độ khó" value="REQUIRED" declared />);
    expect(chip("Bắt buộc")).toBeInTheDocument();
    view.unmount();
    renderWithProviders(<EnforcementControl mode="DECLARED_REQUIRED" name="Độ khó" value="REQUIRED" declared={false} />);
    expect(chip("Chưa khai báo")).toBeInTheDocument();
  });

  it("V-ACT, SP2E...: mặc định Chỉ đối chiếu, nút Áp dụng khi sinh đề lưu REQUIRED rồi REFERENCE", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const view = renderWithProviders(<EnforcementControl mode="REFERENCE_WITH_APPLY" name="Lớp" value="REFERENCE" onChange={onChange} />);
    expect(chip("Chỉ đối chiếu")).toBeInTheDocument();
    const toggle = screen.getByRole("switch", { name: "Áp dụng khi sinh đề" });
    expect(toggle).not.toBeChecked();
    await user.click(toggle);
    expect(onChange).toHaveBeenLastCalledWith("REQUIRED");
    view.rerender(<EnforcementControl mode="REFERENCE_WITH_APPLY" name="Lớp" value="REQUIRED" onChange={onChange} />);
    expect(chip("Bắt buộc")).toBeInTheDocument();
    await user.click(screen.getByRole("switch", { name: "Áp dụng khi sinh đề" }));
    expect(onChange).toHaveBeenLastCalledWith("REFERENCE");
  });

  it("phân bố theo lệnh hỏi hoặc chỉ tiêu nhiều môn: nút khóa kèm lý do bằng chữ gắn aria-describedby", async () => {
    const user = userEvent.setup();
    const onChange = vi.fn();
    const view = renderWithProviders(<EnforcementControl mode="REFERENCE_WITH_APPLY" name="Lệnh" value="REFERENCE" onChange={onChange} lockReason="promptBasis" />);
    const toggle = screen.getByRole("switch", { name: "Áp dụng khi sinh đề" });
    expect(toggle).toBeDisabled();
    expect(screen.getByText("Phân bố theo lệnh hỏi chỉ dùng để đối chiếu")).toBeInTheDocument();
    expect(toggle.getAttribute("aria-describedby")).toBe(view.container.querySelector("[data-lock-reason]")?.id);
    await user.click(toggle);
    expect(onChange).not.toHaveBeenCalled();
    view.unmount();
    renderWithProviders(<EnforcementControl mode="REFERENCE_WITH_APPLY" name="Lệnh" value="REFERENCE" lockReason="multiSubject" />);
    expect(screen.getByText("Chỉ tiêu nhiều môn chỉ dùng để đối chiếu")).toBeInTheDocument();
  });

  it("en: nhãn tiếng Anh, không lẫn tiếng Việt", () => {
    const view = renderWithProviders(<EnforcementControl mode="REFERENCE_WITH_APPLY" name="Grade" value="REFERENCE" lockReason="multiSubject" />, english);
    expect(view.container.textContent).toContain("For comparison only");
    expect(view.container.textContent).toContain("Apply when generating");
    expect(view.container.textContent).not.toMatch(/[ạảãàáâấầẩẫăắằđêếềểễôốồổỗơớờởỡưứừửữ]/i);
  });
});

describe("Ô số câu / tỷ lệ (R-9)", () => {
  const type = async (name: RegExp | string, text: string) => {
    const user = userEvent.setup();
    const box = screen.getByRole("textbox", { name });
    await user.clear(box);
    if (text) await user.type(box, text);
  };

  it("kỳ không nhập %: một ô số câu, % chỉ đọc bên cạnh", async () => {
    const onChange = vi.fn();
    renderWithProviders(<CountPercentInput label="Trắc nghiệm" entry="NONE" total={40} count={10} fieldName="structure" onChange={onChange} />);
    expect(screen.getByText("25%")).toBeInTheDocument();
    expect(screen.queryByRole("textbox", { name: /Tỷ lệ/ })).toBeNull();
    await type("Trắc nghiệm", "12");
    expect(onChange).toHaveBeenLastCalledWith({ count: 12, percent: null });
  });

  it("nhập 30% của 40 câu: hiện '30% của 40 câu = 12 câu' trước khi lưu, ghi 12 kèm tỷ lệ đã gõ", async () => {
    const onChange = vi.fn();
    renderWithProviders(<CountPercentInput label="Dòng 1" entry="COUNT_OR_PERCENT" total={40} count={0} fieldName="row" onChange={onChange} />);
    await type(/Tỷ lệ/, "30");
    expect(screen.getByText("30% của 40 câu = 12 câu")).toBeInTheDocument();
    expect(onChange).toHaveBeenLastCalledWith({ count: 12, percent: 30 });
    expect(screen.getByRole("textbox", { name: "Số câu" })).toHaveValue("12");
  });

  it("mẫu số của mục: 40% của 25 câu = 10 câu (không phải 40 câu của cả môn)", async () => {
    renderWithProviders(<CountPercentInput label="Dòng" entry="TWO_COLUMNS" total={25} count={0} fieldName="row" onChange={vi.fn()} />);
    await type(/Tỷ lệ/, "40");
    expect(screen.getByText("40% của 25 câu = 10 câu")).toBeInTheDocument();
  });

  it("chùm cỡ cố định: 25% của 40 ra 10 câu không chia hết 4 thì báo lỗi tại ô, không ghi bản nháp, báo nhập thô", async () => {
    const onChange = vi.fn();
    const onRawInput = vi.fn();
    renderWithProviders(<CountPercentInput label="Dòng chùm" entry="COUNT_OR_PERCENT" total={40} count={0} setSize={4} fieldName="row" onChange={onChange} onRawInput={onRawInput} />);
    await type(/Tỷ lệ/, "25");
    expect(screen.getByRole("alert")).toHaveTextContent("Tỷ lệ này ra 10 câu, không chia được thành chùm 4 câu");
    expect(onChange).not.toHaveBeenCalled();
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "25", code: "setNotWhole" });
    expect(screen.getByRole("textbox", { name: /Tỷ lệ/ })).toHaveAttribute("aria-invalid", "true");
    await type(/Tỷ lệ/, "30");
    expect(onChange).toHaveBeenLastCalledWith({ count: 12, percent: 30 });
    expect(onRawInput).toHaveBeenLastCalledWith(null);
    expect(screen.queryByRole("alert")).toBeNull();
  });

  it("chữ không phải số: lỗi tại ô, không ghi bản nháp", () => {
    const onChange = vi.fn();
    const onRawInput = vi.fn();
    renderWithProviders(<CountPercentInput label="Dòng" entry="NONE" total={40} count={5} fieldName="row" onChange={onChange} onRawInput={onRawInput} />);
    fireEvent.change(screen.getByRole("textbox", { name: "Dòng" }), { target: { value: "abc" } });
    expect(screen.getByRole("alert")).toHaveTextContent("Nhập một số không âm.");
    expect(onChange).not.toHaveBeenCalled();
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "abc", code: "invalidNumber" });
  });

  it("lỗi từ nơi gọi hiện tại ô với aria-describedby; ô gắn data-form-field cho điều hướng", () => {
    renderWithProviders(<CountPercentInput label="Số chùm" entry="NONE" total={17} count={3} fieldName="SET_COUNT" onChange={vi.fn()} error="Số chùm chỉ nhận 1 hoặc 2" />);
    const box = screen.getByRole("textbox", { name: "Số chùm" });
    expect(box).toHaveAttribute("aria-invalid", "true");
    expect(box.getAttribute("aria-describedby")).toContain(screen.getByRole("alert").id);
    expect(box.closest("[data-form-field]")).toHaveAttribute("data-form-field", "SET_COUNT");
  });

  it("en: nhãn và xem trước bằng tiếng Anh", async () => {
    renderWithProviders(<CountPercentInput label="Row" entry="COUNT_OR_PERCENT" total={40} count={0} fieldName="row" onChange={vi.fn()} />, english);
    await type(/Percent/, "30");
    expect(screen.getByText("30% of 40 questions = 12 questions")).toBeInTheDocument();
  });

  it("số câu đổi từ bên ngoài (đổi tổng) thay chữ đang hiện", () => {
    const view = renderWithProviders(<CountPercentInput label="Dòng" entry="NONE" total={40} count={5} fieldName="row" onChange={vi.fn()} />);
    view.rerender(<CountPercentInput label="Dòng" entry="NONE" total={40} count={9} fieldName="row" onChange={vi.fn()} />);
    expect(screen.getByRole("textbox", { name: "Dòng" })).toHaveValue("9");
  });

  it("xóa ô tỷ lệ: không gọi onChange, số câu giữ nguyên, nhưng nhập thô của ô được gỡ (R1-7)", async () => {
    const onChange = vi.fn();
    const onRawInput = vi.fn();
    renderWithProviders(<CountPercentInput label="Dòng" entry="COUNT_OR_PERCENT" total={40} count={12} fieldName="row" onChange={onChange} onRawInput={onRawInput} />);
    // Tạo một lỗi nhập thô rồi xóa trắng: lỗi phải được gỡ mà số câu không đổi.
    await type(/Tỷ lệ/, "abc");
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "abc", code: "invalidNumber" });
    onChange.mockClear();
    await type(/Tỷ lệ/, "");
    expect(onChange).not.toHaveBeenCalled();
    expect(onRawInput).toHaveBeenLastCalledWith(null);
    expect(screen.getByRole("textbox", { name: "Số câu" })).toHaveValue("12");
  });

  it("tỷ lệ ngoài 0–100: báo sai số, không ghi bản nháp (R1-9)", async () => {
    const onChange = vi.fn();
    const onRawInput = vi.fn();
    renderWithProviders(<CountPercentInput label="Dòng" entry="COUNT_OR_PERCENT" total={40} count={0} fieldName="row" onChange={onChange} onRawInput={onRawInput} />);
    // Gõ một lần (thay đổi nguyên cụm) để tránh các tiền tố hợp lệ như "15" gọi onChange trước.
    fireEvent.change(screen.getByRole("textbox", { name: /Tỷ lệ/ }), { target: { value: "150" } });
    expect(onChange).not.toHaveBeenCalled();
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "150", code: "invalidNumber" });
    fireEvent.change(screen.getByRole("textbox", { name: /Tỷ lệ/ }), { target: { value: "-5" } });
    expect(onChange).not.toHaveBeenCalled();
    expect(onRawInput).toHaveBeenLastCalledWith({ text: "-5", code: "invalidNumber" });
  });
});

describe("Đầu môn dùng chung (R-7, R-15)", () => {
  it("tên môn, đã phân bổ kèm thiếu bằng chữ, số câu chỉ đọc, đường dẫn Thông tin tổng quan", () => {
    renderWithProviders(<SubjectFormHeader subjectName="Vật lí" count={50} allocated={47} examId="e1" action={<button type="button">Kiểm tra môn</button>} />);
    expect(screen.getByRole("heading", { level: 2, name: /Vật lí/ })).toBeInTheDocument();
    expect(screen.getByText("Thiếu 3 câu")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: /Số câu/ })).toHaveAttribute("readonly");
    expect(screen.getByRole("link", { name: "Sửa thông tin tổng quan" })).toHaveAttribute("href", "/exams/new?exam=e1");
    expect(screen.getByRole("button", { name: "Kiểm tra môn" })).toBeInTheDocument();
  });

  it("vượt ghi Vượt n câu; đủ ghi Khớp; mẫu khóa số câu ghi lý do bằng chữ", () => {
    const view = renderWithProviders(<SubjectFormHeader subjectName="Hóa" count={50} allocated={53} examId="e1" />);
    expect(screen.getByText("Vượt 3 câu")).toBeInTheDocument();
    view.unmount();
    renderWithProviders(<SubjectFormHeader subjectName="Hóa" count={50} allocated={50} examId="e1" templateLocked />);
    expect(screen.getByText("Khớp số câu của môn")).toBeInTheDocument();
    expect(screen.getByText("Số câu của mục theo mẫu, không đổi")).toBeInTheDocument();
  });
});

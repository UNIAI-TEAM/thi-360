import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fail, fakeApi } from "@/test/fake-api";
import { QuestionEditorView } from "../components/question-editor-view";
import { questionStore, renderEn, rich, seedQuestion } from "./editor-test-kit";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  usePathname: () => "/questions/q-0001/edit",
  useRouter: () => ({ push: navigation.push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
}));

const PUT = "/api/v1/admin/questions/{id}";

beforeEach(() => {
  navigation.push.mockReset();
});

const putCalls = () => fakeApi.calls.filter((call) => call.method === "PUT" && call.path === PUT);
const sentRevision = (index: number) => (putCalls()[index]?.options as { body: { expectedRevision: number } }).body.expectedRevision;

const questionText = () => screen.findByRole("textbox", { name: /^Question text/ });

describe("lưu câu hỏi: xung đột 412", () => {
  it("hai tab sửa cùng một câu: tab sau nhận thông báo xung đột, tải lại bản mới rồi lưu được", async () => {
    const user = userEvent.setup();
    renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
    rich(await questionText()).set("<p>Chữ gõ ở tab thứ hai</p>");

    // Tab thứ nhất lưu trước: máy chủ tăng revision và đổi nội dung.
    const opened = questionStore.require("q-0001").revision;
    const other = questionStore.require("q-0001");
    other.revision = opened + 1;
    other.content = "<p>Chữ từ tab thứ nhất</p>";

    await user.click(screen.getByRole("button", { name: "Save question" }));

    const notice = await screen.findByRole("alert");
    expect(notice).toHaveTextContent("Someone else has changed this data");
    expect(notice).toHaveTextContent("your save was not applied");
    expect(sentRevision(0)).toBe(opened);
    expect(questionStore.require("q-0001").content).toBe("<p>Chữ từ tab thứ nhất</p>");
    expect(navigation.push).not.toHaveBeenCalled();

    // Tải lại: biểu mẫu dựng lại từ bản mới, phần đã gõ bị bỏ, thông báo biến mất.
    await user.click(screen.getByRole("button", { name: "Reload latest version" }));
    await waitFor(() => expect(screen.queryByRole("alert")).not.toBeInTheDocument());
    await waitFor(async () => expect(rich(await questionText()).text).toBe("Chữ từ tab thứ nhất"));

    rich(await questionText()).set("<p>Bản sửa sau khi tải lại</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(navigation.push).toHaveBeenCalledWith("/questions/q-0001"));
    expect(sentRevision(1)).toBe(opened + 1);
    expect(questionStore.require("q-0001").content).toBe("<p>Bản sửa sau khi tải lại</p>");
  });

  it("xung đột không phải lỗi chung: không hiện chữ lỗi lưu và không giữ trạng thái lưu thành công", async () => {
    const user = userEvent.setup();
    fakeApi.put(PUT, fail(412, { code: "QUESTION_REVISION_CONFLICT", message: "revision conflict" }));
    renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
    rich(await questionText()).set("<p>Sửa</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));

    expect(await screen.findByRole("alert")).toHaveTextContent("Someone else has changed this data");
    expect(screen.queryByText(/could not be saved/)).not.toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Reload latest version" })).toBeInTheDocument();
  });
});

describe("lưu câu hỏi: lỗi 422 theo trường", () => {
  it("đặt lỗi của máy chủ lên đúng ô, hiện dải thông báo và chuyển focus tới ô sai đầu tiên", async () => {
    const user = userEvent.setup();
    fakeApi.put(
      PUT,
      fail(422, {
        code: "VALIDATION_FAILED",
        message: "Validation failed",
        fieldErrors: [
          { code: "QUESTION_BODY_INVALID", field: "body.options[1].content", message: "Option B is empty" },
          { code: "QUESTION_SOMETHING_NEW", field: "solution", message: "x" },
        ] as never,
      }),
    );
    renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
    rich(await questionText()).set("<p>Sửa</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));

    const banner = await screen.findByText(
      "Not saved: the server rejected some fields. Fix the fields marked below.",
    );
    expect(banner).toBeInTheDocument();
    // Mã đã biết dùng chữ riêng, mã lạ dùng chữ chung; chữ của máy chủ không hiện ra.
    expect(screen.getAllByText("The answer section is not valid.").length).toBeGreaterThan(0);
    expect(screen.getAllByText("The server did not accept this value.").length).toBeGreaterThan(0);
    expect(screen.queryByText("Option B is empty")).not.toBeInTheDocument();
    // Ô sai đầu tiên theo thứ tự trên màn hình nhận focus (ô phương án 2 hoặc ô lời giải).
    await waitFor(() => expect(document.activeElement).toHaveAttribute("aria-invalid", "true"));
    expect(navigation.push).not.toHaveBeenCalled();
  });

  it("422 không có lỗi theo trường thì báo theo mã nghiệp vụ, mã lạ và lỗi 500 báo chung", async () => {
    const user = userEvent.setup();
    fakeApi.put(PUT, fail(422, { code: "QUESTION_SET_CHILDREN_MIN", message: "children" }));
    renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
    rich(await questionText()).set("<p>Sửa</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("A question set needs at least 2 sub-questions.");

    fakeApi.put(PUT, fail(500, { code: "INTERNAL", message: "boom" }));
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() =>
      expect(screen.getByRole("alert")).toHaveTextContent("The question could not be saved. Try again in a moment."),
    );
  });
});

describe("lưu câu hỏi: 400 VALIDATION_FAILED không chỉ rõ trường (F-02)", () => {
  it("báo lớp lỗi rõ ràng thay vì câu 'thử lại' chung chung; fieldErrors trên 400 cũng được đặt lên ô", async () => {
    const user = userEvent.setup();
    fakeApi.put(PUT, fail(400, { code: "VALIDATION_FAILED", message: "Validation failed", fieldErrors: [] }));
    renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
    rich(await questionText()).set("<p>Sửa</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("the server rejected the question data as invalid");
    expect(alert).not.toHaveTextContent("Try again in a moment");

    fakeApi.put(
      PUT,
      fail(400, {
        code: "VALIDATION_FAILED",
        message: "Validation failed",
        fieldErrors: [{ code: "QUESTION_BODY_INVALID", field: "body.options[1].content", message: "x" }] as never,
      }),
    );
    await user.click(screen.getByRole("button", { name: "Save question" }));
    await waitFor(() => expect(screen.getByText(/Fix the fields marked below/)).toBeInTheDocument());
  });
});

describe("lưu câu hỏi: lỗi 422 không có ô trên biểu mẫu", () => {
  it("đường dẫn không thuộc biểu mẫu thì không báo 'ô đánh dấu bên dưới' mà ghi lỗi theo mã vào dải thông báo", async () => {
    const user = userEvent.setup();
    // Câu chọn một không có cặp ghép: `body.pairs[0].rightId` không có ô nào hiện lỗi.
    fakeApi.put(
      PUT,
      fail(422, {
        code: "VALIDATION_FAILED",
        message: "Validation failed",
        fieldErrors: [{ code: "QUESTION_PAIR_UNKNOWN_RIGHT", field: "body.pairs[0].rightId", message: "x" }] as never,
      }),
    );
    renderEn(<QuestionEditorView mode="edit" questionId="q-0001" />);
    rich(await questionText()).set("<p>Sửa</p>");
    await user.click(screen.getByRole("button", { name: "Save question" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("A pair points to a right choice that does not exist.");
    expect(alert).not.toHaveTextContent("Fix the fields marked below");
    expect(document.querySelector('[aria-invalid="true"]')).toBeNull();
  });

  it("lỗi của cặp ghép (body.pairs[n]) hiện ở đúng thẻ cặp của biểu mẫu", async () => {
    const user = userEvent.setup();
    const id = await seedQuestion("MATCHING", (values) => {
      values.points = "1";
      values.body.left = [
        { id: "l1", content: "Việt Nam", rightId: "r1", points: "0,5" },
        { id: "l2", content: "Nhật Bản", rightId: "r2", points: "0,5" },
      ];
      values.body.right = [
        { id: "r1", content: "Hà Nội" },
        { id: "r2", content: "Tokyo" },
      ];
    });
    fakeApi.put(
      PUT,
      fail(422, {
        code: "VALIDATION_FAILED",
        message: "Validation failed",
        fieldErrors: [{ code: "QUESTION_PAIR_UNKNOWN_RIGHT", field: "body.pairs[1].rightId", message: "x" }] as never,
      }),
    );
    renderEn(<QuestionEditorView mode="edit" questionId={id} />);
    await screen.findAllByRole("textbox", { name: "Pair points" });
    await user.click(screen.getByRole("button", { name: "Save question" }));

    await screen.findByText("Not saved: the server rejected some fields. Fix the fields marked below.");
    const error = await screen.findByText("A pair points to a right choice that does not exist.");
    // Dòng lỗi nằm ở thẻ của cặp thứ hai (mục "Nhật Bản"), không phải cặp thứ nhất.
    const card = error.closest("li");
    expect(card).toHaveTextContent("Nhật Bản");
    expect(card).not.toHaveTextContent("Việt Nam");
  });
});

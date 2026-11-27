/**
 * Giả lập `mathlive` cho vitest (plan B4): jsdom không chạy được thư viện thật. API tối thiểu mà hộp công thức dùng:
 * `value`, `getValue`, `insert`, sự kiện `input`, `focus`, và các thuộc tính tĩnh `fontsDirectory`/`soundsDirectory`.
 * Dùng: `vi.mock("mathlive", () => import("./mathlive-mock").then((module) => module.mathliveModule))`.
 *
 * Ô giả giữ một chuỗi LaTeX: `type` nối chữ vào cuối và phát `input` như người dùng gõ; `insert` nối mẫu (kể cả
 * `\placeholder{}`) rồi phát `input`. Mọi lần gọi `getValue` ghi định dạng vào `MockMathfield.getValueFormats`.
 */
export class MockMathfield extends HTMLElement {
  static fontsDirectory: string | null = null;
  static soundsDirectory: string | null = "default";
  static instances: MockMathfield[] = [];
  static getValueFormats: string[] = [];
  static inserts: Array<{ latex: string; options: unknown }> = [];

  /** Đặt lại trạng thái dùng chung giữa các test. */
  static reset() {
    MockMathfield.fontsDirectory = null;
    MockMathfield.soundsDirectory = "default";
    MockMathfield.instances = [];
    MockMathfield.getValueFormats = [];
    MockMathfield.inserts = [];
  }

  /** Ô vừa dựng gần nhất. */
  static get current(): MockMathfield {
    const field = MockMathfield.instances.at(-1);
    if (!field) throw new Error("No math-field has been created");
    return field;
  }

  mathVirtualKeyboardPolicy = "auto";
  /** Mặc định của mathlive là `true` (nhảy khỏi ô mũ sau một chữ số); ô soạn tắt đi (UNI-2878). */
  smartSuperscript = true;
  private menu: unknown[] = [{ id: "default" }];
  private content = "";

  constructor() {
    super();
    MockMathfield.instances.push(this);
  }

  get menuItems(): unknown[] {
    return this.menu;
  }

  /** Như mathlive thật: `menuItems` chỉ đặt được khi ô đã gắn vào trang. */
  set menuItems(items: unknown[]) {
    if (!this.isConnected) throw new Error("Mathfield not mounted");
    this.menu = items;
  }

  get value(): string {
    return this.content;
  }

  set value(next: string) {
    this.content = next;
  }

  getValue(format?: string): string {
    MockMathfield.getValueFormats.push(format ?? "latex");
    return this.content;
  }

  insert(latex: string, options?: unknown): boolean {
    MockMathfield.inserts.push({ latex, options });
    this.content += latex;
    this.dispatchEvent(new Event("input", { bubbles: true }));
    return true;
  }

  /** Người dùng gõ vào ô: nối chữ rồi phát `input`. */
  type(text: string) {
    this.content += text;
    this.dispatchEvent(new Event("input", { bubbles: true }));
  }

  /** Người dùng thay cả nội dung ô (xóa hết rồi gõ lại). */
  replaceAll(text: string) {
    this.content = text;
    this.dispatchEvent(new Event("input", { bubbles: true }));
  }
}

if (!customElements.get("math-field")) customElements.define("math-field", MockMathfield);

export const mathliveModule = { MathfieldElement: MockMathfield };

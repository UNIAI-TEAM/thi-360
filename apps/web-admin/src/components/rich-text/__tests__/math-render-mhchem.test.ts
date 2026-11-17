import katex from "katex";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";

/** mhchem nạp theo yêu cầu: mỗi test cần module `math-render` mới (trạng thái nạp là biến của module). */
async function freshRenderMath() {
  vi.resetModules();
  // Nạp động có chủ đích: cần bản module mới sau `resetModules`, không import tĩnh được.
  return (await import("../math-render")).renderMath;
}

function formula(latex: string) {
  const host = document.createElement("div");
  host.innerHTML = `<span class="math-inline">\\(${latex}\\)</span>`;
  document.body.append(host);
  return host;
}

describe("renderMath with on-demand mhchem", () => {
  beforeEach(() => {
    document.body.replaceChildren();
  });

  afterEach(() => {
    vi.doUnmock("katex/contrib/mhchem");
  });

  it("renders a neutral placeholder for a \\ce formula first, then the KaTeX output", async () => {
    const renderMath = await freshRenderMath();
    const host = formula("\\ce{H2O}");
    renderMath(host);

    const span = host.querySelector("span.math-inline")!;
    const placeholder = span.querySelector("[aria-busy='true']");
    expect(placeholder).toBeTruthy();
    expect(placeholder!.getAttribute("aria-label")).toBe("\\ce{H2O}");
    expect(span.textContent).toBe("\u2026");
    expect(span.querySelector(".katex")).toBeNull();

    await vi.waitFor(() => expect(span.querySelector(".katex")).toBeTruthy());
    expect(span.querySelector("[aria-busy]")).toBeNull();
    expect(span.classList.contains("math-error")).toBe(false);
  });

  it("renders a formula without \\ce or \\pu synchronously", async () => {
    const renderMath = await freshRenderMath();
    const host = formula("x^2");
    renderMath(host);
    expect(host.querySelector(".katex")).toBeTruthy();
    expect(host.querySelector("[aria-busy]")).toBeNull();
  });

  it("does not re-render a placeholder whose element left the DOM", async () => {
    const renderMath = await freshRenderMath();
    const host = formula("\\pu{1.5 mol}");
    renderMath(host);
    const span = host.querySelector("span.math-inline")!;
    host.remove();
    await vi.dynamicImportSettled();
    expect(span.querySelector(".katex")).toBeNull();
  });

  it("falls back to the error rendering when mhchem fails to load", async () => {
    vi.resetModules();
    // `katex` là module ngoài, dùng chung giữa các test (mhchem đã đăng ký macro ở test trước): ép dựng lỗi để kiểm đường lỗi.
    vi.doMock("katex/contrib/mhchem", () => {
      throw new Error("chunk load failed");
    });
    vi.spyOn(katex, "render").mockImplementation(() => {
      throw new Error("Undefined control sequence: \\ce");
    });
    const { renderMath } = await import("../math-render"); // nạp động có chủ đích: sau `doMock`/`resetModules`
    const host = formula("\\ce{H2O}");
    renderMath(host);

    const span = host.querySelector("span.math-inline")!;
    await vi.waitFor(() => expect(span.classList.contains("math-error")).toBe(true));
    expect(span.textContent).toBe("\\(\\ce{H2O}\\)");
  });
});

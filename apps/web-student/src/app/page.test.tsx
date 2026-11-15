import { render, screen } from "@testing-library/react";
import { NextIntlClientProvider } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import viMessages from "@/messages/vi.json";
import LandingPage from "./page";

vi.mock("next-intl/server", () => ({
  getTranslations: async (namespace: "landing" | "common") => (key: string) => {
    const block = viMessages[namespace] as Record<string, unknown>;
    const value = key.split(".").reduce<unknown>((node, part) => (node as Record<string, unknown>)[part], block);
    return String(value);
  },
}));

describe("web-student landing page", () => {
  it("có đúng một <main> chứa tiêu đề và phần giới thiệu; header và footer nằm ngoài <main>", async () => {
    const tree = await LandingPage();
    render(
      <NextIntlClientProvider locale="vi" messages={viMessages} timeZone="Asia/Ho_Chi_Minh">
        {tree}
      </NextIntlClientProvider>,
    );

    const mains = screen.getAllByRole("main");
    expect(mains).toHaveLength(1);
    const main = mains[0]!;
    expect(main).toContainElement(screen.getByRole("heading", { level: 1 }));
    expect(main).toContainElement(screen.getByRole("heading", { level: 2, name: "Thi 360 giúp bạn điều gì" }));
    expect(main).not.toContainElement(screen.getByRole("banner"));
    expect(main).not.toContainElement(screen.getByRole("contentinfo"));
  });
});

import { screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import type * as SessionApi from "@/features/session/api";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import LandingPage from "./page";

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, loadOptionalSession: async () => null };
});

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
    renderWithProviders(tree);

    const mains = screen.getAllByRole("main");
    expect(mains).toHaveLength(1);
    const main = mains[0]!;
    expect(main).toContainElement(screen.getByRole("heading", { level: 1 }));
    expect(main).toContainElement(screen.getByRole("heading", { level: 2, name: "Thi 360 giúp bạn điều gì" }));
    expect(main).not.toContainElement(screen.getByRole("banner"));
    expect(main).not.toContainElement(screen.getByRole("contentinfo"));
  });
});

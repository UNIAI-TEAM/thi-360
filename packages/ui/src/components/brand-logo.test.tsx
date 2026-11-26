import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { BrandLogo, BrandWordmark } from "./brand-logo";

describe("BrandLogo", () => {
  it("is decorative by default and uses unique gradient IDs across instances", () => {
    const { container } = render(
      <div>
        <BrandLogo />
        <BrandLogo />
      </div>,
    );
    const svgs = container.querySelectorAll("svg");
    expect(svgs).toHaveLength(2);
    expect(svgs[0]).toHaveAttribute("aria-hidden", "true");
    expect(svgs[0]).not.toHaveAttribute("role");

    const gradientIds = [...container.querySelectorAll("linearGradient")].map((node) => node.getAttribute("id"));
    expect(gradientIds).toHaveLength(4);
    expect(new Set(gradientIds).size).toBe(4);
  });

  it("exposes role=img and aria-label when label is provided", () => {
    render(<BrandLogo label="Thi360" />);
    const logo = screen.getByRole("img", { name: "Thi360" });
    expect(logo).not.toHaveAttribute("aria-hidden");
  });
});

describe("BrandWordmark", () => {
  it("renders the horizontal lockup asset with accessible alt text", () => {
    render(<BrandWordmark alt="Thi360" />);
    const img = screen.getByRole("img", { name: "Thi360" });
    expect(img).toHaveAttribute("src", "/logo-full.png");
    expect(img).not.toHaveAttribute("aria-hidden");
  });

  it("marks empty alt wordmarks as aria-hidden", () => {
    const { container } = render(<BrandWordmark alt="" />);
    const img = container.querySelector("img");
    expect(img).toHaveAttribute("aria-hidden", "true");
    expect(img).toHaveAttribute("alt", "");
  });
});

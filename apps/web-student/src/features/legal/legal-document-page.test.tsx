import { screen, within } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import enMessages from "@/messages/en.json";
import { renderWithProviders } from "@/test/render";
import draftFixture from "./__fixtures__/draft-document.json";
import type { LegalDocument } from "./legal-document";
import { LegalDocumentPage } from "./legal-document-page";
import { getLegalDocument } from "./legal-documents";

describe("LegalDocumentPage", () => {
  it("rendersEveryBlock", () => {
    for (const [id, breadcrumbTitle] of [
      ["terms", "Điều kiện giao dịch chung"],
      ["privacy", "Chính sách bảo mật thông tin"],
    ] as const) {
      const doc = getLegalDocument(id, "vi");
      const view = renderWithProviders(<LegalDocumentPage doc={doc} breadcrumbTitle={breadcrumbTitle} />);

      const nav = screen.getByRole("navigation", { name: "Đường dẫn trang" });
      expect(within(nav).getByRole("link", { name: "Trang chủ" })).toHaveAttribute("href", "/");
      expect(within(nav).getByText(breadcrumbTitle)).toHaveAttribute("aria-current", "page");

      expect(screen.getByRole("heading", { level: 1, name: doc.title })).toBeInTheDocument();
      expect(screen.getByText("Có hiệu lực từ 26/11/2026")).toBeInTheDocument();

      for (const block of doc.blocks) {
        if (block.type === "heading") {
          expect(screen.getByRole("heading", { level: block.level, name: block.text })).toBeInTheDocument();
        } else if (block.type === "paragraph") {
          expect(screen.getByText(block.text)).toBeInTheDocument();
        } else if (block.type === "list") {
          for (const item of block.items) {
            expect(screen.getByText(item)).toBeInTheDocument();
          }
        } else if (block.type === "table") {
          const table = screen.getByRole("table");
          expect(table.parentElement).toHaveClass("overflow-x-auto");
          expect(table.parentElement).toHaveAttribute("role", "region");
          expect(table.parentElement).toHaveAttribute("tabindex", "0");
          for (const header of block.headers) {
            expect(within(table).getByRole("columnheader", { name: header })).toBeInTheDocument();
          }
          for (const row of block.rows) {
            for (const cell of row) {
              expect(within(table).getAllByText(cell).length).toBeGreaterThan(0);
            }
          }
        }
      }

      view.unmount();
    }
  });

  it("english", () => {
    for (const [id, breadcrumbTitle] of [
      ["terms", "General transaction terms"],
      ["privacy", "Information privacy policy"],
    ] as const) {
      const doc = getLegalDocument(id, "en");
      const view = renderWithProviders(<LegalDocumentPage doc={doc} breadcrumbTitle={breadcrumbTitle} />, {
        locale: "en",
        messages: enMessages,
      });

      expect(screen.getByRole("heading", { level: 1, name: doc.title })).toBeInTheDocument();
      expect(screen.getByText("Effective from 26/11/2026")).toBeInTheDocument();
      expect(
        screen.getByText(
          "This English version is a translation. If it differs from the Vietnamese version, the Vietnamese version prevails.",
        ),
      ).toBeInTheDocument();

      view.unmount();
    }
  });

  it("draftBanner", () => {
    const draftDoc = draftFixture as LegalDocument;
    const viView = renderWithProviders(
      <LegalDocumentPage doc={draftDoc} breadcrumbTitle="Điều kiện giao dịch chung" />,
    );
    expect(screen.getByRole("status")).toHaveTextContent("Bản nháp, chờ đơn vị vận hành xác nhận.");
    expect(screen.queryByText(/Có hiệu lực từ/)).not.toBeInTheDocument();
    viView.unmount();

    renderWithProviders(<LegalDocumentPage doc={draftDoc} breadcrumbTitle="General transaction terms" />, {
      locale: "en",
      messages: enMessages,
    });
    expect(screen.getByRole("status")).toHaveTextContent("Draft, pending confirmation by the operator.");
    expect(screen.queryByText(/Effective from/)).not.toBeInTheDocument();
  });

  it("publishedRealFiles", () => {
    for (const id of ["terms", "privacy"] as const) {
      const viDoc = getLegalDocument(id, "vi");
      const viView = renderWithProviders(<LegalDocumentPage doc={viDoc} breadcrumbTitle={viDoc.title} />);
      expect(screen.queryByText("Bản nháp, chờ đơn vị vận hành xác nhận.")).not.toBeInTheDocument();
      expect(screen.getByText("Có hiệu lực từ 26/11/2026")).toBeInTheDocument();
      viView.unmount();

      const enDoc = getLegalDocument(id, "en");
      const enView = renderWithProviders(<LegalDocumentPage doc={enDoc} breadcrumbTitle={enDoc.title} />, {
        locale: "en",
        messages: enMessages,
      });
      expect(screen.queryByText("Draft, pending confirmation by the operator.")).not.toBeInTheDocument();
      expect(screen.getByText("Effective from 26/11/2026")).toBeInTheDocument();
      enView.unmount();
    }
  });
});

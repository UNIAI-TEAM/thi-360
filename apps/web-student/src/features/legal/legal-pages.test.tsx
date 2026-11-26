import { existsSync, readdirSync, readFileSync } from "node:fs";
import path from "node:path";
import { screen } from "@testing-library/react";
import { createTranslator } from "next-intl";
import { describe, expect, it, vi } from "vitest";
import PrivacyPolicyPage from "@/app/chinh-sach-bao-mat-thong-tin/page";
import TermsOfServicePage from "@/app/dieu-khoan-giao-dich-chung/page";
import { PublicFooter } from "@/components/public-footer";
import type * as SessionApi from "@/features/session/api";
import enMessages from "@/messages/en.json";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import draftFixture from "./__fixtures__/draft-document.json";
import { formatLegalEffectiveDate, type LegalDocument } from "./legal-document";
import { LegalDocumentPage } from "./legal-document-page";
import { getLegalDocument } from "./legal-documents";

const localeState = vi.hoisted(() => ({ locale: "vi" as "vi" | "en" }));

vi.mock("next-intl/server", () => ({
  getLocale: async () => localeState.locale,
  getTranslations: async (namespace: string) =>
    createTranslator({
      locale: localeState.locale,
      messages: localeState.locale === "vi" ? viMessages : enMessages,
      namespace: namespace as "legal",
    }),
}));

vi.mock("@/features/session/api", async () => {
  const actual = await vi.importActual<typeof SessionApi>("@/features/session/api");
  return { ...actual, loadOptionalSession: async () => null };
});

vi.mock("next/navigation", () => ({
  usePathname: () => "/dieu-khoan-giao-dich-chung",
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), refresh: vi.fn() }),
}));

const SRC_ROOT = path.resolve(import.meta.dirname, "../..");

function collectLeafPaths(obj: unknown, prefix = ""): string[] {
  if (typeof obj !== "object" || obj === null || Array.isArray(obj)) {
    return [prefix];
  }
  const paths: string[] = [];
  for (const [key, value] of Object.entries(obj)) {
    const next = prefix ? `${prefix}.${key}` : key;
    paths.push(...collectLeafPaths(value, next));
  }
  return paths;
}

function walkTsxFiles(dir: string): string[] {
  const files: string[] = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...walkTsxFiles(full));
    } else if (entry.name.endsWith(".tsx") && !entry.name.endsWith(".test.tsx")) {
      files.push(full);
    }
  }
  return files;
}

function extractArticleTextSequence(article: HTMLElement): string[] {
  const texts: string[] = [];
  const walker = document.createTreeWalker(article, NodeFilter.SHOW_TEXT);
  let node = walker.nextNode();
  while (node) {
    const normalized = (node.textContent ?? "").replace(/\s+/g, " ").trim();
    if (normalized) {
      texts.push(normalized);
    }
    node = walker.nextNode();
  }
  return texts;
}

function expectedArticleTextSequence(doc: LegalDocument, locale: "vi" | "en"): string[] {
  const messages = locale === "vi" ? viMessages : enMessages;
  const expected: string[] = [doc.title];

  if (doc.effectiveDate) {
    expected.push(
      messages.legal.effectiveFrom.replace("{date}", formatLegalEffectiveDate(doc.effectiveDate)),
    );
  }
  if (doc.status === "draft") {
    expected.push(messages.legal.draftBanner);
  }
  if (doc.note) {
    expected.push(doc.note);
  }

  for (const block of doc.blocks) {
    switch (block.type) {
      case "heading":
      case "paragraph":
        expected.push(block.text);
        break;
      case "list":
        expected.push(...block.items);
        break;
      case "table":
        expected.push(...block.headers);
        for (const row of block.rows) {
          expected.push(...row);
        }
        break;
    }
  }

  return expected.map((text) => text.replace(/\s+/g, " ").trim());
}

describe("legal pages single source of truth (ĐT 788)", () => {
  it("singleSource", () => {
    const allowedLegalKeys = [
      "breadcrumbHome",
      "breadcrumbLabel",
      "draftBanner",
      "effectiveFrom",
      "privacy.title",
      "terms.title",
    ];

    for (const [locale, messages] of [
      ["vi.json", viMessages],
      ["en.json", enMessages],
    ] as const) {
      const actualKeys = collectLeafPaths(messages.legal).sort();
      const extraKeys = actualKeys.filter((k) => !allowedLegalKeys.includes(k));
      const missingKeys = allowedLegalKeys.filter((k) => !actualKeys.includes(k));
      expect(extraKeys, `${locale} has disallowed legal.* keys: ${extraKeys.join(", ")}`).toEqual([]);
      expect(missingKeys, `${locale} is missing required legal.* keys: ${missingKeys.join(", ")}`).toEqual([]);
    }

    expect(existsSync(path.join(SRC_ROOT, "features/legal/terms-of-service-view.tsx"))).toBe(false);
    expect(existsSync(path.join(SRC_ROOT, "features/legal/privacy-policy-view.tsx"))).toBe(false);
    expect(existsSync(path.join(SRC_ROOT, "lib/site-url.ts"))).toBe(false);

    const violations: string[] = [];
    for (const file of walkTsxFiles(SRC_ROOT)) {
      const rel = path.relative(SRC_ROOT, file);
      const source = readFileSync(file, "utf8");

      if (/useTranslations\(\s*["']legal\.(terms|privacy)(?:\.[^"']*)?["']\s*\)/.test(source)) {
        violations.push(`${rel}: calls useTranslations("legal.terms|privacy")`);
      }

      if (/getTranslations\(\s*["']legal\.(terms|privacy)\.[^"']+["']\s*\)/.test(source)) {
        violations.push(`${rel}: calls getTranslations on nested legal content namespace`);
      }

      const contentKeyMatch = /["'](?:legal\.)?(?:terms|privacy)\.(?!title["'])[a-zA-Z0-9_.]+["']/.exec(source);
      if (contentKeyMatch) {
        violations.push(`${rel}: references legal content key ${contentKeyMatch[0]}`);
      }

      const getTransMatch = /const\s+(\w+)\s*=\s*(?:await\s+)?getTranslations\(\s*["']legal\.(?:terms|privacy)["']\s*\)/.exec(
        source,
      );
      if (getTransMatch) {
        const varName = getTransMatch[1]!;
        const callRe = new RegExp(`\\b${varName}\\(\\s*["']([^"']+)["']`, "g");
        for (const call of source.matchAll(callRe)) {
          if (call[1] !== "title") {
            violations.push(`${rel}: calls ${varName}("${call[1]}") outside "title"`);
          }
        }
      }
    }

    expect(violations, `Disallowed legal translation usage:\n${violations.join("\n")}`).toEqual([]);
  });

  it("articleEqualsContractFile", async () => {
    const routes = [
      { id: "terms" as const, renderPage: TermsOfServicePage },
      { id: "privacy" as const, renderPage: PrivacyPolicyPage },
    ];

    for (const locale of ["vi", "en"] as const) {
      localeState.locale = locale;
      const messages = locale === "vi" ? viMessages : enMessages;

      for (const { id, renderPage } of routes) {
        const tree = await renderPage();
        const view = renderWithProviders(tree, { locale, messages });

        const article = view.container.querySelector("article");
        expect(article, `${id} (${locale}) must render <article>`).not.toBeNull();

        const actual = extractArticleTextSequence(article!);
        const expected = expectedArticleTextSequence(getLegalDocument(id, locale), locale);
        expect(actual, `${id} (${locale}) <article> text differs from contract file`).toEqual(expected);

        view.unmount();
      }
    }

    localeState.locale = "vi";
    const draftDoc = draftFixture as LegalDocument;
    const draftView = renderWithProviders(
      <LegalDocumentPage doc={draftDoc} breadcrumbTitle={viMessages.legal.terms.title} />,
    );
    const draftArticle = draftView.container.querySelector("article");
    expect(draftArticle).not.toBeNull();
    expect(extractArticleTextSequence(draftArticle!)).toEqual(expectedArticleTextSequence(draftDoc, "vi"));
    draftView.unmount();
  });

  it("footerLinksResolve", () => {
    renderWithProviders(<PublicFooter />);

    const termsLinks = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("href") === "/dieu-khoan-giao-dich-chung");
    const privacyLinks = screen
      .getAllByRole("link")
      .filter((link) => link.getAttribute("href") === "/chinh-sach-bao-mat-thong-tin");

    expect(termsLinks.length).toBeGreaterThan(0);
    expect(privacyLinks.length).toBeGreaterThan(0);
    expect(screen.getByRole("link", { name: "Chính sách bảo mật thông tin" })).toHaveAttribute(
      "href",
      "/chinh-sach-bao-mat-thong-tin",
    );
  });
});

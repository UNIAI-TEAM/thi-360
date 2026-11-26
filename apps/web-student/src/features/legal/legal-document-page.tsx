import { cn, focusRing } from "@thi360/ui";
import { useTranslations } from "next-intl";
import Link from "next/link";
import { formatLegalEffectiveDate, type LegalBlock, type LegalDocument } from "./legal-document";

function renderBlock(block: LegalBlock, index: number) {
  switch (block.type) {
    case "heading":
      return block.level === 2 ? (
        <h2 key={index} className="pt-2 text-h3 font-bold text-text">
          {block.text}
        </h2>
      ) : (
        <h3 key={index} className="pt-1 text-title font-semibold text-text">
          {block.text}
        </h3>
      );

    case "paragraph":
      return (
        <p key={index} className="text-body text-text-secondary">
          {block.text}
        </p>
      );

    case "list": {
      const ListTag = block.ordered ? "ol" : "ul";
      return (
        <ListTag
          key={index}
          className={cn(
            "space-y-1.5 pl-6 text-body text-text-secondary",
            block.ordered ? "list-decimal" : "list-disc",
          )}
        >
          {block.items.map((item, itemIndex) => (
            <li key={itemIndex}>{item}</li>
          ))}
        </ListTag>
      );
    }

    case "table":
      return (
        <div
          key={index}
          tabIndex={0}
          role="region"
          aria-label={block.headers.join(", ")}
          className={cn("overflow-x-auto rounded-xl border border-border", focusRing)}
        >
          <table className="w-full border-collapse text-left text-body">
            <thead>
              <tr className="border-b border-border bg-table-head text-label font-semibold text-text">
                {block.headers.map((header, headerIndex) => (
                  <th key={headerIndex} scope="col" className="px-4 py-3">
                    {header}
                  </th>
                ))}
              </tr>
            </thead>
            <tbody className="divide-y divide-border text-text-secondary">
              {block.rows.map((row, rowIndex) => (
                <tr key={rowIndex}>
                  {row.map((cell, cellIndex) => (
                    <td key={cellIndex} className="px-4 py-3 align-top">
                      {cell}
                    </td>
                  ))}
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      );
  }
}

export function LegalDocumentPage({
  doc,
  breadcrumbTitle,
}: {
  readonly doc: LegalDocument;
  readonly breadcrumbTitle: string;
}) {
  const legal = useTranslations("legal");

  return (
    <div className="mx-auto flex w-full max-w-[880px] flex-col gap-6 px-4 py-8 sm:px-6 sm:py-10">
      <nav aria-label={legal("breadcrumbLabel")}>
        <ol className="flex flex-wrap items-center gap-2 text-field-label text-text-muted">
          <li>
            <Link href="/" className={cn("hover:underline", focusRing)}>
              {legal("breadcrumbHome")}
            </Link>
          </li>
          <li aria-hidden>/</li>
          <li aria-current="page" className="text-text">
            {breadcrumbTitle}
          </li>
        </ol>
      </nav>

      <article className="flex flex-col gap-5 rounded-2xl border border-border bg-surface p-6 shadow-sm sm:p-10">
        <header className="flex flex-col gap-2.5 border-b border-border pb-5">
          <h1 className="text-h2 font-bold text-primary-900">{doc.title}</h1>
          {doc.effectiveDate && (
            <p className="text-field-label font-medium text-text-muted">
              {legal("effectiveFrom", { date: formatLegalEffectiveDate(doc.effectiveDate) })}
            </p>
          )}
          {doc.status === "draft" && (
            <p
              role="status"
              className="rounded-lg border border-warning bg-warning-bg px-3.5 py-2.5 text-body font-medium text-warning"
            >
              {legal("draftBanner")}
            </p>
          )}
          {doc.note && <p className="text-field-label italic text-text-muted">{doc.note}</p>}
        </header>

        <div className="flex flex-col gap-4">{doc.blocks.map(renderBlock)}</div>
      </article>
    </div>
  );
}

import { Fragment } from "react";

const START = "\uFDD0";
const END = "\uFDD1";

/** Preserve the exact boundaries of stored names while formatting translated messages. */
export function markExamContent(value: string): string {
  return START + JSON.stringify(value).replaceAll(START, "\\ufdd0").replaceAll(END, "\\ufdd1") + END;
}

export function ExamContentText({ text }: { text: string }) {
  return text.split(/(\uFDD0[^\uFDD1]*\uFDD1)/u).map((part, index) => {
    if (part.startsWith(START) && part.endsWith(END)) {
      try {
        const value: unknown = JSON.parse(part.slice(1, -1));
        if (typeof value === "string") return <span key={index} data-exam-content>{value}</span>;
      } catch {
        // An unrelated string may contain the delimiter characters.
      }
    }
    return <Fragment key={index}>{part}</Fragment>;
  });
}

import type { ReactNode } from "react";
import { MockAccessGate } from "@/features/session/mock-access";

export default function QuestionsLayout({ children }: { children: ReactNode }) {
  return (
    <MockAccessGate permission="QUESTION.VIEW" flag="content">
      {children}
    </MockAccessGate>
  );
}

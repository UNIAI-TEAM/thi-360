import type { ReactNode } from "react";
import { MockAccessGate } from "@/features/session/mock-access";

export default function GradingLayout({ children }: { children: ReactNode }) {
  return (
    <MockAccessGate permission="GRADING.VIEW" flag="grading">
      {children}
    </MockAccessGate>
  );
}

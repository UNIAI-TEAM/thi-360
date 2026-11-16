import type { ReactNode } from "react";
import { MockAccessGate } from "@/features/session/mock-access";

export default function ExamsLayout({ children }: { children: ReactNode }) {
  return (
    <MockAccessGate permission="EXAM.VIEW" flag="content">
      {children}
    </MockAccessGate>
  );
}

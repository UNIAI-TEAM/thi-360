import type { ExamAccessRow } from "@/features/storefront/exam-access";
import type { ProductCard, ProductDetail } from "@/features/storefront/types";

export const STUDENT_SESSION = {
  id: "u-stu",
  username: "student01",
  fullName: "Hoc vien 01",
  realm: "APP" as const,
  access: "EXAM" as const,
  permissions: [],
  homeUrl: "http://localhost:3000/home",
};

export const ADMIN_SESSION = {
  id: "u-adm",
  username: "ops.admin",
  fullName: "Van hanh",
  realm: "APP" as const,
  access: "MANAGE" as const,
  permissions: ["USER.VIEW"],
  homeUrl: "http://localhost:3001",
};

export const FULL_CARD: ProductCard = {
  code: "HSA-FULL-01",
  name: "De thi thu HSA FULL so 01",
  summary: "De day du ba phan",
  versionNo: 3,
  attemptsGranted: 3,
  buildMode: "STANDARD",
  coverUrl: "/api/v1/public/storefront/covers/0192f3a1-0000-7000-8000-000000000001",
  examKind: { code: "HSA", nameVi: "Danh gia nang luc", nameEn: "Aptitude test" },
  format: "FULL",
  listPrice: 249000,
  price: 199000,
  promotion: { name: "Uu dai thang 11", endsAt: "2026-11-30T16:59:00Z" },
  questionCount: { min: 150, max: 150 },
  durationMinutes: { min: 195, max: 195 },
};

export const PART_CARD: ProductCard = {
  code: "HSA-P-TOAN-001",
  name: "HSA Toan hoc va Xu li so lieu De 01",
  versionNo: 1,
  attemptsGranted: 3,
  buildMode: "STANDARD",
  examKind: { code: "HSA", nameVi: "Danh gia nang luc", nameEn: "Aptitude test" },
  format: "PART",
  scopeLabel: { nameVi: "Toan hoc va Xu li so lieu", nameEn: "Toan hoc va Xu li so lieu" },
  listPrice: 79000,
  price: 79000,
  questionCount: { min: 48, max: 50 },
  durationMinutes: { min: 75, max: 75 },
};

export const REVIEW_CARD: ProductCard = {
  ...PART_CARD,
  code: "HSA-R-TOAN-001",
  name: "HSA Toan on tap chu de",
  buildMode: "REVIEW",
  format: "SUBJECT",
  scopeLabel: { nameVi: "Toan", nameEn: "Math" },
};

export const FULL_DETAIL: ProductDetail = {
  code: "HSA-FULL-01",
  name: "De thi thu HSA FULL so 01",
  summary: "De day du ba phan",
  description: "Lam tren may, tinh gio tung phan",
  objectives: "Lam quen ap luc thoi gian",
  audience: "Hoc sinh lop 12",
  versionNo: 3,
  attemptsGranted: 3,
  buildMode: "STANDARD",
  examKind: { code: "HSA", nameVi: "Danh gia nang luc", nameEn: "Aptitude test" },
  format: "FULL",
  listPrice: 249000,
  price: 199000,
  promotion: { name: "Uu dai thang 11", endsAt: "2026-11-30T16:59:00Z" },
  questionCount: { min: 150, max: 150 },
  durationMinutes: { min: 195, max: 195 },
  maxPoints: { min: 150, max: 150 },
  resultView: "FULL_SOLUTION",
  benefits: { aiExplanation: true, strengthWeakness: true, practice: false, note: "AI cho phan 1 va 2" },
  choices: [
    {
      partName: "Phan 3",
      options: [
        {
          code: "KH",
          kind: "SUBJECT_CHOICE",
          name: "Khoa hoc",
          chooseSubjects: 3,
          countsByPick: [17, 17, 16],
          answerTypes: [],
          subjectAnswerTypes: [],
          subjects: [
            { code: "LY", nameVi: "Vat li", nameEn: "Physics" },
            { code: "HOA", nameVi: "Hoa hoc", nameEn: "Chemistry" },
            { code: "SINH", nameVi: "Sinh hoc", nameEn: "Biology" },
          ],
        },
      ],
    },
  ],
  omittedEssayGroups: [],
  parts: [
    {
      order: 1,
      name: "Toan hoc va Xu li so lieu",
      required: true,
      questionCount: { min: 50, max: 50 },
      durationMinutes: { min: 75, max: 75 },
      maxPoints: { min: 50, max: 50 },
      answerTypes: [
        { questionType: "SINGLE_CHOICE", questionCount: 40 },
        { questionType: "SHORT_ANSWER", questionCount: 10 },
      ],
    },
    {
      order: 3,
      name: "Khoa hoc hoac Tieng Anh",
      required: false,
      questionCount: { min: 50, max: 50 },
      durationMinutes: { min: 60, max: 60 },
      maxPoints: { min: 50, max: 50 },
      answerTypes: [],
    },
  ],
  scoring: { maxScore: 150, timing: "PER_PART" },
};

export function accessRow(overrides: Partial<ExamAccessRow> = {}): ExamAccessRow {
  return {
    code: "HSA-FULL-01",
    state: "NONE",
    attemptsLeft: 0,
    entitlementId: null,
    activeAttemptId: null,
    pendingOrderCode: null,
    hasPendingFulfillment: false,
    ...overrides,
  };
}

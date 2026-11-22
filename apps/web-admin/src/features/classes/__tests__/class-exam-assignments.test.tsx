import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SessionProvider } from "@/features/session/session-provider";
import type { Session } from "@/features/session/types";
import { TEST_ADMIN_SESSION } from "@/test/fixtures";
import { renderWithProviders } from "@/test/render";
import {
  type AssignableExamItem,
  type ClassDetail,
  type ClassesApiDeps,
  type ClassExamAssignmentItem,
  defaultClassesApiDeps,
} from "../api";
import { ClassDetailView } from "../components/class-detail-view";

const routerMock = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("next/navigation", () => ({
  useRouter: () => routerMock,
}));

const BASE_CLASS: ClassDetail = {
  id: "00000000-0000-4000-8000-000000000301",
  code: "LOP-HSA-01",
  name: "Lớp HSA 01",
  description: "Nhóm ôn HSA chuyên sâu",
  ownerUserId: "00000000-0000-4000-8000-000000000002",
  ownerUsername: "ops.admin",
  ownerFullName: "Vận hành CSKH",
  status: "ACTIVE",
  memberCount: 2,
  version: 0,
  createdAt: "2026-11-10T08:00:00Z",
  updatedAt: "2026-11-10T08:00:00Z",
};

const ASSIGNABLE_EXAMS: AssignableExamItem[] = [
  {
    examId: "00000000-0000-4000-8000-000000000801",
    code: "HSA-FULL-01",
    name: "Đề luyện tập HSA FULL số 01",
    versionNo: 2,
    examKind: {
      code: "HSA",
      nameVi: "Đánh giá năng lực HSA",
      nameEn: "HSA Aptitude Test",
    },
    scope: "FULL",
    buildMode: "STANDARD",
    defaultResultView: "FULL_SOLUTION",
    activeInClass: false,
  },
  {
    examId: "00000000-0000-4000-8000-000000000802",
    code: "HSA-PART-02",
    name: "Đề tư duy định lượng tuần 2",
    versionNo: 1,
    examKind: {
      code: "HSA",
      nameVi: "Đánh giá năng lực HSA",
      nameEn: "HSA Aptitude Test",
    },
    scope: "PART",
    buildMode: "REVIEW",
    defaultResultView: "SCORE_AND_ANSWERS",
    activeInClass: true,
  },
];

const ACTIVE_ASSIGNMENT: ClassExamAssignmentItem = {
  id: "00000000-0000-4000-8000-000000000901",
  classId: BASE_CLASS.id,
  examId: ASSIGNABLE_EXAMS[0]!.examId,
  examCode: ASSIGNABLE_EXAMS[0]!.code,
  examName: ASSIGNABLE_EXAMS[0]!.name,
  examVersionNo: 2,
  examKind: ASSIGNABLE_EXAMS[0]!.examKind,
  scope: "FULL",
  attemptsGranted: 3,
  resultView: "FULL_SOLUTION",
  autoGrantNewMembers: true,
  status: "ACTIVE",
  note: "Bài kiểm tra tuần 1",
  assignedBy: "00000000-0000-4000-8000-000000000002",
  assignedByUsername: "ops.admin",
  assignedAt: "2026-11-22T08:00:00Z",
  revokedBy: null,
  revokedByUsername: null,
  revokedAt: null,
  revokeReason: null,
  grantedMembersCount: 2,
  totalAttemptsUsed: 1,
  totalAttemptsLeft: 5,
  totalAttemptsRevoked: 0,
};

function makeDeps(overrides: Partial<ClassesApiDeps> = {}): Partial<ClassesApiDeps> {
  return {
    ...defaultClassesApiDeps,
    getClass: vi.fn<ClassesApiDeps["getClass"]>(async () => BASE_CLASS),
    listClassMembers: vi.fn<ClassesApiDeps["listClassMembers"]>(async () => ({
      items: [],
      page: 0,
      size: 20,
      totalItems: 0,
      totalPages: 0,
    })),
    listClassExamAssignments: vi.fn<ClassesApiDeps["listClassExamAssignments"]>(async () => []),
    listAssignableClassExams: vi.fn<ClassesApiDeps["listAssignableClassExams"]>(
      async () => ASSIGNABLE_EXAMS,
    ),
    assignClassExams: vi.fn<ClassesApiDeps["assignClassExams"]>(async (_classId, body) => ({
      assignments: [
        {
          ...ACTIVE_ASSIGNMENT,
          attemptsGranted: body.attemptsGranted,
          resultView: body.resultView,
          autoGrantNewMembers: body.autoGrantNewMembers ?? true,
          note: body.note ?? null,
        },
      ],
      totalGrantsCreated: 2,
    })),
    updateClassExamAssignment: vi.fn<ClassesApiDeps["updateClassExamAssignment"]>(
      async (_classId, _assignmentId, body) => ({
        ...ACTIVE_ASSIGNMENT,
        autoGrantNewMembers: body.autoGrantNewMembers,
      }),
    ),
    revokeClassExamAssignment: vi.fn<ClassesApiDeps["revokeClassExamAssignment"]>(async () => ({
      assignment: {
        ...ACTIVE_ASSIGNMENT,
        status: "REVOKED",
        revokedAt: "2026-11-22T09:00:00Z",
        totalAttemptsLeft: 0,
        totalAttemptsRevoked: 5,
      },
      attemptsRevoked: 5,
    })),
    ...overrides,
  };
}

function renderDetail(deps: Partial<ClassesApiDeps>, session: Session = TEST_ADMIN_SESSION) {
  return renderWithProviders(
    <SessionProvider loadSession={async () => session}>
      <ClassDetailView classId={BASE_CLASS.id} deps={deps} />
    </SessionProvider>,
  );
}

describe("ClassExamAssignmentsSection (UNI-2774)", () => {
  it("hien trang thai trong va cho phep giao bai thi dang xuat ban cho lop", async () => {
    const user = userEvent.setup();
    let assignments: ClassExamAssignmentItem[] = [];
    const assignClassExams = vi.fn<ClassesApiDeps["assignClassExams"]>(async (_classId, body) => {
      const created: ClassExamAssignmentItem = {
        ...ACTIVE_ASSIGNMENT,
        attemptsGranted: body.attemptsGranted,
        resultView: body.resultView,
        autoGrantNewMembers: body.autoGrantNewMembers ?? true,
        note: body.note ?? null,
      };
      assignments = [created];
      return { assignments: [created], totalGrantsCreated: 2 };
    });

    const deps = makeDeps({
      listClassExamAssignments: vi.fn(async () => assignments),
      assignClassExams,
    });

    renderDetail(deps);

    expect(await screen.findByText("Chưa có bài thi nào được giao")).toBeInTheDocument();

    const assignButtons = await screen.findAllByRole("button", { name: "Giao bài thi" });
    await user.click(assignButtons[0]!);

    const dialog = await screen.findByRole("dialog", { name: /Giao bài thi cho lớp LOP-HSA-01/ });
    expect(await within(dialog).findByText("Đã có đợt đang hiệu lực")).toBeInTheDocument();

    await user.click(
      within(dialog).getByRole("checkbox", { name: /Đề luyện tập HSA FULL số 01/ }),
    );

    const attemptsInput = within(dialog).getByLabelText(/Số lượt mỗi học viên/);
    await user.clear(attemptsInput);
    await user.type(attemptsInput, "3");

    const noteInput = within(dialog).getByLabelText("Ghi chú đợt giao");
    await user.type(noteInput, "Bài kiểm tra tuần 1");

    await user.click(within(dialog).getByRole("button", { name: "Giao 1 bài thi" }));

    await waitFor(() => {
      expect(assignClassExams).toHaveBeenCalledTimes(1);
    });
    expect(assignClassExams.mock.calls[0]![0]).toBe(BASE_CLASS.id);
    expect(assignClassExams.mock.calls[0]![1]).toEqual({
      examIds: [ASSIGNABLE_EXAMS[0]!.examId],
      attemptsGranted: 3,
      resultView: "FULL_SOLUTION",
      autoGrantNewMembers: true,
      note: "Bài kiểm tra tuần 1",
    });
    expect(typeof assignClassExams.mock.calls[0]![2]).toBe("string");

    const assignmentsTable = await screen.findByRole("table", {
      name: "Danh sách đợt giao bài thi cho lớp",
    });
    expect(
      await within(assignmentsTable).findByText("Đề luyện tập HSA FULL số 01"),
    ).toBeInTheDocument();
    expect(within(assignmentsTable).getByText("2 HV · đã dùng 1, còn 5")).toBeInTheDocument();
  });

  it("cho phep doi autoGrantNewMembers va thu hoi dot giao bai thi", async () => {
    const user = userEvent.setup();
    let current = { ...ACTIVE_ASSIGNMENT };
    const updateClassExamAssignment = vi.fn<ClassesApiDeps["updateClassExamAssignment"]>(
      async (_classId, _assignmentId, body) => {
        current = { ...current, autoGrantNewMembers: body.autoGrantNewMembers };
        return current;
      },
    );
    const revokeClassExamAssignment = vi.fn<ClassesApiDeps["revokeClassExamAssignment"]>(
      async (_classId, _assignmentId, body) => {
        current = {
          ...current,
          status: "REVOKED",
          revokedAt: "2026-11-22T09:00:00Z",
          revokeReason: body?.reason ?? null,
          totalAttemptsLeft: 0,
          totalAttemptsRevoked: 5,
        };
        return { assignment: current, attemptsRevoked: 5 };
      },
    );

    const deps = makeDeps({
      listClassExamAssignments: vi.fn(async () => [current]),
      updateClassExamAssignment,
      revokeClassExamAssignment,
    });

    renderDetail(deps);

    expect(await screen.findByText("1 đợt giao")).toBeInTheDocument();
    expect(await screen.findByRole("button", { name: "Giao bài thi" })).toBeInTheDocument();

    const assignmentsTable = screen.getByRole("table", {
      name: "Danh sách đợt giao bài thi cho lớp",
    });
    expect(within(assignmentsTable).getByText("Đề luyện tập HSA FULL số 01")).toBeInTheDocument();

    const autoGrantCheckbox = within(assignmentsTable).getByRole("checkbox", {
      name: "Tự động cấp thành viên mới cho bài HSA-FULL-01",
    });
    expect(autoGrantCheckbox).toBeChecked();

    await user.click(autoGrantCheckbox);
    await waitFor(() => {
      expect(updateClassExamAssignment).toHaveBeenCalledWith(BASE_CLASS.id, ACTIVE_ASSIGNMENT.id, {
        autoGrantNewMembers: false,
      });
    });

    await user.click(
      within(assignmentsTable).getByRole("button", { name: "Thu hồi đợt giao bài HSA-FULL-01" }),
    );

    const confirmDialog = await screen.findByRole("dialog", { name: "Thu hồi đợt giao bài thi" });
    await user.type(within(confirmDialog).getByLabelText(/Lý do thu hồi/), "Đổi đề tuần 1");
    await user.click(within(confirmDialog).getByRole("button", { name: "Thu hồi đợt giao" }));

    await waitFor(() => {
      expect(revokeClassExamAssignment).toHaveBeenCalledWith(BASE_CLASS.id, ACTIVE_ASSIGNMENT.id, {
        reason: "Đổi đề tuần 1",
      });
    });
    expect(await within(assignmentsTable).findByText("Đã thu hồi")).toBeInTheDocument();
  });
});

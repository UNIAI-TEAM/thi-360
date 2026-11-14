import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import viMessages from "@/messages/vi.json";
import { renderWithProviders } from "@/test/render";
import {
  ObjectPermissionPicker,
  groupPermissionsByObject,
} from "../components/object-permission-picker";
import type { RbacTranslator } from "../messages";
import type { PermissionResponse } from "../types";

const SAMPLE_PERMISSIONS: PermissionResponse[] = [
  {
    id: "p1",
    code: "USER.VIEW",
    bitIndex: 0,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Xem tài khoản",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 5,
  },
  {
    id: "p2",
    code: "USER.MODIFY",
    bitIndex: 1,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Xem và sửa tài khoản",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 5,
  },
  {
    id: "p3",
    code: "USER.ASSIGN",
    bitIndex: 2,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Gán vai trò và quyền",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 1,
  },
  {
    id: "p4",
    code: "USER.LEARNING_VIEW",
    bitIndex: 3,
    groupCode: "USER",
    groupName: "Tài khoản",
    name: "Xem dữ liệu học tập",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 3,
  },
  {
    id: "p5",
    code: "CLASS.VIEW",
    bitIndex: 4,
    groupCode: "CLASS",
    groupName: "Lớp học",
    name: "Xem lớp học",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 3,
  },
  {
    id: "p6",
    code: "CLASS.MODIFY",
    bitIndex: 5,
    groupCode: "CLASS",
    groupName: "Lớp học",
    name: "Xem và sửa lớp học",
    scope: "MANAGE",
    roleCount: 2,
    userCount: 3,
  },
  {
    id: "p7",
    code: "AUDIT.VIEW",
    bitIndex: 11,
    groupCode: "AUDIT",
    groupName: "Nhật ký",
    name: "Xem nhật ký",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 1,
  },
  {
    id: "p8",
    code: "APPEARANCE.MODIFY",
    bitIndex: 12,
    groupCode: "APPEARANCE",
    groupName: "Giao diện",
    name: "Sửa giao diện",
    scope: "MANAGE",
    roleCount: 1,
    userCount: 1,
  },
];

const VI_RBAC_T: RbacTranslator = (key) => {
  const parts = key.split(".");
  let cursor: unknown = viMessages.rbac;
  for (const part of parts) {
    if (cursor && typeof cursor === "object" && part in cursor) {
      cursor = (cursor as Record<string, unknown>)[part];
    } else {
      return key;
    }
  }
  return typeof cursor === "string" ? cursor : key;
};

describe("ObjectPermissionPicker (AC-1)", () => {
  it("chọn mức Xem và sửa cho ra cả VIEW lẫn MODIFY", async () => {
    const user = userEvent.setup();
    const groups = groupPermissionsByObject(SAMPLE_PERMISSIONS, VI_RBAC_T);
    const onChange = vi.fn();

    renderWithProviders(
      <ObjectPermissionPicker
        groups={groups}
        selectedCodes={["CLASS.VIEW"]}
        onChange={onChange}
      />,
    );

    const levelGroup = screen.getByRole("group", { name: "Mức truy cập của Lớp học" });
    const modifyBtn = levelGroup.querySelectorAll("button")[1]!;
    await user.click(modifyBtn);

    expect(onChange).toHaveBeenCalledWith(["CLASS.VIEW", "CLASS.MODIFY"]);
  });

  it("bỏ tick đối tượng thì bỏ cả mức lẫn mọi quyền riêng của đối tượng đó", async () => {
    const user = userEvent.setup();
    const groups = groupPermissionsByObject(SAMPLE_PERMISSIONS, VI_RBAC_T);
    const onChange = vi.fn();

    renderWithProviders(
      <ObjectPermissionPicker
        groups={groups}
        selectedCodes={[
          "USER.VIEW",
          "USER.MODIFY",
          "USER.ASSIGN",
          "USER.LEARNING_VIEW",
          "AUDIT.VIEW",
        ]}
        onChange={onChange}
      />,
    );

    await user.click(screen.getByRole("checkbox", { name: "Cấp đối tượng Tài khoản" }));

    expect(onChange).toHaveBeenCalledWith(["AUDIT.VIEW"]);
  });

  it("khóa toàn bộ danh sách và đưa bộ đếm về 0 khi examLocked=true nhưng vẫn hiện các đối tượng", () => {
    const groups = groupPermissionsByObject(SAMPLE_PERMISSIONS, VI_RBAC_T);
    const onChange = vi.fn();

    renderWithProviders(
      <ObjectPermissionPicker
        groups={groups}
        selectedCodes={["USER.VIEW", "USER.ASSIGN"]}
        onChange={onChange}
        examLocked
      />,
    );

    expect(screen.getByText(/Đang cấp 0 đối tượng · 0 quyền riêng/)).toBeInTheDocument();
    expect(screen.getByText(/Phạm vi Thi không nhận quyền quản trị/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Cấp đối tượng Tài khoản" })).toBeDisabled();
    expect(screen.getByRole("checkbox", { name: "Cấp đối tượng Lớp học" })).toBeDisabled();
    expect(screen.queryByText("USER.VIEW")).not.toBeInTheDocument();
  });

  it("đối tượng chỉ có một mức hiện nhãn cố định và tick cấp đúng quyền", async () => {
    const user = userEvent.setup();
    const groups = groupPermissionsByObject(SAMPLE_PERMISSIONS, VI_RBAC_T);
    const onChange = vi.fn();

    renderWithProviders(
      <ObjectPermissionPicker
        groups={groups}
        selectedCodes={[]}
        onChange={onChange}
      />,
    );

    expect(screen.getByText("Chỉ có mức xem")).toBeInTheDocument();
    expect(screen.getByText("Chỉ có mức sửa")).toBeInTheDocument();

    await user.click(screen.getByRole("checkbox", { name: "Cấp đối tượng Giao diện" }));
    expect(onChange).toHaveBeenCalledWith(["APPEARANCE.MODIFY"]);
  });

  it("giữ nguyên quyền riêng trực tiếp khi quyền xem đến từ vai trò (inheritedCodes) và thêm đối tượng khác", async () => {
    const user = userEvent.setup();
    const groups = groupPermissionsByObject(SAMPLE_PERMISSIONS, VI_RBAC_T);
    const onChange = vi.fn();

    renderWithProviders(
      <ObjectPermissionPicker
        groups={groups}
        selectedCodes={["USER.ASSIGN"]}
        inheritedCodes={["USER.VIEW"]}
        onChange={onChange}
      />,
    );

    expect(screen.getByText(/Đang cấp 0 đối tượng · 1 quyền riêng/)).toBeInTheDocument();
    expect(screen.getByRole("checkbox", { name: "Cấp đối tượng Tài khoản" })).not.toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Gán vai trò và quyền" })).toBeChecked();
    expect(screen.getByRole("checkbox", { name: "Gán vai trò và quyền" })).toBeEnabled();

    await user.click(screen.getByRole("checkbox", { name: "Cấp đối tượng Nhật ký" }));
    expect(onChange).toHaveBeenCalledWith(["USER.ASSIGN", "AUDIT.VIEW"]);
  });
});

CREATE TABLE identity.permission (
    id uuid PRIMARY KEY,
    code text NOT NULL UNIQUE,
    bit_index int NOT NULL UNIQUE CHECK (bit_index >= 0),
    scope smallint NOT NULL CHECK (scope IN (1, 2)),
    name text NOT NULL,
    group_code text NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE identity.role (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    code_normalized text NOT NULL,
    name text NOT NULL,
    description text,
    access smallint NOT NULL CHECK (access IN (1, 2)),
    is_system boolean NOT NULL DEFAULT false,
    status smallint NOT NULL DEFAULT 1 CHECK (status IN (1, 2)),
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT uq_role_code_normalized UNIQUE (code_normalized)
);

CREATE TABLE identity.role_permission (
    role_id uuid NOT NULL REFERENCES identity.role (id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES identity.permission (id),
    granted_by uuid,
    granted_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (role_id, permission_id)
);

CREATE INDEX idx_role_permission_permission_id
    ON identity.role_permission (permission_id);

CREATE TABLE identity.user_role (
    user_id uuid NOT NULL REFERENCES identity.user_account (id) ON DELETE CASCADE,
    role_id uuid NOT NULL REFERENCES identity.role (id),
    assigned_by uuid,
    assigned_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX idx_user_role_role_id
    ON identity.user_role (role_id);

CREATE TABLE identity.user_permission (
    user_id uuid NOT NULL REFERENCES identity.user_account (id) ON DELETE CASCADE,
    permission_id uuid NOT NULL REFERENCES identity.permission (id),
    granted_by uuid,
    granted_at timestamptz NOT NULL DEFAULT now(),
    reason text NOT NULL,
    PRIMARY KEY (user_id, permission_id)
);

CREATE INDEX idx_user_permission_permission_id
    ON identity.user_permission (permission_id);

INSERT INTO identity.role (id, code, code_normalized, name, description, access, is_system, status)
VALUES
    ('00000000-0000-7000-8000-000000000001', 'SYSTEM_ADMIN', 'system_admin', 'Quản trị hệ thống', 'Vai trò quản trị toàn bộ hệ thống', 1, true, 1),
    ('00000000-0000-7000-8000-000000000002', 'CONTENT_ADMIN', 'content_admin', 'Quản trị nội dung', 'Vai trò quản trị danh mục và nội dung', 1, true, 1),
    ('00000000-0000-7000-8000-000000000003', 'OPERATIONS', 'operations', 'Vận hành / CSKH', 'Vai trò vận hành tài khoản và lớp học', 1, true, 1),
    ('00000000-0000-7000-8000-000000000004', 'STUDENT', 'student', 'Học viên', 'Vai trò truy cập cổng học viên và dự thi', 2, true, 1)
ON CONFLICT (code_normalized) DO NOTHING;

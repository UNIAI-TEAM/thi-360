CREATE SCHEMA IF NOT EXISTS classroom;

CREATE TABLE classroom.class_room (
    id uuid PRIMARY KEY,
    code text NOT NULL,
    code_normalized text NOT NULL,
    name text NOT NULL,
    description text,
    owner_user_id uuid,
    status smallint NOT NULL DEFAULT 1 CHECK (status IN (1, 2)),
    deleted_at timestamptz,
    deleted_by uuid,
    delete_reason text,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by uuid,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by uuid,
    version bigint NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX uq_class_room_code_active
    ON classroom.class_room (code_normalized)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_class_room_status_active
    ON classroom.class_room (status)
    WHERE deleted_at IS NULL;

CREATE INDEX idx_class_room_owner_active
    ON classroom.class_room (owner_user_id)
    WHERE deleted_at IS NULL AND owner_user_id IS NOT NULL;

CREATE TABLE classroom.class_member (
    class_id uuid NOT NULL REFERENCES classroom.class_room (id),
    user_id uuid NOT NULL,
    joined_at timestamptz NOT NULL DEFAULT now(),
    added_by uuid,
    PRIMARY KEY (class_id, user_id)
);

CREATE INDEX idx_class_member_user_id
    ON classroom.class_member (user_id);

CREATE TABLE classroom.class_member_history (
    id uuid PRIMARY KEY,
    seq bigserial NOT NULL,
    class_id uuid NOT NULL REFERENCES classroom.class_room (id),
    user_id uuid NOT NULL,
    action smallint NOT NULL CHECK (action IN (1, 2, 3, 4)),
    related_class_id uuid REFERENCES classroom.class_room (id),
    actor_id uuid,
    reason text,
    occurred_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_class_member_history_user_occurred
    ON classroom.class_member_history (user_id, occurred_at DESC, seq DESC);

CREATE INDEX idx_class_member_history_class_occurred
    ON classroom.class_member_history (class_id, occurred_at DESC, seq DESC);

CREATE TABLE audit.audit_event (
    id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    actor_id uuid,
    actor_username text,
    actor_roles text[] NOT NULL DEFAULT ARRAY[]::text[],
    actor_type smallint NOT NULL CHECK (actor_type IN (1, 2, 3)),
    module text NOT NULL,
    action text NOT NULL,
    target_type text,
    target_id text,
    target_label text,
    before jsonb,
    after jsonb,
    reason text,
    outcome smallint NOT NULL CHECK (outcome IN (1, 2)),
    error_code text,
    trace_id text,
    ip text,
    CONSTRAINT pk_audit_event PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE INDEX idx_audit_event_target
    ON audit.audit_event (target_type, target_id, occurred_at DESC);
CREATE INDEX idx_audit_event_actor
    ON audit.audit_event (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_event_action
    ON audit.audit_event (action, occurred_at DESC);
CREATE INDEX idx_audit_event_occurred_at
    ON audit.audit_event (occurred_at DESC);

CREATE TABLE audit.audit_event_archive (
    id uuid NOT NULL,
    occurred_at timestamptz NOT NULL DEFAULT now(),
    actor_id uuid,
    actor_username text,
    actor_roles text[] NOT NULL DEFAULT ARRAY[]::text[],
    actor_type smallint NOT NULL CHECK (actor_type IN (1, 2, 3)),
    module text NOT NULL,
    action text NOT NULL,
    target_type text,
    target_id text,
    target_label text,
    before jsonb,
    after jsonb,
    reason text,
    outcome smallint NOT NULL CHECK (outcome IN (1, 2)),
    error_code text,
    trace_id text,
    ip text,
    CONSTRAINT pk_audit_event_archive PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE INDEX idx_audit_event_archive_target
    ON audit.audit_event_archive (target_type, target_id, occurred_at DESC);
CREATE INDEX idx_audit_event_archive_actor
    ON audit.audit_event_archive (actor_id, occurred_at DESC);
CREATE INDEX idx_audit_event_archive_action
    ON audit.audit_event_archive (action, occurred_at DESC);
CREATE INDEX idx_audit_event_archive_occurred_at
    ON audit.audit_event_archive (occurred_at DESC);

CREATE OR REPLACE FUNCTION audit.prevent_audit_mutation()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
BEGIN
    RAISE EXCEPTION 'audit.% is append-only (% is not allowed)', TG_TABLE_NAME, TG_OP;
END;
$$;

CREATE TRIGGER trg_audit_event_append_only
    BEFORE UPDATE OR DELETE ON audit.audit_event
    FOR EACH ROW
    EXECUTE FUNCTION audit.prevent_audit_mutation();

CREATE TRIGGER trg_audit_event_archive_append_only
    BEFORE UPDATE OR DELETE ON audit.audit_event_archive
    FOR EACH ROW
    EXECUTE FUNCTION audit.prevent_audit_mutation();

DO $$
DECLARE
    utc_now timestamp := CURRENT_TIMESTAMP AT TIME ZONE 'UTC';
    cur_year integer := EXTRACT(YEAR FROM utc_now)::integer;
    cur_quarter integer := EXTRACT(QUARTER FROM utc_now)::integer;
    next_year integer;
    next_quarter integer;
    end_year integer;
    end_month integer;
    q_start timestamptz;
    q_end timestamptz;
    nq_start timestamptz;
    nq_end timestamptz;
    y_start timestamptz;
    y_end timestamptz;
BEGIN
    IF cur_quarter = 4 THEN
        next_year := cur_year + 1;
        next_quarter := 1;
        end_year := cur_year + 1;
        end_month := 1;
    ELSE
        next_year := cur_year;
        next_quarter := cur_quarter + 1;
        end_year := cur_year;
        end_month := cur_quarter * 3 + 1;
    END IF;

    q_start := make_timestamptz(cur_year, (cur_quarter - 1) * 3 + 1, 1, 0, 0, 0, 'UTC');
    q_end := make_timestamptz(end_year, end_month, 1, 0, 0, 0, 'UTC');
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_event FOR VALUES FROM (%L) TO (%L)',
        format('audit_event_%sq%s', cur_year, cur_quarter),
        q_start,
        q_end
    );

    IF next_quarter = 4 THEN
        end_year := next_year + 1;
        end_month := 1;
    ELSE
        end_year := next_year;
        end_month := next_quarter * 3 + 1;
    END IF;
    nq_start := make_timestamptz(next_year, (next_quarter - 1) * 3 + 1, 1, 0, 0, 0, 'UTC');
    nq_end := make_timestamptz(end_year, end_month, 1, 0, 0, 0, 'UTC');
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_event FOR VALUES FROM (%L) TO (%L)',
        format('audit_event_%sq%s', next_year, next_quarter),
        nq_start,
        nq_end
    );

    y_start := make_timestamptz(cur_year, 1, 1, 0, 0, 0, 'UTC');
    y_end := make_timestamptz(cur_year + 1, 1, 1, 0, 0, 0, 'UTC');
    EXECUTE format(
        'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_event_archive FOR VALUES FROM (%L) TO (%L)',
        format('audit_event_archive_%s', cur_year),
        y_start,
        y_end
    );

    IF next_year <> cur_year THEN
        y_start := make_timestamptz(next_year, 1, 1, 0, 0, 0, 'UTC');
        y_end := make_timestamptz(next_year + 1, 1, 1, 0, 0, 0, 'UTC');
        EXECUTE format(
            'CREATE TABLE IF NOT EXISTS audit.%I PARTITION OF audit.audit_event_archive FOR VALUES FROM (%L) TO (%L)',
            format('audit_event_archive_%s', next_year),
            y_start,
            y_end
        );
    END IF;
END;
$$;

DO $$
DECLARE
    app_role text := NULLIF(btrim('${auditAppRole}'), '');
    migrator_role text := current_user;
    granted_temp_maint_set boolean := false;
    had_direct_maint_membership boolean := false;
    orig_inherit_option boolean;
    orig_set_option boolean;
    child_name text;
BEGIN
    IF app_role IS NULL THEN
        RAISE EXCEPTION 'Flyway placeholder auditAppRole must not be blank';
    END IF;
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = app_role) THEN
        RAISE EXCEPTION 'Audit app role "%" does not exist', app_role;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'thi360_audit_maint') THEN
        CREATE ROLE thi360_audit_maint NOLOGIN;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'thi360_audit_maint' AND rolcanlogin) THEN
        ALTER ROLE thi360_audit_maint NOLOGIN;
    END IF;
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'thi360_audit_maint' AND rolcanlogin) THEN
        RAISE EXCEPTION 'thi360_audit_maint must remain NOLOGIN';
    END IF;

    EXECUTE format('GRANT thi360_audit_maint TO %I WITH INHERIT FALSE, SET TRUE', app_role);
    IF NOT pg_has_role(migrator_role, 'thi360_audit_maint', 'SET') THEN
        SELECT m.inherit_option, m.set_option
        INTO orig_inherit_option, orig_set_option
        FROM pg_auth_members m
        JOIN pg_roles r ON r.oid = m.roleid
        JOIN pg_roles u ON u.oid = m.member
        JOIN pg_roles g ON g.oid = m.grantor
        WHERE r.rolname = 'thi360_audit_maint'
          AND u.rolname = migrator_role
          AND g.rolname = migrator_role;

        had_direct_maint_membership := FOUND;

        EXECUTE format('GRANT thi360_audit_maint TO %I WITH INHERIT FALSE, SET TRUE', migrator_role);
        granted_temp_maint_set := true;
    END IF;

    GRANT USAGE, CREATE ON SCHEMA audit TO thi360_audit_maint;
    FOR child_name IN
        SELECT child.relname
        FROM pg_inherits inh
        JOIN pg_class parent ON parent.oid = inh.inhparent
        JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
        JOIN pg_class child ON child.oid = inh.inhrelid
        WHERE parent_ns.nspname = 'audit'
          AND parent.relname IN ('audit_event', 'audit_event_archive')
    LOOP
        EXECUTE format('ALTER TABLE audit.%I OWNER TO thi360_audit_maint', child_name);
    END LOOP;

    ALTER TABLE audit.audit_event OWNER TO thi360_audit_maint;
    ALTER TABLE audit.audit_event_archive OWNER TO thi360_audit_maint;

    ALTER SCHEMA audit OWNER TO thi360_audit_maint;

    SET LOCAL ROLE thi360_audit_maint;

    REVOKE ALL ON SCHEMA audit FROM PUBLIC;
    EXECUTE format('REVOKE ALL ON SCHEMA audit FROM %I', app_role);
    EXECUTE format('GRANT USAGE ON SCHEMA audit TO %I', app_role);

    REVOKE ALL ON TABLE audit.audit_event, audit.audit_event_archive FROM PUBLIC;
    EXECUTE format('REVOKE ALL ON TABLE audit.audit_event, audit.audit_event_archive FROM %I', app_role);
    EXECUTE format('GRANT SELECT, INSERT ON TABLE audit.audit_event, audit.audit_event_archive TO %I', app_role);

    FOR child_name IN
        SELECT child.relname
        FROM pg_inherits inh
        JOIN pg_class parent ON parent.oid = inh.inhparent
        JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
        JOIN pg_class child ON child.oid = inh.inhrelid
        WHERE parent_ns.nspname = 'audit'
          AND parent.relname IN ('audit_event', 'audit_event_archive')
    LOOP
        EXECUTE format('REVOKE ALL ON TABLE audit.%I FROM PUBLIC', child_name);
        EXECUTE format('REVOKE ALL ON TABLE audit.%I FROM %I', child_name, app_role);
        EXECUTE format('GRANT SELECT, INSERT ON TABLE audit.%I TO %I', child_name, app_role);
    END LOOP;

    ALTER DEFAULT PRIVILEGES FOR ROLE thi360_audit_maint IN SCHEMA audit
        REVOKE ALL ON TABLES FROM PUBLIC;
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE thi360_audit_maint IN SCHEMA audit REVOKE ALL ON TABLES FROM %I',
        app_role
    );
    EXECUTE format(
        'ALTER DEFAULT PRIVILEGES FOR ROLE thi360_audit_maint IN SCHEMA audit GRANT SELECT, INSERT ON TABLES TO %I',
        app_role
    );

    IF migrator_role = session_user THEN
        RESET ROLE;
    ELSE
        EXECUTE format('SET LOCAL ROLE %I', migrator_role);
    END IF;

    IF granted_temp_maint_set THEN
        IF had_direct_maint_membership THEN
            EXECUTE format(
                'GRANT thi360_audit_maint TO %I WITH INHERIT %s, SET %s',
                migrator_role,
                CASE WHEN orig_inherit_option THEN 'TRUE' ELSE 'FALSE' END,
                CASE WHEN orig_set_option THEN 'TRUE' ELSE 'FALSE' END
            );
        ELSE
            EXECUTE format('REVOKE thi360_audit_maint FROM %I', migrator_role);
        END IF;
    END IF;
END;
$$;

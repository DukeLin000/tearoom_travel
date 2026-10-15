-- Additive upgrade: V1 is retained unchanged for existing developer databases.
ALTER TABLE app_user
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    ADD CONSTRAINT app_user_display_name_not_blank CHECK (length(trim(display_name)) > 0);

ALTER TABLE trip_member
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0);
UPDATE trip_member SET created_at = joined_at, updated_at = joined_at;

ALTER TABLE trip_day
    ADD COLUMN created_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now();

-- No API or FK references the legacy audit sequence. Preserve every event while
-- upgrading its internal key to UUID, including on an existing V1 database.
ALTER TABLE trip_change_log ALTER COLUMN id DROP IDENTITY;
ALTER TABLE trip_change_log ALTER COLUMN id TYPE uuid USING gen_random_uuid();
ALTER TABLE trip_change_log ALTER COLUMN id SET DEFAULT gen_random_uuid();
ALTER TABLE trip_change_log
    ADD COLUMN updated_at timestamptz NOT NULL DEFAULT now(),
    ADD COLUMN version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    ADD CONSTRAINT history_action_not_blank CHECK (length(trim(action)) > 0);
UPDATE trip_change_log SET updated_at = created_at;

CREATE TABLE trip_invite (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    trip_id uuid NOT NULL REFERENCES trip(id),
    token_hash bytea NOT NULL UNIQUE CHECK (octet_length(token_hash) = 32),
    created_by uuid NOT NULL REFERENCES app_user(id),
    expires_at timestamptz NOT NULL,
    revoked_at timestamptz,
    max_uses integer NOT NULL CHECK (max_uses > 0),
    use_count integer NOT NULL DEFAULT 0 CHECK (use_count >= 0 AND use_count <= max_uses),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (expires_at > created_at),
    CHECK (revoked_at IS NULL OR revoked_at >= created_at),
    FOREIGN KEY (trip_id, created_by) REFERENCES trip_member(trip_id, user_id)
);
CREATE INDEX trip_invite_by_trip ON trip_invite(trip_id, created_at DESC);
CREATE INDEX trip_by_owner ON trip(owner_user_id);

-- Every mutable table records update time even for explicit JDBC writes.
-- Optimistic versions remain explicit in application commands / @Version.
CREATE FUNCTION set_updated_at() RETURNS trigger LANGUAGE plpgsql AS $$
BEGIN
    NEW.updated_at = clock_timestamp();
    RETURN NEW;
END;
$$;
CREATE TRIGGER app_user_updated BEFORE UPDATE ON app_user FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trip_updated BEFORE UPDATE ON trip FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trip_member_updated BEFORE UPDATE ON trip_member FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trip_day_updated BEFORE UPDATE ON trip_day FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER itinerary_item_updated BEFORE UPDATE ON itinerary_item FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE TRIGGER trip_invite_updated BEFORE UPDATE ON trip_invite FOR EACH ROW EXECUTE FUNCTION set_updated_at();

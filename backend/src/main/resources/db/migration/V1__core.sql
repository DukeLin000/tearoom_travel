CREATE TABLE app_user (
    id uuid PRIMARY KEY,
    oidc_issuer varchar(255) NOT NULL,
    oidc_subject varchar(255) NOT NULL,
    display_name varchar(80) NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (oidc_issuer, oidc_subject)
);
CREATE TABLE trip (
    id uuid PRIMARY KEY,
    title varchar(120) NOT NULL CHECK (length(trim(title)) > 0),
    destination varchar(120) NOT NULL CHECK (length(trim(destination)) > 0),
    start_date date NOT NULL,
    end_date date NOT NULL,
    timezone varchar(64) NOT NULL,
    currency char(3) NOT NULL CHECK (currency ~ '^[A-Z]{3}$'),
    budget_limit numeric(14,2) NOT NULL CHECK (budget_limit >= 0),
    owner_user_id uuid NOT NULL REFERENCES app_user(id),
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CHECK (end_date >= start_date AND end_date - start_date < 14)
);
CREATE TABLE trip_member (
    trip_id uuid NOT NULL REFERENCES trip(id),
    user_id uuid NOT NULL REFERENCES app_user(id),
    joined_at timestamptz NOT NULL DEFAULT now(),
    PRIMARY KEY (trip_id, user_id)
);
-- A trip owner must also be a member; checked at transaction commit.
ALTER TABLE trip ADD CONSTRAINT trip_owner_membership
    FOREIGN KEY (id, owner_user_id) REFERENCES trip_member(trip_id, user_id)
    DEFERRABLE INITIALLY DEFERRED;
CREATE TABLE trip_day (
    id uuid PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id),
    local_date date NOT NULL,
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    UNIQUE (trip_id, local_date)
);
CREATE TABLE itinerary_item (
    id uuid PRIMARY KEY,
    day_id uuid NOT NULL REFERENCES trip_day(id),
    title varchar(160) NOT NULL CHECK (length(trim(title)) > 0),
    category varchar(32) NOT NULL CHECK (category IN ('FOOD','TRANSPORT','STAY','ACTIVITY','SHOPPING','OTHER')),
    local_start_time time,
    local_end_time time,
    sort_order integer NOT NULL CHECK (sort_order >= 0),
    location_name varchar(255),
    map_url text,
    estimated_amount numeric(14,2) NOT NULL CHECK (estimated_amount >= 0),
    note text NOT NULL DEFAULT '',
    version bigint NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_by uuid NOT NULL REFERENCES app_user(id),
    updated_by uuid NOT NULL REFERENCES app_user(id),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    deleted_at timestamptz,
    CHECK (local_start_time IS NULL OR local_end_time IS NULL OR local_end_time > local_start_time)
);
CREATE TABLE trip_change_log (
    id bigint GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    trip_id uuid NOT NULL REFERENCES trip(id),
    actor_id uuid NOT NULL REFERENCES app_user(id),
    action varchar(40) NOT NULL,
    entity_id uuid NOT NULL,
    before_data jsonb,
    after_data jsonb,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX trip_member_by_user ON trip_member(user_id, trip_id);
CREATE INDEX itinerary_live_order ON itinerary_item(day_id, sort_order) WHERE deleted_at IS NULL;
CREATE INDEX history_newest ON trip_change_log(trip_id, created_at DESC, id DESC);

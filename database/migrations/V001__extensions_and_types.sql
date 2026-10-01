CREATE EXTENSION IF NOT EXISTS btree_gist;
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TYPE coordinates AS (
    latitude DOUBLE PRECISION,
    longitude DOUBLE PRECISION
);

CREATE TYPE severity_level AS ENUM (
    'low',
    'medium',
    'high',
    'critical'
);

CREATE TYPE disaster_status AS ENUM (
    'active',
    'monitoring',
    'resolved',
    'closed'
);

CREATE TYPE shelter_status AS ENUM (
    'operational',
    'full',
    'closed'
);

-- Phase 1: Initial schema setup
-- btree_gist extension is required for EXCLUDE constraints on reservation time ranges (Phase 4)
CREATE EXTENSION IF NOT EXISTS btree_gist;

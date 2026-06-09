-- Baseline demo rows (Postgres profile / Flyway only). Schema: V1__create_items.sql
INSERT INTO items (name, created_at) VALUES
  ('Demo widget', NOW() - INTERVAL '2 days'),
  ('Sample gadget', NOW() - INTERVAL '1 day'),
  ('Exercise item', NOW());

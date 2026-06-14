-- BCrypt password hash for dashboard/API user registration (nullable for Kafka-created users).
ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);

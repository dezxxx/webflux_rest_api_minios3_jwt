-- Base schema: users, files, events.
-- Deviates from the spec DDL in three places, all of them required by the
-- functional requirements themselves:
--   1. users.password_hash + users.role  -- JWT auth and ADMIN/MODERATOR/USER
--   2. files.user_id                     -- "USER reads only his own data"
--   3. deleted_at on every table         -- soft delete
CREATE TABLE users (
    id            INT PRIMARY KEY AUTO_INCREMENT,
    username      VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'USER',
    status        VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at    TIMESTAMP    NULL DEFAULT NULL,
    CONSTRAINT uq_users_username UNIQUE (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE files (
    id         INT PRIMARY KEY AUTO_INCREMENT,
    name       VARCHAR(255) NOT NULL,
    location   VARCHAR(500) NOT NULL,
    status     VARCHAR(50)  NOT NULL DEFAULT 'ACTIVE',
    user_id    INT          NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP    NULL DEFAULT NULL,
    CONSTRAINT fk_files_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE events (
    id         INT PRIMARY KEY AUTO_INCREMENT,
    user_id    INT          NOT NULL,
    file_id    INT          NOT NULL,
    status     VARCHAR(50)  NOT NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP    NULL DEFAULT NULL,
    CONSTRAINT fk_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_events_file FOREIGN KEY (file_id) REFERENCES files (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE INDEX idx_files_user      ON files (user_id);
CREATE INDEX idx_events_user     ON events (user_id);
CREATE INDEX idx_events_file     ON events (file_id);
CREATE INDEX idx_users_deleted   ON users (deleted_at);
CREATE INDEX idx_files_deleted   ON files (deleted_at);
CREATE INDEX idx_events_deleted  ON events (deleted_at);
-- Freesky 初始表结构（Flyway V1）
-- 所有外键采用 ON DELETE CASCADE，确保删除父记录时不产生无主的章节/角色/日志/用量记录。

CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    username      VARCHAR(64)  NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE novel_project (
    id                  BIGINT       NOT NULL AUTO_INCREMENT,
    user_id             BIGINT       NOT NULL,
    idea                TEXT         NOT NULL,
    title               VARCHAR(255) NULL,
    status              VARCHAR(20)  NOT NULL COMMENT 'CREATING / COMPLETED / FAILED',
    review_round        INT          NOT NULL DEFAULT 0,
    world_settings_json MEDIUMTEXT   NULL,
    plot_outline_json   MEDIUMTEXT   NULL,
    error_message       TEXT         NULL,
    created_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_novel_project_user (user_id),
    CONSTRAINT fk_novel_project_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE chapter (
    id               BIGINT     NOT NULL AUTO_INCREMENT,
    novel_project_id BIGINT     NOT NULL,
    chapter_index    INT        NOT NULL,
    content          MEDIUMTEXT NOT NULL,
    created_at       DATETIME   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_chapter_project (novel_project_id, chapter_index),
    CONSTRAINT fk_chapter_project FOREIGN KEY (novel_project_id) REFERENCES novel_project (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE character_profile (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    novel_project_id  BIGINT       NOT NULL,
    name              VARCHAR(255) NOT NULL,
    role_type         VARCHAR(50)  NULL,
    appearance        TEXT         NULL,
    personality       TEXT         NULL,
    background        TEXT         NULL,
    ability           TEXT         NULL,
    motivation        TEXT         NULL,
    relationships_json TEXT        NULL,
    created_at        DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_character_project (novel_project_id),
    CONSTRAINT fk_character_project FOREIGN KEY (novel_project_id) REFERENCES novel_project (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE run_log (
    id               BIGINT       NOT NULL AUTO_INCREMENT,
    novel_project_id BIGINT       NOT NULL,
    sequence         INT          NOT NULL,
    agent            VARCHAR(100) NULL,
    message          TEXT         NOT NULL,
    created_at       DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_run_log_project_seq (novel_project_id, sequence),
    CONSTRAINT fk_run_log_project FOREIGN KEY (novel_project_id) REFERENCES novel_project (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE token_usage (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    novel_project_id BIGINT        NOT NULL,
    input_tokens     BIGINT        NOT NULL DEFAULT 0,
    output_tokens    BIGINT        NOT NULL DEFAULT 0,
    total_tokens     BIGINT        NOT NULL DEFAULT 0,
    call_count       BIGINT        NOT NULL DEFAULT 0,
    cost_yuan        DECIMAL(12, 4) NOT NULL DEFAULT 0,
    model            VARCHAR(80)   NOT NULL DEFAULT '',
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_token_usage_project (novel_project_id),
    CONSTRAINT fk_token_usage_project FOREIGN KEY (novel_project_id) REFERENCES novel_project (id) ON DELETE CASCADE
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

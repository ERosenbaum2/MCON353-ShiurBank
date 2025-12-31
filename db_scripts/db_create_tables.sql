-- Schema for Shiur / Users system (MySQL)

CREATE DATABASE IF NOT EXISTS shiurbank_db;
USE shiurbank_db;

CREATE TABLE users (
    user_id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(100) NOT NULL UNIQUE,
    hashed_pwd      VARCHAR(255) NOT NULL,
    title           VARCHAR(50) NOT NULL,
    fname           VARCHAR(100) NOT NULL,
    lname           VARCHAR(100) NOT NULL,
    email           VARCHAR(255) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE topics (
    topic_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(200) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE institutions (
    inst_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(255) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE rebbeim (
    rebbi_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    title       VARCHAR(50) NOT NULL,
    fname       VARCHAR(100) NOT NULL,
    lname       VARCHAR(100) NOT NULL,
    user_id     BIGINT NULL,
    CONSTRAINT fk_rebbi_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE SET NULL
) ENGINE=InnoDB;

CREATE TABLE shiur_series (
    series_id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rebbi_id               BIGINT NOT NULL,
    topic_id               BIGINT NOT NULL,
    requires_permission    BOOLEAN NOT NULL DEFAULT FALSE,
    inst_id                BIGINT NOT NULL,
    description            TEXT NULL,
    CONSTRAINT fk_series_rebbi FOREIGN KEY (rebbi_id) REFERENCES rebbeim(rebbi_id) ON DELETE RESTRICT,
    CONSTRAINT fk_series_topic FOREIGN KEY (topic_id) REFERENCES topics(topic_id) ON DELETE RESTRICT,
    CONSTRAINT fk_series_inst FOREIGN KEY (inst_id) REFERENCES institutions(inst_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE gabbaim (
    gabbai_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    series_id   BIGINT NOT NULL,
    CONSTRAINT fk_gabbai_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_gabbai_series FOREIGN KEY (series_id) REFERENCES shiur_series(series_id) ON DELETE CASCADE,
    CONSTRAINT uq_gabbai UNIQUE (user_id, series_id)
) ENGINE=InnoDB;

CREATE TABLE shiur_recordings (
    recording_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    series_id      BIGINT NOT NULL,
    s3_file_path   TEXT NOT NULL,
    title          VARCHAR(255) NOT NULL,
    recorded_at    DATETIME NOT NULL,
    keyword_1      VARCHAR(100) NOT NULL,
    keyword_2      VARCHAR(100) NOT NULL,
    keyword_3      VARCHAR(100) NOT NULL,
    keyword_4      VARCHAR(100) NOT NULL,
    keyword_5      VARCHAR(100) NOT NULL,
    keyword_6      VARCHAR(100) NOT NULL,
    description    TEXT NULL,
    CONSTRAINT fk_recording_series FOREIGN KEY (series_id) REFERENCES shiur_series(series_id) ON DELETE CASCADE
) ENGINE=InnoDB;

CREATE TABLE shiur_participants (
    participant_id   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id          BIGINT NOT NULL,
    series_id        BIGINT NOT NULL,
    CONSTRAINT fk_participant_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_participant_series FOREIGN KEY (series_id) REFERENCES shiur_series(series_id) ON DELETE CASCADE,
    CONSTRAINT uq_participant UNIQUE (user_id, series_id)
) ENGINE=InnoDB;

CREATE TABLE user_institution_assoc (
    assoc_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    inst_id     BIGINT NOT NULL,
    CONSTRAINT fk_uia_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_uia_inst FOREIGN KEY (inst_id) REFERENCES institutions(inst_id) ON DELETE RESTRICT,
    CONSTRAINT uq_user_inst UNIQUE (user_id, inst_id)
) ENGINE=InnoDB;

CREATE TABLE rebbi_institution_assoc (
    assoc_id    BIGINT AUTO_INCREMENT PRIMARY KEY,
    rebbi_id    BIGINT NOT NULL,
    inst_id     BIGINT NOT NULL,
    CONSTRAINT fk_ria_rebbi FOREIGN KEY (rebbi_id) REFERENCES rebbeim(rebbi_id) ON DELETE CASCADE,
    CONSTRAINT fk_ria_inst FOREIGN KEY (inst_id) REFERENCES institutions(inst_id) ON DELETE RESTRICT,
    CONSTRAINT uq_rebbi_inst UNIQUE (rebbi_id, inst_id)
) ENGINE=InnoDB;

CREATE TABLE subscriber_types (
    type_id     BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE
) ENGINE=InnoDB;

CREATE TABLE subscribers (
    subscriber_id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT NOT NULL,
    series_id            BIGINT NOT NULL,
    subscription_type_id BIGINT NOT NULL,
    CONSTRAINT fk_sub_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_series FOREIGN KEY (series_id) REFERENCES shiur_series(series_id) ON DELETE CASCADE,
    CONSTRAINT fk_sub_type FOREIGN KEY (subscription_type_id) REFERENCES subscriber_types(type_id) ON DELETE RESTRICT,
    CONSTRAINT uq_subscriber UNIQUE (user_id, series_id, subscription_type_id)
) ENGINE=InnoDB;

CREATE TABLE favorite_shiurim (
    favorite_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id     BIGINT NOT NULL,
    series_id   BIGINT NOT NULL,
    CONSTRAINT fk_fav_user FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE,
    CONSTRAINT fk_fav_series FOREIGN KEY (series_id) REFERENCES shiur_series(series_id) ON DELETE CASCADE,
    CONSTRAINT uq_favorite UNIQUE (user_id, series_id)
) ENGINE=InnoDB;

CREATE TABLE pending_permission (
    pending_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    series_id  BIGINT NOT NULL,

    CONSTRAINT uq_pending_series
        UNIQUE (series_id),

    CONSTRAINT fk_pending_series
        FOREIGN KEY (series_id)
        REFERENCES shiur_series(series_id)
        ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE admins (
    admin_id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    UNIQUE (user_id),
    CONSTRAINT fk_admins_users
        FOREIGN KEY (user_id)
        REFERENCES users(user_id)
        ON DELETE CASCADE
        ON UPDATE CASCADE
) ENGINE=InnoDB;
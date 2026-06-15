-- 성능 측정 전용 DB 스키마 (jipsamoye_loadtest)
-- 측정 DB에는 앱을 띄우지 않으므로 JPA(ddl-auto)가 테이블을 만들지 않는다.
-- LOAD DATA 에 필요한 users / pet_post 두 테이블만 직접 생성한다.
--
-- 컬럼/제약은 다음 엔티티에서 도출:
--   domain/user/entity/User.java
--   domain/petPost/entity/PetPost.java
--   global/entity/BaseEntity.java (created_at / updated_at)
--
-- 적용:
--   docker exec -i jipsamoye-db-loadtest \
--     mysql -uroot -ploadtest jipsamoye_loadtest < loadtest/schema.sql

SET FOREIGN_KEY_CHECKS = 0;
DROP TABLE IF EXISTS pet_post;
DROP TABLE IF EXISTS users;
SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE users (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    deleted_at        DATETIME(6)  NULL,
    nickname          VARCHAR(10)  NOT NULL,
    bio               TEXT         NULL,
    profile_image_url VARCHAR(255) NULL,
    cover_image_url   VARCHAR(255) NULL,
    social_links      JSON         NULL,
    email             VARCHAR(255) NOT NULL,
    provider          VARCHAR(20)  NOT NULL,
    provider_id       VARCHAR(255) NOT NULL,
    role              VARCHAR(20)  NOT NULL,
    created_at        DATETIME(6)  NOT NULL,
    updated_at        DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_nickname (nickname)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE pet_post (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    deleted_at    DATETIME(6)  NULL,
    user_id       BIGINT       NOT NULL,
    title         VARCHAR(100) NOT NULL,
    content       TEXT         NULL,
    image_urls    JSON         NOT NULL,
    like_count    INT          NOT NULL DEFAULT 0,
    comment_count INT          NOT NULL DEFAULT 0,
    created_at    DATETIME(6)  NOT NULL,
    updated_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    KEY idx_pet_post_created_like (created_at, like_count),
    CONSTRAINT fk_pet_post_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

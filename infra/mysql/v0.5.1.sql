/**
 * v0.5.1: authentication/profile split and managed image URLs.
 * Run against v0.5.0 during a write maintenance window, after taking a backup.
 * MySQL DDL implicitly commits; this script is NOT an all-or-nothing transaction.
 * Reruns preserve existing profiles and image records, including later edits.
 * Set @image_public_base_url to the same para.file.managed.public-base-url used
 * by the deployment (the URL serving the existing avatar storage directory):
 *   SET @image_public_base_url = 'http://localhost:8080/bbs/i/';
 * Use the MySQL client without --force so validation failures stop the script.
 */
USE `para_bbs`;
SET @image_public_base_url = COALESCE(@image_public_base_url, 'https://nobeta.cn/bbs/i/');

CREATE TABLE IF NOT EXISTS `user_profile` (
    `user_id` BIGINT NOT NULL COMMENT '用户主键，与认证表共享ID',
    `nickname` VARCHAR(50) NOT NULL COMMENT '用户昵称',
    `avatar_url` VARCHAR(2048) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完整头像URL',
    `sex` TINYINT NOT NULL DEFAULT 2 COMMENT '性别 0:女,1:男,2:未设置',
    `race` VARCHAR(50) NOT NULL DEFAULT '' COMMENT '种族',
    `signature` VARCHAR(255) NOT NULL DEFAULT '' COMMENT '个性签名',
    `background_image_url` VARCHAR(2048) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完整资料背景图URL',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '资料创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '资料更新时间',
    PRIMARY KEY (`user_id`),
    UNIQUE KEY `uk_nickname` (`nickname`),
    CONSTRAINT `fk_user_profile_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`),
    CONSTRAINT `ck_user_profile_sex` CHECK (`sex` IN (0, 1, 2))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户公开资料表';

CREATE TABLE IF NOT EXISTS `image_file` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '文件主键',
    `storage_key` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT '根目录下year/month/day/uuid.extension，不按用途分目录',
    `public_url` VARCHAR(2048) COLLATE utf8mb4_bin NOT NULL COMMENT '完整公开URL',
    `user_id` BIGINT DEFAULT NULL COMMENT '上传者，历史无主记录可空',
    `purpose` VARCHAR(16) NOT NULL COMMENT 'AVATAR/BACKGROUND/COVER',
    `expire_time` DATETIME NOT NULL COMMENT '到期后仅清理无有效引用的文件',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_storage_key` (`storage_key`),
    KEY `idx_public_url` (`public_url`(191)),
    KEY `idx_expire_time` (`expire_time`),
    KEY `idx_user_id` (`user_id`),
    CONSTRAINT `ck_image_purpose` CHECK (`purpose` IN ('AVATAR', 'BACKGROUND', 'COVER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='头像、背景与封面文件生命周期';

DROP PROCEDURE IF EXISTS migrate_v051;
DELIMITER $$
CREATE PROCEDURE migrate_v051()
BEGIN
    DECLARE legacy_columns INT DEFAULT 0;
    DECLARE missing_rows BIGINT DEFAULT 0;
    IF @image_public_base_url IS NULL
       OR @image_public_base_url NOT REGEXP '^https?://[^/]+/.*/$'
       OR CHAR_LENGTH(@image_public_base_url) > 1700
       OR @image_public_base_url REGEXP '[?#]' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid image_public_base_url; use the static avatar directory URL ending in /';
    END IF;

    SELECT COUNT(*) INTO legacy_columns FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'sys_user'
      AND COLUMN_NAME IN ('nickname', 'avatar', 'sex', 'race', 'signature');
    IF legacy_columns NOT IN (0, 5) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Partial legacy sys_user schema; restore or repair before migration';
    END IF;
    IF legacy_columns = 5 THEN
        INSERT INTO user_profile (user_id, nickname, avatar_url, sex, race, signature, create_time, update_time)
        SELECT u.id, u.nickname,
            CASE
                WHEN u.avatar IS NULL OR LOWER(TRIM(u.avatar)) IN
                    ('', 'null', 'undefined', 'default_avator.jpg', 'default_avator',
                     'default_avatar.png', 'default_avatar', 'src/assets/images/default-avatar.png') THEN NULL
                WHEN TRIM(u.avatar) REGEXP '^https?://' THEN TRIM(u.avatar)
                ELSE CONCAT(@image_public_base_url,
                    CASE WHEN TRIM(LEADING '/' FROM TRIM(u.avatar)) LIKE 'avatar/%'
                        THEN SUBSTRING(TRIM(LEADING '/' FROM TRIM(u.avatar)), 8)
                        ELSE TRIM(LEADING '/' FROM TRIM(u.avatar)) END)
            END,
            u.sex, u.race, u.signature, u.create_time, u.update_time
        FROM sys_user u LEFT JOIN user_profile p ON p.user_id = u.id
        WHERE p.user_id IS NULL;
    END IF;

    SELECT COUNT(*) INTO missing_rows FROM sys_user u LEFT JOIN user_profile p ON p.user_id = u.id
    WHERE p.user_id IS NULL;
    IF missing_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing profiles; legacy fields will not be removed';
    END IF;
    SELECT COUNT(*) INTO missing_rows FROM user_profile p LEFT JOIN sys_user u ON u.id = p.user_id
    WHERE u.id IS NULL;
    IF missing_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Orphan profiles; migration stopped';
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'blog' AND COLUMN_NAME = 'cover_url') THEN
        ALTER TABLE blog ADD COLUMN cover_url VARCHAR(2048) COLLATE utf8mb4_bin DEFAULT NULL
            COMMENT '完整博客封面URL' AFTER summary;
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.TABLES
        WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'avatar_info') THEN
        -- Reference state now comes from business tables, not is_referenced flags.
        -- Keep storage keys intact: they are filesystem identities, not display URLs.
        INSERT INTO image_file (storage_key, public_url, user_id, purpose, expire_time)
        SELECT a.file_uuid, CONCAT(@image_public_base_url, a.file_uuid), a.user_id, 'AVATAR',
               COALESCE(a.expire_time, DATE_ADD(NOW(), INTERVAL 2 HOUR))
        FROM avatar_info a LEFT JOIN image_file f ON f.storage_key = a.file_uuid
        WHERE f.id IS NULL;
        SELECT COUNT(*) INTO missing_rows FROM avatar_info a
        LEFT JOIN image_file f ON f.storage_key = a.file_uuid WHERE f.id IS NULL;
        IF missing_rows > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Missing image records; avatar_info will not be removed';
        END IF;
        DROP TABLE avatar_info;
    END IF;

    IF legacy_columns = 5 THEN
        ALTER TABLE sys_user DROP COLUMN nickname, DROP COLUMN avatar, DROP COLUMN sex,
            DROP COLUMN race, DROP COLUMN signature;
    END IF;
END$$
DELIMITER ;
CALL migrate_v051();
DROP PROCEDURE migrate_v051;

-- Deploy v0.5.1 front/backend together, invalidate old serialized auth caches,
-- and rebuild/refresh affected search documents before resuming writes.

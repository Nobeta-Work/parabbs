/**
 * name: v0.5.2 Migration SQL
 * db: para_bbs, para_auth
 * description: v0.5.2 migration sql.
 * precondition: Based after the established of database para_auth, and tables creation sql executed (auth_schema.sql -v v0.5.2).
 */

-- 在停写窗口备份后执行；切换后的 BBS 必须通过 OIDC 登录，不再读取 sys_user.password。
-- MySQL DDL 隐式提交；账号和映射的数据迁移使用事务，整个脚本并非原子操作。
-- 使用不带 --force 的 MySQL 客户端执行，错误时停止，不继续执行后续语句。
-- 可在执行前设置 @auth_issuer，必须与部署时 AUTH_ISSUER 完全一致。
SET @auth_issuer = COALESCE(@auth_issuer, 'https://nobeta.cn/auth');
USE `para_bbs`;

CREATE TABLE IF NOT EXISTS `user_identity` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '身份关联主键',
    `user_id` BIGINT NOT NULL COMMENT 'BBS 业务用户主键',
    `issuer` VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL COMMENT 'OIDC 签发者，与 iss 完全一致',
    `subject` VARCHAR(255) COLLATE utf8mb4_bin NOT NULL COMMENT 'OIDC 用户标识，与 sub 完全一致',
    `create_time` DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) COMMENT '关联创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_identity_issuer_subject` (`issuer`, `subject`),
    UNIQUE KEY `uk_identity_user_issuer` (`user_id`, `issuer`),
    CONSTRAINT `fk_identity_user` FOREIGN KEY (`user_id`) REFERENCES `sys_user` (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin COMMENT='BBS 用户与认证身份的关联';

DROP PROCEDURE IF EXISTS `migrate_v052`;
DELIMITER $$
CREATE PROCEDURE `migrate_v052`()
BEGIN
    DECLARE legacy_password INT DEFAULT 0;
    DECLARE invalid_rows BIGINT DEFAULT 0;
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        DROP TEMPORARY TABLE IF EXISTS `v052_accounts`;
        RESIGNAL;
    END;

    IF @auth_issuer IS NULL OR CHAR_LENGTH(@auth_issuer) > 255
       OR @auth_issuer NOT REGEXP '^https?://[^/]+(/[^?#]*)?$'
       OR @auth_issuer REGEXP '[[:space:]@]'
       OR OCTET_LENGTH(@auth_issuer) <> CHAR_LENGTH(@auth_issuer) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid auth_issuer; use the exact ASCII AUTH_ISSUER URL';
    END IF;

    -- 不因用户名相同推断两套账号属于同一人。
    -- 已有显式映射可以重复执行；未映射但用户名冲突的账号需要先人工处理。
    SELECT COUNT(*) INTO invalid_rows
    FROM `sys_user` u
    JOIN `para_auth`.`auth_account` a
      ON CONVERT(a.username USING utf8mb4) COLLATE utf8mb4_unicode_ci = u.username COLLATE utf8mb4_unicode_ci
    LEFT JOIN `user_identity` i ON i.user_id = u.id AND i.issuer = @auth_issuer
    WHERE i.id IS NULL;
    IF invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Unmapped BBS username already exists in Auth; explicitly resolve identity ownership first';
    END IF;

    SELECT COUNT(*) INTO invalid_rows
    FROM `user_identity` i
    LEFT JOIN `para_auth`.`auth_account` a ON a.subject = i.subject
    WHERE i.issuer = @auth_issuer AND a.id IS NULL;
    IF invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Existing BBS identity refers to a missing Auth account';
    END IF;

    -- 与原 BBS 的用户名规则保持一致，兼容 Unicode 和不区分大小写的账号查询。
    SELECT COUNT(*) INTO invalid_rows FROM (
        SELECT CONVERT(username USING utf8mb4) COLLATE utf8mb4_unicode_ci AS login_name
        FROM `para_auth`.`auth_account`
        GROUP BY login_name HAVING COUNT(*) > 1
    ) conflicts;
    IF invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Auth usernames collide under the existing BBS username collation';
    END IF;

    SELECT COUNT(*) INTO legacy_password FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = 'para_bbs' AND TABLE_NAME = 'sys_user' AND COLUMN_NAME = 'password';

    SELECT COUNT(*) INTO invalid_rows FROM `sys_user` u
    LEFT JOIN `user_identity` i ON i.user_id = u.id AND i.issuer = @auth_issuer
    WHERE i.id IS NULL;
    IF legacy_password = 0 AND invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'BBS password column is missing but some users have no Auth identity';
    END IF;

    ALTER TABLE `para_auth`.`auth_account`
        MODIFY COLUMN `username` VARCHAR(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL;

    DROP TEMPORARY TABLE IF EXISTS `v052_accounts`;
    CREATE TEMPORARY TABLE `v052_accounts` (
        `user_id` BIGINT NOT NULL PRIMARY KEY,
        `subject` CHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
        `username` VARCHAR(64) COLLATE utf8mb4_unicode_ci NOT NULL,
        `password_hash` VARCHAR(255) NOT NULL,
        `status` TINYINT NOT NULL,
        `create_time` DATETIME(6) NOT NULL,
        `update_time` DATETIME(6) NOT NULL,
        UNIQUE KEY `uk_migration_subject` (`subject`),
        UNIQUE KEY `uk_migration_username` (`username`)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

    START TRANSACTION;
    IF legacy_password = 1 THEN
        -- 仅首次迁移生成 subject；重跑不覆盖 Auth 后续的改密、改名或状态变化。
        INSERT INTO `v052_accounts` (user_id, subject, username, password_hash, status, create_time, update_time)
        SELECT u.id, UUID(), u.username, u.password, u.status, u.create_time, u.update_time
        FROM `sys_user` u
        LEFT JOIN `user_identity` i ON i.user_id = u.id AND i.issuer = @auth_issuer
        WHERE i.id IS NULL;

        SELECT COUNT(*) INTO invalid_rows FROM `v052_accounts`
        WHERE password_hash = '' OR status NOT IN (0, 1);
        IF invalid_rows > 0 THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Invalid legacy password hash or account status';
        END IF;

        -- 保留原密码哈希，由 Auth 的 MigratingPasswordEncoder 登录时兼容及升级。
        -- 不自动解除旧封禁：status=0 暂按账号停用迁入，范围调整由管理员明确处理。
        INSERT INTO `para_auth`.`auth_account` (subject, username, password_hash, status, create_time, update_time)
        SELECT subject, username, password_hash, status, create_time, update_time FROM `v052_accounts`;

        INSERT INTO `user_identity` (user_id, issuer, subject)
        SELECT user_id, @auth_issuer, subject FROM `v052_accounts`;
    END IF;

    SELECT COUNT(*) INTO invalid_rows FROM `sys_user` u
    LEFT JOIN `user_identity` i ON i.user_id = u.id AND i.issuer = @auth_issuer
    LEFT JOIN `para_auth`.`auth_account` a ON a.subject = i.subject
    WHERE i.id IS NULL OR a.id IS NULL;
    IF invalid_rows > 0 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Incomplete account migration; BBS passwords will not be removed';
    END IF;
    COMMIT;
    DROP TEMPORARY TABLE `v052_accounts`;

    -- 数据完整迁入后再删除旧凭据；DDL 失败时可重跑，不重新生成身份或覆盖密码。
    IF legacy_password = 1 THEN
        ALTER TABLE `sys_user` DROP COLUMN `password`;
    END IF;
    -- username 保留用于兼容展示，status 表示 BBS 业务状态；认证凭据只由 Auth 管理。
    ALTER TABLE `sys_user`
        MODIFY COLUMN `username` VARCHAR(64) NOT NULL COMMENT '历史账号名，认证账号由 Auth 管理',
        MODIFY COLUMN `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'BBS 业务状态 0:封禁,1:正常';
END$$
DELIMITER ;
CALL `migrate_v052`();
DROP PROCEDURE `migrate_v052`;

-- BBS 社区角色不迁为 AUTH_ADMIN；认证中心管理员应由 Auth 单独配置。
-- 保留 BBS 用户 ID、资料、角色及内容关系；Auth/OIDC 协议表继续使用 auth_schema.sql。
-- 切换账号入口后清理旧 BBS 登录缓存和凭据，避免继续使用旧密码登录链路。

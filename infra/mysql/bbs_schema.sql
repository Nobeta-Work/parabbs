/**
 * name: Para BBS Schema SQL
 * db: para_bbs
 * version: v0.5.1
 * description: Full schema of para_bbs. Including initial role, user, and permissions.
 */

CREATE DATABASE IF NOT EXISTS `para_bbs`
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_unicode_ci;

USE `para_bbs`;

CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    `username` VARCHAR(64) NOT NULL COMMENT '登录账号',
    `password` VARCHAR(255) NOT NULL COMMENT '密码哈希',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 0:封禁,1:正常',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账号创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '认证信息更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户认证表';

CREATE TABLE `user_profile` (
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

CREATE TABLE `sys_role` (
    `id` BIGINT NOT NULL COMMENT '角色主键',
    `role_code` VARCHAR(20) NOT NULL COMMENT '角色唯一编码',
    `role_name` VARCHAR(20) NOT NULL COMMENT '角色名',
    `description` VARCHAR(255) DEFAULT NULL COMMENT '角色描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_code` (`role_code`),
    UNIQUE KEY `uk_role_name` (`role_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统角色表';

CREATE TABLE `sys_perm` (
    `id` BIGINT NOT NULL COMMENT '权限主键',
    `perm_code` VARCHAR(64) NOT NULL COMMENT '权限唯一编码',
    `perm_name` VARCHAR(50) NOT NULL COMMENT '权限名',
    `description` VARCHAR(200) DEFAULT NULL COMMENT '权限描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_perm_code` (`perm_code`),
    UNIQUE KEY `uk_perm_name` (`perm_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统权限表';

CREATE TABLE `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `user_id` BIGINT NOT NULL COMMENT '用户主键',
    `role_id` BIGINT NOT NULL COMMENT '角色主键',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
    KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关系表';

CREATE TABLE `sys_role_perm` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `role_id` BIGINT NOT NULL COMMENT '角色主键',
    `perm_id` BIGINT NOT NULL COMMENT '权限主键',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_role_perm` (`role_id`, `perm_id`),
    KEY `idx_perm_id` (`perm_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关系表';

CREATE TABLE `folder` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '目录主键',
    `author_id` BIGINT NOT NULL COMMENT '作者主键',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父目录主键，0 为根目录',
    `name` VARCHAR(50) NOT NULL COMMENT '目录名',
    `path` VARCHAR(255) NOT NULL COMMENT '目录路径',
    `level` TINYINT NOT NULL COMMENT '目录层级',
    `sort_order` INT NOT NULL DEFAULT 0 COMMENT '排序值',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_author_parent_name` (`author_id`, `parent_id`, `name`),
    KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博客目录表';

CREATE TABLE `blog` (
    `id` BIGINT NOT NULL COMMENT '博客主键（应用层雪花 ID）',
    `author_id` BIGINT NOT NULL COMMENT '作者主键',
    `folder_id` BIGINT NOT NULL DEFAULT 0 COMMENT '目录主键，0 为根目录',
    `is_published` TINYINT NOT NULL DEFAULT 0 COMMENT '是否公开',
    `title` VARCHAR(255) NOT NULL COMMENT '标题',
    `summary` VARCHAR(500) NOT NULL DEFAULT '' COMMENT '摘要',
    `cover_url` VARCHAR(2048) COLLATE utf8mb4_bin DEFAULT NULL COMMENT '完整博客封面URL',
    `content` MEDIUMTEXT NOT NULL COMMENT '正文',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `comments_count` INT NOT NULL DEFAULT 0 COMMENT '评论数',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_author_folder_title` (`author_id`, `folder_id`, `title`),
    KEY `idx_author_id` (`author_id`),
    KEY `idx_folder_id` (`folder_id`),
    KEY `idx_published_create_time` (`is_published`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博客表';

CREATE TABLE `comment` (
    `id` BIGINT NOT NULL COMMENT '评论主键（应用层雪花 ID）',
    `blog_id` BIGINT NOT NULL COMMENT '所属博客主键',
    `user_id` BIGINT NOT NULL COMMENT '评论用户主键',
    `parent_id` BIGINT NOT NULL DEFAULT 0 COMMENT '父评论主键，0 为顶级评论',
    `root_id` BIGINT NOT NULL COMMENT '根评论主键，顶级评论为自身 ID',
    `content` TEXT NOT NULL COMMENT '评论内容',
    `like_count` INT NOT NULL DEFAULT 0 COMMENT '点赞数',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态 1:正常,0:草稿,-1:已删除,-2:隐藏',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    KEY `idx_blog_parent_create` (`blog_id`, `parent_id`, `create_time`),
    KEY `idx_blog_root_create` (`blog_id`, `root_id`, `create_time`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博客评论表';

CREATE TABLE `outbox_event` (
    `id` BIGINT NOT NULL COMMENT '事件主键',
    `event_type` VARCHAR(64) NOT NULL COMMENT '事件类型',
    `aggregate_type` VARCHAR(32) DEFAULT NULL COMMENT '聚合类型',
    `aggregate_id` BIGINT DEFAULT NULL COMMENT '业务实体主键',
    `payload` JSON NOT NULL COMMENT '事件数据',
    `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态 0:待发送,1:发送中,2:已发送,3:失败',
    `retry_count` INT NOT NULL DEFAULT 0 COMMENT '重试次数',
    `next_retry_time` DATETIME DEFAULT NULL COMMENT '下次可发布时间',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `published_time` DATETIME DEFAULT NULL COMMENT '发布时间',
    PRIMARY KEY (`id`),
    KEY `idx_outbox_pending` (`status`, `next_retry_time`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件发件箱';

CREATE TABLE `inbox_event` (
    `consumer_group` VARCHAR(64) NOT NULL COMMENT '消费组',
    `event_id` BIGINT NOT NULL COMMENT '事件主键',
    `consumed_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '消费时间',
    PRIMARY KEY (`consumer_group`, `event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='领域事件收件箱';

CREATE TABLE `tag` (
    `id` BIGINT NOT NULL COMMENT '标签主键（应用层雪花 ID）',
    `name` VARCHAR(20) NOT NULL COMMENT '标签名',
    `description` VARCHAR(200) NOT NULL DEFAULT '' COMMENT '标签描述',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签表';

CREATE TABLE `blog_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `blog_id` BIGINT NOT NULL COMMENT '博客主键',
    `tag_id` BIGINT NOT NULL COMMENT '标签主键',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_blog_tag` (`blog_id`, `tag_id`),
    KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博客标签关系表';

CREATE TABLE `like_blog` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `blog_id` BIGINT NOT NULL COMMENT '博客主键',
    `user_id` BIGINT NOT NULL COMMENT '用户主键',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_blog_user` (`blog_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='博客点赞记录表';

CREATE TABLE `like_comment` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键',
    `comment_id` BIGINT NOT NULL COMMENT '评论主键',
    `user_id` BIGINT NOT NULL COMMENT '用户主键',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_comment_user` (`comment_id`, `user_id`),
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='评论点赞记录表';

CREATE TABLE `image_file` (
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

CREATE TABLE `agent_token` (
    `token` BIGINT NOT NULL COMMENT '主键、业务键',
    `user_id` BIGINT NOT NULL COMMENT '所属用户',
    `name` VARCHAR(50) NOT NULL COMMENT '命名',
    `expire` INT NOT NULL COMMENT '有效时长，单位"天"，-1 为永久',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` BOOLEAN NOT NULL DEFAULT FALSE COMMENT '是否删除',
    PRIMARY KEY (`token`),
    UNIQUE KEY `uk_user_name` (`user_id`, `name`) COMMENT '同一用户下 Token 名唯一',
    KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Agent Token 表';

INSERT INTO `sys_role` (`id`, `role_code`, `role_name`, `description`) VALUES
    (1, 'USER', '普通用户', '默认注册用户'),
    (2, 'ADMIN', '管理员', '系统管理员'),
    (3, 'AGENT', 'Agent', '用户 Agent');

INSERT INTO `sys_perm` (`id`, `perm_code`, `perm_name`, `description`) VALUES
    (1, 'user:edit_profile', '编辑个人资料', '修改自己的个人资料'),
    (2, 'blog:manage_self', '管理个人博客', '创建、编辑、删除自己的博客'),
    (3, 'folder:manage_self', '管理个人目录', '创建、移动、删除自己的目录'),
    (4, 'like:blog', '博客点赞', '点赞或取消点赞博客'),
    (5, 'user:ban', '封禁用户', '管理员封禁用户'),
    (6, 'admin:manage', '系统管理', '管理员系统管理权限');

INSERT INTO `sys_role_perm` (`role_id`, `perm_id`) VALUES
    (1, 1), (1, 2), (1, 3), (1, 4),
    (2, 1), (2, 2), (2, 3), (2, 4), (2, 5), (2, 6);

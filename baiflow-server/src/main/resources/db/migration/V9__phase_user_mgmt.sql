-- Phase: User Management Enhancement — avatar, denormalized owner fields

ALTER TABLE `user`
    ADD COLUMN avatar_url VARCHAR(512) NOT NULL DEFAULT '' COMMENT '头像访问 URL（nginx 静态文件链接）';

ALTER TABLE `download_task`
    ADD COLUMN owner_username      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者用户名快照',
    ADD COLUMN owner_display_name  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建者展示名快照';

ALTER TABLE `share_link`
    ADD COLUMN owner_username      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建者用户名快照',
    ADD COLUMN owner_display_name  VARCHAR(128) NOT NULL DEFAULT '' COMMENT '创建者展示名快照';

-- 本机 MySQL 8.x 初始化脚本（需要 root 或有建库权限的账号执行）
-- 用法：mysql -uroot -p < scripts/init-local-db.sql
CREATE DATABASE IF NOT EXISTS campus_selection
    DEFAULT CHARACTER SET utf8mb4
    DEFAULT COLLATE utf8mb4_0900_ai_ci;

CREATE USER IF NOT EXISTS 'campus'@'localhost' IDENTIFIED BY 'campus123';
CREATE USER IF NOT EXISTS 'campus'@'%' IDENTIFIED BY 'campus123';
GRANT ALL PRIVILEGES ON campus_selection.* TO 'campus'@'localhost';
GRANT ALL PRIVILEGES ON campus_selection.* TO 'campus'@'%';
FLUSH PRIVILEGES;

-- 表结构与演示课程由应用启动时的 Flyway 自动创建，不需要手工建表。

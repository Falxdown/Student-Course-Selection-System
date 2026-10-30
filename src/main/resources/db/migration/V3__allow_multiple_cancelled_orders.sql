-- 取消订单时 active 置为 NULL，而不是 0。
-- MySQL 唯一索引允许多个 NULL，因此同一学生同一课程可以取消多次、
-- 再重新选择，同时仍然只允许存在一个 active=1 的有效订单。
ALTER TABLE selection_order
    MODIFY COLUMN active TINYINT NULL DEFAULT 1 COMMENT '1 有效，NULL 已取消；NULL 可重复';

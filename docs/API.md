# API 说明

- Base URL：`http://localhost:8080/api/v1`（Docker 部署经 Nginx 时为 `https://localhost/api/v1`）
- 鉴权：`Authorization: Bearer <accessToken>`
- JSON：`Content-Type: application/json; charset=UTF-8`
- 时间：`yyyy-MM-ddTHH:mm:ss`（服务器时区 `Asia/Shanghai`）

## 统一响应

```json
{
  "code": 0,
  "message": "成功",
  "data": {},
  "traceId": "3f5e...",
  "timestamp": 1790000000000
}
```

分页 `data`：

```json
{
  "records": [],
  "total": 12,
  "page": 1,
  "size": 10,
  "pages": 2
}
```

## 错误码

| code | HTTP | 含义 |
| --- | --- | --- |
| `40001` | 400 | 参数不合法 |
| `40101` | 401 | 未登录/Token 失效 |
| `40301` | 403 | 无权限 |
| `40401` | 404 | 资源不存在 |
| `40901` | 409 | 通用冲突 |
| `40902` | 409 | 已选择该课程 |
| `40903` | 409 | 名额已抢完 |
| `40904` | 409 | 不在选课时间内 |
| `40905` | 409 | 课程未发布/关闭 |
| `40906` | 409 | 当前订单不可取消 |
| `40907` | 409 | 幂等键冲突 |
| `42901` | 429 | 触发限流 |
| `50001` | 500 | 服务器内部错误 |
| `50301` | 503 | 依赖服务不可用 |

---

## 1. 登录

`POST /auth/login` · 公开

```json
{
  "studentNo": "20260001",
  "password": "Student@123"
}
```

```json
{
  "code": 0,
  "message": "成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzUxMiJ9...",
    "refreshToken": "eyJhbGciOiJIUzUxMiJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "student": {
      "id": 1,
      "studentNo": "20260001",
      "name": "刘同学",
      "role": "STUDENT"
    }
  }
}
```

失败统一提示“学号或密码错误”，不区分账号不存在与密码错误。

## 2. 刷新 Token

`POST /auth/refresh` · 公开

```json
{ "refreshToken": "eyJhbGciOiJIUzUxMiJ9..." }
```

返回结构同登录，并轮换 refresh token。refresh token 不能用于访问业务接口。

## 3. 当前用户

`GET /auth/me` · 登录

```json
{ "id": 1, "studentNo": "20260001", "name": "刘同学", "role": "STUDENT" }
```

---

## 4. 课程列表

`GET /courses?page=1&size=10&keyword=网络&category=计算机&sort=hot` · 登录

| 参数 | 默认 | 说明 |
| --- | --- | --- |
| `page` | 1 | 页码 |
| `size` | 10 | 每页 1-100 |
| `keyword` | - | 课程名/教师/编号模糊匹配 |
| `category` | - | 精确类别 |
| `sort` | hot | `hot` 按已选人数降序，其他值按开课时间升序 |

```json
{
  "records": [
    {
      "id": 2,
      "courseCode": "CS102",
      "name": "计算机网络",
      "teacher": "李老师",
      "category": "计算机",
      "credits": 3.0,
      "capacity": 80,
      "selectedCount": 63,
      "remainingStock": 17,
      "selectStartTime": "2026-10-29T10:00:00",
      "selectEndTime": "2026-11-29T10:00:00",
      "status": "PUBLISHED",
      "description": "TCP/IP 协议栈……",
      "selectionOpen": true
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10,
  "pages": 1
}
```

`remainingStock` 来自 Redis；缓存缺失时自动回源数据库并重建。

## 5. 热门课程

`GET /courses/hot?limit=6` · 登录

`limit` 1-20，结果缓存 30 秒，课程变更时主动失效。返回课程数组。

## 6. 课程详情

`GET /courses/{courseId}` · 登录

返回单个课程对象，字段同列表。

## 7. 实时库存

`GET /courses/{courseId}/stock` · 登录

```json
{
  "courseId": 2,
  "capacity": 80,
  "selectedCount": 63,
  "remainingStock": 17,
  "selectionOpen": true,
  "serverTime": "2026-10-30T10:20:31"
}
```

---

## 8. 抢选课程

`POST /selections` · 学生 · **必须带 `Idempotency-Key`**

请求头：

```text
Idempotency-Key: 6f1d8c2a9b7e4d3f8a1c5e9b2d7f4a60
```

规则：8-64 位，只允许字母、数字、`_`、`-`。同一次抢选的重试必须复用同一个 key；新请求使用新 key。

```json
{ "courseId": 2 }
```

HTTP 202：

```json
{
  "requestId": "9f2c4e7a8b1d4c6e9a3f5b7d1c8e2a40",
  "status": "PENDING",
  "orderNo": null,
  "message": "抢选请求已受理，请轮询任务状态"
}
```

语义：

- 首次请求：Redis Lua 原子扣减成功后才创建任务并投递 MQ。
- 相同 `Idempotency-Key` 重试：返回已有任务，不重复扣库存。
- 同一学生同一课程已有有效订单：`40902`。
- 已售罄：`40903`。
- 不在选课时间：`40904`。
- 限流：`42901`。

## 9. 查询抢选任务

`GET /selections/tasks/{requestId}` · 本人/管理员

```json
{
  "requestId": "9f2c4e7a8b1d4c6e9a3f5b7d1c8e2a40",
  "courseId": 2,
  "status": "SUCCESS",
  "orderNo": "CS20261030102030123ABC456",
  "failReason": null,
  "createdAt": "2026-10-30T10:20:30",
  "updatedAt": "2026-10-30T10:20:31"
}
```

状态：`PENDING` → `PROCESSING` → `SUCCESS` / `FAILED`。

## 10. 我的订单

`GET /selections/orders?page=1&size=10&status=CONFIRMED` · 本人

`status` 可选 `CONFIRMED` / `CANCELLED`。

```json
{
  "records": [
    {
      "orderNo": "CS20261030102030123ABC456",
      "requestId": "9f2c4e7a8b1d4c6e9a3f5b7d1c8e2a40",
      "courseId": 2,
      "courseCode": "CS102",
      "courseName": "计算机网络",
      "status": "CONFIRMED",
      "createdAt": "2026-10-30T10:20:31",
      "cancelledAt": null
    }
  ],
  "total": 1,
  "page": 1,
  "size": 10,
  "pages": 1
}
```

## 11. 订单详情

`GET /selections/orders/{orderNo}` · 本人

返回单个订单对象。

## 12. 取消选课

`DELETE /selections/orders/{orderNo}` · 本人

```json
{
  "orderNo": "CS20261030102030123ABC456",
  "status": "CANCELLED",
  "cancelledAt": "2026-10-30T10:22:10"
}
```

- 只有 `CONFIRMED` 且选课未结束的订单可取消。
- 数据库事务提交后，通过 Lua 幂等归还 Redis 名额。
- 取消后 `active=NULL`，同一课程可以重新选择；MySQL 唯一索引允许多个 `NULL`，所以可反复取消/重选。

---

## 13. 新建课程

`POST /admin/courses` · 管理员

```json
{
  "courseCode": "CS401",
  "name": "云原生架构",
  "teacher": "张老师",
  "category": "计算机",
  "credits": 3.0,
  "capacity": 60,
  "selectStartTime": "2026-11-01T09:00:00",
  "selectEndTime": "2026-11-15T18:00:00",
  "status": "PUBLISHED",
  "description": "容器、服务网格与可观测性"
}
```

- `status`：`DRAFT` / `PUBLISHED` / `CLOSED`
- 课程编号唯一
- 发布状态下会同步 Redis 库存
- HTTP 201

## 14. 编辑课程

`PUT /admin/courses/{courseId}` · 管理员

请求体同新建。使用 MyBatis-Plus `@Version` 乐观锁；并发修改时返回 `40901`。容量不能小于当前已选人数。

## 15. 运营统计

`GET /admin/selections/statistics` · 管理员

```json
{
  "publishedCourses": 12,
  "totalOrders": 320,
  "confirmedOrders": 300,
  "cancelledOrders": 20,
  "todayOrders": 180,
  "totalTasks": 340,
  "pendingTasks": 1,
  "successTasks": 320,
  "failedTasks": 19,
  "taskSuccessRate": 94.12
}
```

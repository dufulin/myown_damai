# 大麦票务管理系统功能说明

## 项目概览

当前项目是一个前后端分离的大麦票务管理系统雏形，已完成用户服务基础能力，并接入 MySQL、Redis、Nacos 和 Vue 前端。

后端服务地址：

```text
http://127.0.0.1:8080
```

前端页面地址：

```text
http://127.0.0.1:5173
```

## 已实现功能

### 用户服务

1. 用户注册，手机号必填，邮箱可选。
2. 用户可通过手机号或邮箱登录。
3. 获取当前登录用户信息。
4. 用户退出登录。
5. 登录 token 生成、存储和吊销。
6. 密码使用 BCrypt 加密存储。
7. 请求参数校验。
8. 统一业务异常返回。
9. SLF4J 接口和业务关键日志。

### 前端页面

1. 注册表单：姓名、手机号、邮箱、密码。
2. 登录表单：手机号/邮箱、密码。
3. 当前用户信息展示。
4. 退出登录按钮。
5. 接口调用状态提示。
6. 浏览器本地保存 token，并在刷新后尝试恢复登录态。

## 后端接口

统一前缀：

```text
/api/users
```

### 注册

```http
POST /api/users/register
```

请求体：

```json
{
  "name": "测试用户",
  "mobile": "18800000000",
  "email": "test@example.com",
  "password": "123456"
}
```

成功后返回用户信息和 Bearer token。

### 登录

```http
POST /api/users/login
```

请求体：

```json
{
  "login": "18800000000",
  "password": "123456"
}
```

`login` 可以填写手机号或邮箱。成功后返回用户信息和 Bearer token。

### 获取当前用户

```http
GET /api/users/me
```

请求头：

```text
Authorization: Bearer <token>
```

### 退出登录

```http
POST /api/users/logout
```

请求头：

```text
Authorization: Bearer <token>
```

退出后 token 会被吊销，再访问当前用户接口会返回未授权。

## 数据库表

表结构位于：

```text
src/main/resources/schema.sql
```

当前项目参考 `cloud/damai_user_0.sql`，在单库单表环境下使用以下表：

### d_user

用户主表，保存用户基础信息。

主要字段：

1. `id`：用户 ID。
2. `name`：用户展示名称。
3. `rel_name`：实名姓名。
4. `mobile`：手机号。
5. `password`：加密后的密码。
6. `email`：邮箱地址。
7. `email_status`：邮箱认证状态。
8. `rel_authentication_status`：实名认证状态。
9. `status`：用户状态。
10. `create_time`、`edit_time`：创建和更新时间。

### d_user_mobile

用户手机号索引表，用于手机号登录。

### d_user_email

用户邮箱索引表，用于邮箱登录。

### d_ticket_user

购票人表，保存未来购票流程需要的实名购票人信息，通过 `user_id` 关联用户。

### d_user_sessions

用户登录会话表，保存 token 哈希、过期时间和吊销时间，通过 `user_id` 关联 `d_user`。

## Redis 缓存策略

Redis 用于减少用户和 token 查询对数据库的压力。

当前策略：

1. 手机号查询缓存用户 ID。
2. 邮箱查询缓存用户 ID。
3. token 查询缓存 session ID。
4. 查询不存在的数据时写入短 TTL 空值，降低缓存穿透风险。
5. 缓存 TTL 会增加随机抖动，降低集中失效风险。
6. Redis 异常时自动回退数据库，不影响主流程。

默认 Redis 配置：

```yaml
host: localhost
port: 6379
password: 123
database: 0
```

## 技术栈

### 后端

1. Java 17
2. Spring Boot 3.2.5
3. Spring MVC
4. Spring Validation
5. MyBatis
6. MySQL 5.7
7. Redis 6.0.8
8. Nacos 2.2.3
9. Spring Cloud Alibaba Nacos Discovery
10. H2 测试数据库
11. JUnit + MockMvc 接口测试

### 前端

1. Vue 3
2. Vite 5
3. 原生 Fetch API
4. CSS 响应式布局

## 启动方式

### 启动依赖服务

```powershell
docker compose up -d mysql redis nacos
```

### 启动后端

```powershell
mvn -s maven-settings.xml spring-boot:run
```

### 启动前端

```powershell
cd frontend
npm install
npm run dev
```

## 测试与构建

### 后端测试

```powershell
mvn -s maven-settings.xml test
```

当前测试覆盖：

1. 注册、邮箱登录、获取当前用户、退出登录完整流程。
2. 重复手机号注册。
3. 错误密码登录。

### 前端构建

```powershell
cd frontend
npm run build
```

## 后续可扩展方向

1. 购票人增删改查接口。
2. 演出管理。
3. 场次管理。
4. 票档管理。
5. 库存管理。
6. 下单与支付。
7. 订单查询。
8. 后台管理员登录。
9. 接口鉴权拦截器。
10. Redis 分布式锁或库存扣减能力。

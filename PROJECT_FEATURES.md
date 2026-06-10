# 大麦票务管理系统功能说明

## 项目概览

当前项目是一个前后端分离的大麦票务管理系统雏形，已完成用户服务的基础功能，并接入 MySQL、Redis、Nacos 和 Vue 前端。

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

已实现用户相关基础能力：

1. 用户注册
2. 用户登录
3. 获取当前登录用户信息
4. 用户退出登录
5. 登录 token 生成、存储和吊销
6. 密码 BCrypt 加密存储
7. 请求参数校验
8. 统一业务异常返回

### 前端页面

Vue 前端已实现基础操作界面：

1. 注册表单
2. 登录表单
3. 当前用户信息展示
4. 退出登录按钮
5. 接口调用状态提示
6. 浏览器本地保存 token，并在刷新后尝试恢复登录态

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
  "username": "test001",
  "password": "123456",
  "nickname": "测试用户",
  "phone": "18800000000"
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
  "username": "test001",
  "password": "123456"
}
```

成功后返回用户信息和 Bearer token。

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

## 数据库表

表结构位于：

```text
src/main/resources/schema.sql
```

### user_accounts

用户账号表，保存用户基础信息。

主要字段：

1. `id`：用户 ID
2. `username`：用户名，唯一
3. `password_hash`：加密后的密码
4. `nickname`：昵称
5. `phone`：手机号
6. `status`：用户状态
7. `created_at`：创建时间
8. `updated_at`：更新时间

### user_sessions

用户登录会话表，保存 token 状态。

主要字段：

1. `id`：会话 ID
2. `token_hash`：token 哈希值，唯一
3. `user_id`：用户 ID
4. `created_at`：创建时间
5. `expires_at`：过期时间
6. `revoked_at`：吊销时间

## Redis 缓存策略

Redis 用于减少用户和 token 查询对数据库的压力。

当前策略：

1. 用户名查询会缓存用户 ID
2. token 查询会缓存 session ID
3. 查询不存在的数据时写入短 TTL 空值，降低缓存穿透风险
4. 缓存 TTL 会增加随机抖动，降低集中失效风险
5. Redis 异常时自动回退数据库，不影响主流程

默认 Redis 配置：

```yaml
host: localhost
port: 6379
password: 123
database: 0
```

## Nacos 注册中心

项目已引入 Nacos Discovery，启动后会注册到 Nacos。

默认配置：

```yaml
server-addr: localhost:8848
username: nacos
password: nacos
group: DEFAULT_GROUP
service: myown-damai
```

测试环境中已关闭 Nacos 注册，避免单元测试依赖外部服务。

## Docker 服务

项目提供了 `docker-compose.yml`，包含：

1. MySQL 5.7
2. Redis 6.0.8
3. Nacos 2.2.3

启动命令：

```powershell
docker compose up -d mysql redis nacos
```

停止命令：

```powershell
docker compose down
```

## 启动方式

### 启动后端

确保 MySQL、Redis、Nacos 已启动后，运行：

```powershell
mvn spring-boot:run
```

如果使用项目本地 Maven settings：

```powershell
mvn -s maven-settings.xml spring-boot:run
```

### 启动前端

进入前端目录：

```powershell
cd frontend
```

安装依赖：

```powershell
npm install
```

启动开发服务器：

```powershell
npm run dev
```

访问：

```text
http://127.0.0.1:5173
```

## 测试与构建

### 后端测试

```powershell
mvn -s maven-settings.xml test
```

当前测试覆盖：

1. 注册、登录、获取当前用户、退出登录完整流程
2. 重复用户名注册
3. 错误密码登录

### 前端构建

```powershell
cd frontend
npm run build
```

## 主要目录说明

```text
src/main/java/com/myown/damai
```

后端 Java 源码。

```text
src/main/resources/mapper
```

MyBatis XML SQL 文件。

```text
src/main/resources/schema.sql
```

数据库建表脚本。

```text
frontend
```

Vue 前端项目。

```text
docker-compose.yml
```

本地 MySQL、Redis、Nacos 容器配置。

## 后续可扩展方向

1. 演出管理
2. 场次管理
3. 票档管理
4. 库存管理
5. 下单与支付
6. 订单查询
7. 后台管理员登录
8. 前端路由和菜单
9. 接口鉴权拦截器
10. Redis 分布式锁或库存扣减能力

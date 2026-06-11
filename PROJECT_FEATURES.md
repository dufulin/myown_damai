# 大麦票务管理系统功能说明

## 项目概览

当前项目是一个前后端分离的大麦票务管理系统雏形，已完成用户服务和节目服务的基础能力，并接入 MySQL、Redis、Nacos 和 Vue 前端。

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
7. Redis 缓存手机号、邮箱、token 查询结果，并用空值缓存降低缓存穿透风险。
8. SLF4J 接口和业务关键日志。

### 节目服务

1. 节目类型创建和查询。
2. 节目创建，包含节目分组、演出时间、票档信息。
3. 节目列表查询，支持关键词和类型过滤。
4. 节目详情查询，返回基础信息、演出时间和票档。
5. 座位批量初始化。
6. 座位列表查询。

### 前端页面

1. 注册表单：姓名、手机号、邮箱、密码。
2. 登录表单：手机号/邮箱、密码。
3. 当前用户信息展示。
4. 退出登录按钮。
5. 接口调用状态提示。
6. 浏览器本地保存 token，并在刷新后尝试恢复登录态。

## 后端接口

### 用户接口

统一前缀：

```text
/api/users
```

注册：

```http
POST /api/users/register
```

```json
{
  "name": "测试用户",
  "mobile": "18800000000",
  "email": "test@example.com",
  "password": "123456"
}
```

登录：

```http
POST /api/users/login
```

```json
{
  "login": "18800000000",
  "password": "123456"
}
```

`login` 可以填写手机号或邮箱。

获取当前用户：

```http
GET /api/users/me
Authorization: Bearer <token>
```

退出登录：

```http
POST /api/users/logout
Authorization: Bearer <token>
```

### 节目接口

统一前缀：

```text
/api/programs
```

创建类型：

```http
POST /api/programs/categories
```

```json
{
  "parentId": 0,
  "name": "演唱会",
  "type": 1
}
```

查询类型：

```http
GET /api/programs/categories
```

创建节目：

```http
POST /api/programs
```

```json
{
  "areaId": 110000,
  "programCategoryId": 2,
  "parentProgramCategoryId": 1,
  "title": "测试演唱会",
  "actor": "测试歌手",
  "place": "测试场馆",
  "detail": "节目详情",
  "permitChooseSeat": 1,
  "showTimes": [
    {
      "showTime": "2026-08-01T12:00:00Z"
    }
  ],
  "ticketCategories": [
    {
      "introduce": "VIP",
      "price": 680,
      "totalNumber": 20
    }
  ]
}
```

节目列表：

```http
GET /api/programs?keyword=测试&categoryId=2&pageNumber=1&pageSize=20
```

节目详情：

```http
GET /api/programs/{programId}
```

批量初始化座位：

```http
POST /api/programs/{programId}/seats
```

```json
{
  "ticketCategoryId": 1,
  "startRow": 1,
  "endRow": 2,
  "startCol": 1,
  "endCol": 3,
  "seatType": 1
}
```

查询座位：

```http
GET /api/programs/{programId}/seats
```

## 数据库表

表结构位于：

```text
src/main/resources/schema.sql
```

### 用户域

参考 `cloud/damai_user_0.sql`，单库版本包含：

1. `d_user`：用户主表。
2. `d_user_mobile`：手机号索引表。
3. `d_user_email`：邮箱索引表。
4. `d_ticket_user`：购票人表。
5. `d_user_sessions`：登录会话表。

### 节目域

参考 `cloud/damai_program_0.sql`，单库版本包含：

1. `d_program`：节目主表。
2. `d_program_group`：节目分组表。
3. `d_program_category`：节目类型表。
4. `d_program_show_time`：节目演出时间表。
5. `d_ticket_category`：节目票档表。
6. `d_seat`：座位表。

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

启动依赖服务：

```powershell
docker compose up -d mysql redis nacos
```

启动后端：

```powershell
mvn -s maven-settings.xml spring-boot:run
```

启动前端：

```powershell
cd frontend
npm install
npm run dev
```

## 测试与构建

后端测试：

```powershell
mvn -s maven-settings.xml test
```

当前测试覆盖：

1. 用户注册、邮箱登录、获取当前用户、退出登录完整流程。
2. 重复手机号注册。
3. 错误密码登录。
4. 节目类型创建。
5. 节目创建、列表、详情。
6. 座位批量初始化和查询。

前端构建：

```powershell
cd frontend
npm run build
```

## 后续可扩展方向

1. 购票人增删改查接口。
2. 节目更新和上下架。
3. 票档库存扣减。
4. 座位锁定和释放。
5. 下单与支付。
6. 订单查询。
7. 后台管理员登录。
8. 接口鉴权拦截器。
9. Redis 分布式锁或库存扣减能力。

# 大麦票务管理系统功能说明

## 项目概览

当前项目是一个前后端分离的单库版大麦票务管理系统，后端已按 Spring Cloud 拆分为网关、用户、节目、订单和公共模块，前端使用 Vue 3 + Vite 实现基础业务页面。

统一前端访问入口：

```text
http://127.0.0.1:8080
```

前端开发地址：

```text
http://127.0.0.1:5173
```

## 服务模块

1. `damai-common`：公共响应对象、业务异常和全局异常处理。
2. `damai-gateway-service`：网关服务，默认端口 `8080`。
3. `damai-user-service`：用户服务，默认端口 `8081`。
4. `damai-program-service`：节目服务，默认端口 `8082`。
5. `damai-order-service`：订单服务，默认端口 `8083`。
6. `damai-pay-service`：支付服务，默认端口 `8084`。
7. `frontend`：Vue 前端项目。

## 已实现功能

### 网关服务

1. 统一转发 `/api/users/**`、`/api/programs/**`、`/api/orders/**`。
2. 除登录、注册和预检请求外，其他接口需要携带登录 token。
3. 通过用户服务校验当前登录态。
4. 支持同一 IP 的基础访问频率限制。
5. 接入 Nacos 服务发现，使用服务名进行路由。
6. 统一转发 `/api/pay/**` 到支付服务。
7. 支付宝异步通知 `/api/pay/alipay/notify` 放行登录校验，保留后续真实支付宝回调能力。

### 用户服务

1. 用户注册，手机号必填，邮箱可选。
2. 用户可以通过手机号或邮箱登录。
3. 获取当前登录用户信息。
4. 用户退出登录。
5. 登录 token 生成、存储和吊销。
6. 密码使用 BCrypt 加密存储。
7. Redis 缓存手机号、邮箱和 token 查询结果，并使用空值缓存降低缓存穿透风险。
8. 接口和关键业务逻辑已添加 SLF4J 日志。

### 节目服务

1. 节目类型创建和查询。
2. 节目创建，包含节目分组、演出时间和票档信息。
3. 节目列表查询，支持关键词、类型、地区和分页筛选。
4. 节目详情查询，返回基础信息、节目介绍、演出时间、票档和购票须知。
5. 座位批量初始化。
6. 座位列表查询。
7. 节目详情按节目 ID 优先查询 Redis，缓存未命中时查询数据库并回写 Redis。

### 订单服务

1. 根据节目、演出时间、票档和购票人明细创建未支付订单。
2. 根据订单号查询订单详情。
3. 按用户分页查询订单列表。
4. 支持用户取消未支付订单。
5. 支持超时未支付订单自动取消，默认每 60 秒扫描一次。
6. 支持手动触发超时订单取消，方便测试和运维。
7. 参考 `cloud/damai_order_0.sql` 的分表结构，设计了单库版 `d_order` 和 `d_order_ticket_user`。
8. 接口已添加必要调用日志。
9. 支持支付服务确认后将未支付订单更新为已支付。

### 支付服务

1. 参考 `cloud/damai_pay_0.sql` 的分表结构，设计了单库版 `d_pay_bill` 和 `d_refund_bill`。
2. 支持创建支付宝支付账单。
3. 当前本地模式不跳转支付宝，创建支付账单后直接模拟支付成功。
4. 模拟支付成功后更新支付账单状态为已支付。
5. 模拟支付成功后调用订单服务，将订单状态更新为已支付。
6. 保留支付宝异步通知处理能力，后续真实接入时可继续扩展。
7. 支持支付宝参数配置化，包括 `app-id`、商户私钥、支付宝公钥、回调地址和返回地址。

### 前端页面

1. 登录页面：支持手机号或邮箱登录。
2. 注册页面：支持姓名、手机号、邮箱和密码。
3. 登录后进入首页，默认加载 10 条节目。
4. 首页包含搜索框。
5. 首页包含按类型和按地区筛选的分类菜单。
6. 点击分类或搜索后弹出节目列表，并支持分页。
7. 点击节目进入节目详情页。
8. 节目详情页展示节目介绍、演出时间、地点、票档和购票须知。
9. 节目详情页包含“下单”功能，可选择演出时间、票档和数量创建订单。
10. 顶部提供个人中心入口。
11. 个人中心展示当前用户信息和订单列表。
12. 个人中心支持刷新订单和取消未支付订单。
13. 个人中心支持添加、刷新、删除购票人。
14. 节目详情下单时支持选择购票人。
15. 未支付订单支持点击“支付宝支付”，当前会模拟支付成功并刷新订单状态。
16. 浏览器本地保存 token，并在刷新后尝试恢复登录态。

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
GET /api/programs?keyword=测试&categoryId=2&areaId=110000&pageNumber=1&pageSize=20
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

### 订单接口

统一前缀：

```text
/api/orders
```

创建订单：

```http
POST /api/orders
Authorization: Bearer <token>
```

```json
{
  "programId": 1,
  "programItemPicture": "https://example.com/poster.jpg",
  "userId": 1,
  "programTitle": "测试演唱会",
  "programPlace": "测试场馆",
  "programShowTime": "2026-08-01T12:00:00Z",
  "programPermitChooseSeat": 0,
  "distributionMode": "电子票",
  "takeTicketMode": "线上取票",
  "payOrderType": 1,
  "ticketUsers": [
    {
      "ticketUserId": 1,
      "seatId": null,
      "seatInfo": "未选择座位",
      "ticketCategoryId": 1,
      "orderPrice": 680
    }
  ]
}
```

查询订单详情：

```http
GET /api/orders/{orderNumber}
Authorization: Bearer <token>
```

分页查询当前用户订单：

```http
GET /api/orders?userId=1&pageNumber=1&pageSize=8
Authorization: Bearer <token>
```

取消订单：

```http
POST /api/orders/{orderNumber}/cancel
Authorization: Bearer <token>
```

```json
{
  "reason": "用户主动取消"
}
```

手动触发超时未支付取消：

```http
POST /api/orders/timeout-cancel
Authorization: Bearer <token>
```

### 支付接口

统一前缀：

```text
/api/pay
```

创建支付宝支付账单并模拟支付成功：

```http
POST /api/pay/alipay/page-pay
Authorization: Bearer <token>
```

```json
{
  "orderNumber": 1781600000000001,
  "userId": 1
}
```

接口返回支付账单信息，当前不会跳转支付宝，会直接将支付账单和订单状态更新为已支付。

支付宝异步通知：

```http
POST /api/pay/alipay/notify
Content-Type: application/x-www-form-urlencoded
```

该接口保留给后续真实支付宝服务器回调，网关不要求登录 token。支付成功通知会更新 `d_pay_bill`，并调用订单服务把订单状态更新为已支付。

## 数据库表

### 用户库表

参考 `cloud/damai_user_0.sql`，单库版本位于：

```text
damai-user-service/src/main/resources/schema.sql
```

包含：

1. `d_user`：用户主表。
2. `d_user_mobile`：用户手机号表。
3. `d_user_email`：用户邮箱表。
4. `d_ticket_user`：购票人表。
5. `d_user_sessions`：登录会话表。

### 节目库表

参考 `cloud/damai_program_0.sql`，单库版本位于：

```text
damai-program-service/src/main/resources/schema.sql
```

包含：

1. `d_program`：节目主表。
2. `d_program_group`：节目分组表。
3. `d_program_category`：节目类型表。
4. `d_program_show_time`：节目演出时间表。
5. `d_ticket_category`：节目票档表。
6. `d_seat`：座位表。

### 订单库表

参考 `cloud/damai_order_0.sql`，单库版本位于：

```text
damai-order-service/src/main/resources/schema.sql
```

包含：

1. `d_order`：订单主表。
2. `d_order_ticket_user`：订单购票人明细表。

### 支付库表

参考 `cloud/damai_pay_0.sql`，单库版本位于：

```text
damai-pay-service/src/main/resources/schema.sql
```

包含：

1. `d_pay_bill`：支付账单表。
2. `d_refund_bill`：退款账单表。

## 技术栈

### 后端

1. Java 17
2. Spring Boot 3.2.5
3. Spring Cloud Gateway
4. Spring Cloud Alibaba Nacos Discovery
5. Spring MVC
6. Spring Validation
7. MyBatis
8. MySQL 5.7
9. Redis 6.0.8
10. Nacos 2.2.3
11. H2 测试数据库
12. JUnit + MockMvc 接口测试
13. SLF4J 日志
14. WebClient 服务间调用
15. 支付宝支付账单与本地模拟支付

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

分别启动后端服务：

```powershell
mvn -s maven-settings.xml -pl damai-user-service spring-boot:run
mvn -s maven-settings.xml -pl damai-program-service spring-boot:run
mvn -s maven-settings.xml -pl damai-order-service spring-boot:run
mvn -s maven-settings.xml -pl damai-pay-service spring-boot:run
mvn -s maven-settings.xml -pl damai-gateway-service spring-boot:run
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
5. 节目创建、列表和详情。
6. 座位批量初始化和查询。
7. 订单创建、查询、列表和取消。
8. 超时未支付订单取消。
9. 订单支付状态更新。
10. 支付宝支付单创建、模拟支付和异步通知处理。
11. 网关登录态过滤和 IP 访问频率限制。

前端构建：

```powershell
cd frontend
npm run build
```

## 后续可扩展方向

1. 购票人编辑能力。
2. 节目更新和上下架。
3. 票档库存扣减。
4. 座位锁定和释放。
5. 退款接口和支付宝退款回调。
6. 支付状态主动查询和补偿任务。
7. 后台管理员登录。
8. 更细粒度接口鉴权。
9. Redis 分布式锁或数据库乐观锁库存扣减。

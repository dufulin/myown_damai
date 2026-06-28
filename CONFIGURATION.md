# 配置治理说明

## Profile

项目默认使用 `dev` profile：

```bash
SPRING_PROFILES_ACTIVE=dev
```

`dev` 默认所有基础设施都走 `localhost`，适合 IDEA 本地启动服务。

生产环境使用：

```bash
SPRING_PROFILES_ACTIVE=prod
```

`prod` 会从 Nacos 配置中心读取配置，默认读取两个 dataId：

- `{spring.application.name}-prod.yml`
- `{spring.application.name}.yml`

例如订单服务会读取：

- `damai-order-service-prod.yml`
- `damai-order-service.yml`

## 通用环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `SPRING_PROFILES_ACTIVE` | `dev` | 当前运行 profile |
| `DAMAI_MYSQL_HOST` | `localhost` | MySQL 主机 |
| `DAMAI_MYSQL_PORT` | `3306` | MySQL 端口 |
| `DAMAI_MYSQL_DATABASE` | `myown_damai` | MySQL 数据库 |
| `DAMAI_DB_URL` | 自动拼接 localhost URL | 所有服务通用数据库 URL |
| `DAMAI_USER_DB_URL` | 继承 `DAMAI_DB_URL` | 用户服务数据库 URL |
| `DAMAI_PROGRAM_DB_URL` | 继承 `DAMAI_DB_URL` | 节目服务数据库 URL |
| `DAMAI_ORDER_DB_URL` | 继承 `DAMAI_DB_URL` | 订单服务数据库 URL |
| `DAMAI_PAY_DB_URL` | 继承 `DAMAI_DB_URL` | 支付服务数据库 URL |
| `DAMAI_DB_USERNAME` | `root` | 数据库用户名 |
| `DAMAI_DB_PASSWORD` | `root` | 数据库密码 |
| `DAMAI_REDIS_HOST` | `localhost` | Redis 主机 |
| `DAMAI_REDIS_PORT` | `6379` | Redis 端口 |
| `DAMAI_REDIS_PASSWORD` | 空 | Redis 密码，若本地 Redis 使用 `requirepass=123`，设置为 `123` |
| `DAMAI_REDIS_DATABASE` | `0` | Redis database |
| `DAMAI_GATEWAY_RATE_GLOBAL_IP_MAX_REQUESTS` | `120` | 网关单 IP 每个全局窗口的最大请求数 |
| `DAMAI_GATEWAY_RATE_GLOBAL_IP_WINDOW_SECONDS` | `60` | 网关全局 IP 限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_API_IP_MAX_REQUESTS` | `120` | 单 IP 每种普通接口类型的最大请求数 |
| `DAMAI_GATEWAY_RATE_API_IP_WINDOW_SECONDS` | `60` | 普通接口类型 IP 限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_AUTH_IP_MAX_REQUESTS` | `20` | 单 IP 登录、注册、刷新接口的最大请求数 |
| `DAMAI_GATEWAY_RATE_AUTH_IP_WINDOW_SECONDS` | `60` | 认证接口 IP 限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_ORDER_IP_MAX_REQUESTS` | `20` | 单 IP 下单接口的最大请求数 |
| `DAMAI_GATEWAY_RATE_ORDER_IP_WINDOW_SECONDS` | `60` | 下单接口 IP 限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_USER_MAX_REQUESTS` | `120` | 单登录用户每个窗口的最大请求数 |
| `DAMAI_GATEWAY_RATE_USER_WINDOW_SECONDS` | `60` | 登录用户全局限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_USER_READ_MAX_REQUESTS` | `120` | 单用户每种查询接口的最大请求数 |
| `DAMAI_GATEWAY_RATE_USER_WRITE_MAX_REQUESTS` | `30` | 单用户每种写接口的最大请求数 |
| `DAMAI_GATEWAY_RATE_USER_API_WINDOW_SECONDS` | `60` | 用户接口类型限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_ORDER_USER_MAX_REQUESTS` | `5` | 单用户每分钟最大下单请求数 |
| `DAMAI_GATEWAY_RATE_ORDER_USER_WINDOW_SECONDS` | `60` | 单用户下单限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_ORDER_PROGRAM_MAX_REQUESTS` | `200` | 单节目在节目限流窗口内的最大下单请求数 |
| `DAMAI_GATEWAY_RATE_ORDER_PROGRAM_WINDOW_SECONDS` | `1` | 单节目下单限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_ORDER_USER_PROGRAM_MAX_REQUESTS` | `3` | 单用户对同一节目在组合窗口内的最大下单请求数 |
| `DAMAI_GATEWAY_RATE_ORDER_USER_PROGRAM_WINDOW_SECONDS` | `10` | 用户与节目组合限流窗口秒数 |
| `DAMAI_GATEWAY_RATE_ORDER_BODY_MAX_BYTES` | `65536` | 网关为提取节目 ID 允许缓存的最大下单请求体字节数 |
| `DAMAI_KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka 地址 |
| `DAMAI_ES_HOST` | `localhost` | Elasticsearch 主机 |
| `DAMAI_ES_PORT` | `9200` | Elasticsearch 端口 |
| `DAMAI_ES_URI` | 自动拼接 `http://localhost:9200` | Elasticsearch 完整地址 |
| `DAMAI_NACOS_SERVER_ADDR` | dev: `localhost:8848`, prod: `nacos-server:8848` | Nacos 地址 |
| `DAMAI_NACOS_USERNAME` | `nacos` | Nacos 用户名 |
| `DAMAI_NACOS_PASSWORD` | `nacos` | Nacos 密码 |
| `DAMAI_NACOS_CONFIG_ENABLED` | dev: `false`, prod: `true` | 是否启用 Nacos 配置中心 |
| `DAMAI_NACOS_NAMESPACE` | 空 | Nacos namespace |
| `DAMAI_NACOS_GROUP` | `DEFAULT_GROUP` | Nacos 服务发现 group |
| `DAMAI_NACOS_CONFIG_GROUP` | `DEFAULT_GROUP` | Nacos 配置 group |
| `DAMAI_ACCESS_TOKEN_TTL_MINUTES` | `15` | access token 有效分钟数 |
| `DAMAI_ACCESS_SESSION_CACHE_TTL_MINUTES` | `20` | access token 会话索引在 Redis 中的缓存分钟数 |
| `DAMAI_REFRESH_TOKEN_TTL_DAYS` | `7` | refresh token 和 HttpOnly Cookie 有效天数 |
| `DAMAI_REFRESH_COOKIE_SECURE` | dev: `false`, prod: `true` | 是否仅允许 HTTPS 发送 refresh Cookie |
| `DAMAI_REFRESH_COOKIE_SAME_SITE` | `Lax` | refresh Cookie 的 SameSite 策略 |
| `DAMAI_TOKEN_CLEANUP_CRON` | `0 0 * * * *` | 失效 token 清理任务 cron |
| `DAMAI_TOKEN_CLEANUP_RETENTION_HOURS` | `24` | 失效 token 清理前的审计保留小时数 |
| `DAMAI_USER_SERVICE_BASE_URL` | `http://localhost:8081` | 订单服务调用用户服务的地址 |
| `DAMAI_USER_CONNECT_TIMEOUT_MILLIS` | `1000` | 订单服务调用用户服务的连接超时 |
| `DAMAI_USER_READ_TIMEOUT_MILLIS` | `2500` | 订单服务调用用户服务的读取超时 |
| `DAMAI_USER_RETRY_COUNT` | `1` | 订单服务调用用户服务的最大重试次数 |
| `DAMAI_PAY_EVENT_COMPENSATION_ENABLED` | `true` | 是否启用支付事件补偿扫描 |
| `DAMAI_PAY_EVENT_SCAN_DELAY_MILLIS` | `30000` | 支付事件补偿扫描间隔 |
| `DAMAI_PAY_EVENT_MAX_RETRY_COUNT` | `5` | 支付事件通知订单服务最大重试次数 |
| `DAMAI_PAY_EVENT_RETRY_BASE_DELAY_SECONDS` | `30` | 支付事件重试基础延迟 |
| `DAMAI_PAY_EVENT_RETRY_MAX_DELAY_SECONDS` | `300` | 支付事件最大重试延迟 |
| `DAMAI_PAY_EVENT_PROCESSING_TIMEOUT_SECONDS` | `120` | 支付事件处理中卡住后的恢复时间 |

## 本地 Docker 建议

本地服务在 IDEA 中启动时，基础设施容器统一映射到宿主机端口即可：

```bash
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root mysql:5.7
docker run -d --name redis -p 6379:6379 redis:6.0.8
docker run -d --name nacos-server -p 8848:8848 -p 9848:9848 -e MODE=standalone nacos/nacos-server:v2.2.3
```

如果 Redis 使用密码启动：

```bash
set DAMAI_REDIS_PASSWORD=123
```

Kafka 本地建议使用成对的 Zookeeper/Kafka 容器，并让 Java 服务连接：

```bash
set DAMAI_KAFKA_BOOTSTRAP_SERVERS=localhost:9092
```

## 首个管理员初始化

用户默认角色为 `USER`。首次启用 RBAC 时，先执行 [cloud/damai_user_role.sql](cloud/damai_user_role.sql)，并将脚本中的
`REPLACE_WITH_ADMIN_MOBILE` 替换为首个管理员手机号。之后管理员可以调用
`PUT /api/users/{userId}/role` 管理 `USER`、`OPERATOR`、`ADMIN` 角色；`SYSTEM` 不允许分配给用户账号。

生产部署只应对外暴露网关 `8080` 端口。用户、节目、订单、支付服务端口应放在受限内网，
避免外部请求绕过网关伪造 `X-Damai-User-Id`、`X-Damai-User-Role` 等服务间可信请求头。

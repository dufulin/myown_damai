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
| `DAMAI_ADMIN_DB_URL` | 继承 `DAMAI_DB_URL` | 管理服务只读聚合数据库 URL |
| `DAMAI_DB_USERNAME` | `root` | 数据库用户名 |
| `DAMAI_DB_PASSWORD` | `root` | 数据库密码 |
| `DAMAI_PROGRAM_SQL_INIT_MODE` | `never` | 节目核心表初始化模式；默认禁止服务启动时自动建表 |
| `DAMAI_REDIS_HOST` | `localhost` | Redis 主机 |
| `DAMAI_REDIS_PORT` | `6379` | Redis 端口 |
| `DAMAI_REDIS_PASSWORD` | `123` | Redis 密码，与 Docker Compose 默认配置一致 |
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
| `DAMAI_USER_SERVICE_URL` | `http://damai-user-service` | 管理服务调用用户服务的地址 |
| `DAMAI_PROGRAM_SERVICE_URL` | `http://damai-program-service` | 管理服务调用节目服务的地址 |
| `DAMAI_ORDER_SERVICE_URL` | `http://damai-order-service` | 管理服务调用订单服务的地址 |
| `DAMAI_PAY_SERVICE_URL` | `http://damai-pay-service` | 管理服务调用支付服务的地址 |
| `DAMAI_ADMIN_CLIENT_TIMEOUT_MILLIS` | `3500` | 管理服务执行运营动作的总超时 |
| `DAMAI_PAY_EVENT_COMPENSATION_ENABLED` | `true` | 是否启用支付事件补偿扫描 |
| `DAMAI_PAY_EVENT_SCAN_DELAY_MILLIS` | `30000` | 支付事件补偿扫描间隔 |
| `DAMAI_PAY_EVENT_MAX_RETRY_COUNT` | `5` | 支付事件通知订单服务最大重试次数 |
| `DAMAI_PAY_EVENT_RETRY_BASE_DELAY_SECONDS` | `30` | 支付事件重试基础延迟 |
| `DAMAI_PAY_EVENT_RETRY_MAX_DELAY_SECONDS` | `300` | 支付事件最大重试延迟 |
| `DAMAI_PAY_EVENT_PROCESSING_TIMEOUT_SECONDS` | `120` | 支付事件处理中卡住后的恢复时间 |

## Docker Compose 一键启动

根目录的 `docker-compose.yml` 统一启动 MySQL、Redis、Nacos、Elasticsearch、Zookeeper、Kafka 和 Prometheus。
默认配置已经和 Java 服务的 `dev` profile 对齐，不需要填写局域网 IP：

```powershell
docker compose up -d --wait
docker compose ps
```

需要覆盖默认密码、端口或 JVM 内存时，可以从示例创建本地 `.env`：

```powershell
Copy-Item .env.example .env
docker compose up -d --wait
```

固定访问地址：

| 组件 | IDEA/宿主机地址 | Compose 网络地址 |
| --- | --- | --- |
| MySQL | `localhost:3306` | `mysql:3306` |
| Redis | `localhost:6379`，密码 `123` | `redis:6379` |
| Nacos | `localhost:8848` | `nacos:8848` 或 `nacos-server:8848` |
| Elasticsearch | `http://localhost:9200` | `http://elasticsearch:9200` 或 `http://es:9200` |
| Zookeeper | `localhost:2181` | `zookeeper:2181` |
| Kafka | `localhost:9092` | `kafka:29092` |
| Prometheus | `http://localhost:9090` | `prometheus:9090` |

停止容器但保留数据：

```powershell
docker compose down
```

Kafka 和 Zookeeper 使用同一个 Compose 项目的命名卷。需要重建 Kafka 集群元数据时，必须成对清理；
以下命令也会删除 MySQL、Redis、Nacos 和 ES 的本地数据：

```powershell
docker compose down -v
docker compose up -d --wait
```

如果之前手工启动的容器仍占用 `3306`、`6379`、`8848`、`9200`、`2181`、`9092` 或 `9090`，
应先停止旧容器，再启动本项目 Compose。

### Redis 密码排查

官方 `redis` 镜像不会根据 `-e requirepass=123` 自动启用密码。环境变量只会被保存到容器中，
Redis 进程仍然以无密码模式启动，随后会导致 Redisson 报
`ERR AUTH <password> called without any password configured`。

推荐直接使用本项目 Compose。需要单独创建 Redis 容器时，应把密码作为 `redis-server` 参数传入：

```powershell
docker run -d --name redis -p 6379:6379 redis:6.0.8 redis-server --appendonly yes --requirepass 123
```

检查密码是否生效：

```powershell
docker exec redis redis-cli -a 123 ping
```

返回 `PONG` 表示配置正确。`application.yml`、`.env` 与容器的密码必须保持一致；默认均为 `123`。
`CONFIG SET requirepass 123` 只修改当前 Redis 进程，旧容器重启后可能失效，不能替代正确的启动命令。

## 节目表手动初始化

节目服务默认不会自动创建以下核心表：

- `d_program_category`
- `d_program_group`
- `d_program`
- `d_program_show_time`
- `d_ticket_category`
- `d_seat`

首次启动节目服务前，在项目根目录手动执行：

```powershell
Get-Content -Raw damai-program-service/src/main/resources/schema.sql |
  docker exec -i mysql mysql -uroot -proot myown_damai
```

本地通过 MySQL 客户端执行时，也可以直接打开
`damai-program-service/src/main/resources/schema.sql` 并选择 `myown_damai` 数据库运行。
应用运行环境保持 `DAMAI_PROGRAM_SQL_INIT_MODE=never`；只有确实需要临时恢复自动初始化时才将其设置为 `always`。

## 可观测性

所有后端服务都暴露以下 Actuator 端点：

| 端点 | 用途 |
| --- | --- |
| `/actuator/health` | 服务健康状态 |
| `/actuator/prometheus` | Prometheus 指标抓取 |
| `/actuator/metrics` | 本地查看可用指标名称 |

Prometheus 使用 [deploy/prometheus/prometheus.yml](deploy/prometheus/prometheus.yml) 抓取宿主机 `8080` 到 `8085`
端口，并加载 [deploy/prometheus/damai-rules.yml](deploy/prometheus/damai-rules.yml) 中的聚合规则。启动 Java 服务和
Compose 后，可在 `http://localhost:9090/targets` 检查抓取状态。

核心记录指标：

| 指标 | 说明 |
| --- | --- |
| `http_server_requests_seconds_count` | HTTP QPS 和错误率来源 |
| `kafka_consumer_records_lag_max` | Kafka 消费者最大积压 |
| `damai_cache_requests_total{result="hit\|miss\|error"}` | Redis 缓存读取结果与命中率 |
| `damai_order_creation_duration_seconds` | 同步提交、异步提交和异步消费建单耗时 |

每个 API 响应都会携带 `X-Trace-Id`。网关会接受合法的调用方 traceId 或生成新值，并向下游 HTTP 与 Kafka
消息传播。日志格式固定输出 `traceId`、`userId`、`orderNumber`、`programId`，未知字段显示为 `-`。

## 首个管理员初始化

用户默认角色为 `USER`。首次启用 RBAC 时，先执行 [cloud/damai_user_role.sql](cloud/damai_user_role.sql)，并将脚本中的
`REPLACE_WITH_ADMIN_MOBILE` 替换为首个管理员手机号。之后管理员可以调用
`PUT /api/users/{userId}/role` 管理 `USER`、`OPERATOR`、`ADMIN` 角色；`SYSTEM` 不允许分配给用户账号。

生产部署只应对外暴露网关 `8080` 端口。用户、节目、订单、支付、管理服务端口应放在受限内网，
避免外部请求绕过网关伪造 `X-Damai-User-Id`、`X-Damai-User-Role` 等服务间可信请求头。

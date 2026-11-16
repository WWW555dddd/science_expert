# 科技保险专家管理与协同服务平台

基于 Spring Boot、Vue 3、MySQL、Redis、MinIO 和 Neo4j 的专家管理平台。

## 技术栈

| 组件 | 版本或说明 |
|---|---|
| 后端 | Java 21、Spring Boot 4.1.2、MyBatis |
| 前端 | Vue 3、Vite、Element Plus |
| 数据库 | MySQL 8 |
| 缓存与队列 | Redis 7 |
| 文件存储 | MinIO |
| 图谱查询 | Neo4j 5 |

## 项目结构

```text
backend/
├─ ruoyi-admin/          应用启动模块
├─ ruoyi-expert/         专家、画像、图谱和匹配模块
├─ sql/init.sql          全新数据库初始化
├─ sql/verify.sql        只读验收检查
├─ bin/dev.ps1           开发运维入口
├─ docker-compose.yml    Redis、Neo4j、MinIO
└─ .env.expert.example   后端及基础服务配置模板
frontend/                Vue 前端
docs/                    设计与验收文档
miniodata/               MinIO 数据目录
```

## 环境要求

- JDK 21
- Maven 3.9+
- Node.js 22+
- MySQL 8
- Docker Desktop

## 配置

在 `backend` 目录创建本地配置：

```powershell
cd backend
Copy-Item .env.expert.example .env.expert.local
```
`.env.expert.local` 包含真实凭据，不得提交到 Git。

## 基础服务

Redis、Neo4j、MinIO 统一由 `backend/docker-compose.yml` 管理，配置来自 `.env.expert.local`。

```powershell
cd backend

# 启动或更新
.\bin\dev.ps1 services

# 查看状态
.\bin\dev.ps1 status

# 停止
.\bin\dev.ps1 stop-services
```

直接使用 Compose：

```powershell
cd backend
docker compose --env-file .env.expert.local up -d
docker compose --env-file .env.expert.local ps
docker compose --env-file .env.expert.local logs -f redis
docker compose --env-file .env.expert.local logs -f neo4j
docker compose --env-file .env.expert.local logs -f minio
```

默认端口：

| 服务 | 地址或端口 |
|---|---|
| Redis | `REDIS_PORT`，默认 `6379` |
| Neo4j Browser | `NEO4J_HTTP_PORT`，默认 `7474` |
| Neo4j Bolt | `NEO4J_BOLT_PORT`，默认 `7687` |
| MinIO API | `9000` |
| MinIO 控制台 | `9001` |

### 数据持久化

| 服务 | 持久化位置 |
|---|---|
| Redis | Docker 命名卷 `science-expert-redis-data` |
| Neo4j | 原有 Neo4j 命名卷 |
| MinIO | `MINIO_DATA_DIR`，默认 `../miniodata` |

`docker compose down` 保留数据卷；禁止在生产环境执行 `docker compose down -v`。

### 从旧启动方式迁移

旧版本使用 `docker run` 创建 Redis 和 MinIO。首次切换到 Compose 时执行：

```powershell
cd backend
docker stop redis science-expert-neo4j science-expert-minio
docker rm redis science-expert-neo4j science-expert-minio
.\bin\dev.ps1 services
```

Neo4j 和 MinIO 数据会保留；旧 Redis 缓存会清空。

## 初始化 MySQL

仅在全新空库执行：

```powershell
cd backend
mysql -uroot -p -e "source sql/init.sql"
```

已运行环境执行只读检查：

```powershell
cd backend
mysql -uroot -p science_expert -e "source sql/verify.sql"
```

## 启动后端

### IDEA

1. 以 Maven 项目导入 `backend/pom.xml`。
2. 将运行工作目录设置为 `backend`。
3. 启动 `com.ruoyi.RuoYiApplication`。

Spring Boot 会自动读取 `backend/.env.expert.local`。

### 命令行

```powershell
cd backend
.\bin\dev.ps1 build
.\bin\dev.ps1 run
```

后端默认地址：`http://127.0.0.1:8080`。

## 启动前端

```powershell
cd frontend
npm ci
npm run dev
```

访问 `http://localhost/`。Vite 将 `/dev-api` 代理到 `http://127.0.0.1:8080`。

## 验收

```powershell
# 后端构建
cd backend
.\bin\dev.ps1 build

# 基础服务状态
.\bin\dev.ps1 status

# 数据库只读检查
mysql -uroot -p science_expert -e "source sql/verify.sql"

# 前端生产构建
cd ../frontend
npm run build:prod
```

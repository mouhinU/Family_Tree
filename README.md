# Family Tree 族谱管理系统

一个面向家族族谱数字化管理的 Web 应用，支持以可视化树形结构维护家族成员、亲属关系、世代辈分与排次，采用古典族谱视觉风格（宣纸牌位式节点、朱砂配色、龙纹辈分水印）。

## 技术栈

| 分类 | 技术 | 版本 |
|------|------|------|
| 语言 / 运行时 | Java | 21 |
| 后端框架 | Spring Boot | 3.4.1 |
| ORM | MyBatis-Plus | 3.5.9 |
| 数据库 | H2（开发，文件模式）/ MySQL（生产） | 2.3.x / 8.x |
| 数据库迁移 | Flyway | 10.x |
| 密码加密 | Spring Security Crypto（BCrypt） | - |
| 前端可视化 | D3.js | 7.8.5 |
| 构建 | Maven（多模块，内置 mvnw） | 3.9.x |

## 模块结构

Maven 多模块工程，分层依赖 `web → service → persistence → common`：

| 模块 | 职责 |
|------|------|
| `family-tree-common` | 公共层：DTO/VO、枚举、常量、业务异常、统一返回 `Result` |
| `family-tree-persistence` | 持久层：MyBatis-Plus Mapper 与 DO 实体 |
| `family-tree-service` | 业务层：节点、关系、树形结构、辈分、用户服务 |
| `family-tree-web` | Web 层：Controller、会话认证、静态资源（前端页面） |

## 功能特性

- **族谱树可视化**：D3.js 渲染古典牌位式节点（竖排姓名、双线框、绶带强调色），支持三种布局方向——上下（tb）、左→右（lr）、右→左（rl，传统阅读方向）。
- **成员管理**：增删改查家族节点，支持姓名、性别、出生/去世日期、颜色标注、备注、头像。
- **亲属关系**：配偶关系（含离异标记与结婚/离婚日期）、亲子关系；支持"血亲配偶"（如表兄妹结婚）跨分支连线。
- **世代辈分**：50 世辈分名（字辈）管理，节点显示世代号与辈分名，背景龙纹辈分水印并高亮当前用户所属世代。
- **同胞排次**：为子女设置排次（birthOrder），支持按出生日期批量编号。
- **已故成员**：去世日期记录，已故节点统一置灰，支持"只看健在"过滤。
- **颜色标注**：默认 / 父系 / 母系 / 姻亲 / 过继 / 高亮，已故统一灰色。
- **认证**：注册 / 登录 / 登出，BCrypt 加盐哈希（存量 MD5 登录时透明迁移），会话固定防护。
- **多用户隔离**：数据按用户隔离，各用户维护各自的族谱。

## 快速开始

### 环境要求

- JDK 21
- Maven（项目内置 `mvnw`，无需全局安装）

### 启动（开发环境，H2）

```bash
# 首次运行需先将各模块安装到本地仓库
./mvnw install -DskipTests

# 启动 Web 模块（默认 dev profile，H2 文件库 ./data/family-tree）
./mvnw spring-boot:run -pl family-tree-web -Dspring-boot.run.profiles=dev
```

启动后访问 `http://localhost:8090`，H2 控制台位于 `http://localhost:8090/h2-console`。

### 启动（生产环境，MySQL）

```bash
./mvnw spring-boot:run -pl family-tree-web -Dspring-boot.run.profiles=mysql
```

需先创建 `family_tree` 数据库，并按 `application-mysql.yml` 配置账号密码。

### 配置说明

| Profile | 数据源 | 迁移脚本目录 |
|---------|--------|--------------|
| `dev`（默认） | H2 文件库 `./data/family-tree`（AUTO_SERVER 模式） | `db/migration/h2` |
| `mysql` | MySQL `jdbc:mysql://localhost:3306/family_tree` | `db/migration/mysql` |

Flyway 在启动时自动执行迁移（V1~V5），`baseline-on-migrate: true`。

## 数据库迁移

| 版本 | 内容 |
|------|------|
| V1 | 初始化表结构（sys_user、family_node、family_relation） |
| V2 | 关系表增加离异标记（is_divorced / 婚姻日期） |
| V3 | 节点增加同胞排次（birth_order） |
| V4 | 辈分名表（family_generation，uk(user_id, generation)） |
| V5 | 用户表增加所属世代（sys_user.generation） |

## API 概览

| 前缀 | 说明 |
|------|------|
| `/api/auth` | 注册 / 登录 / 登出 / 当前用户信息 |
| `/api/tree` | 获取完整族谱树 / 子树 |
| `/api/node` | 节点增删改查、批量修改颜色 |
| `/api/relation` | 关系增删改（配偶 / 亲子） |
| `/api/generation` | 辈分名批量读写 |

## 前端

静态资源位于 `family-tree-web/src/main/resources/static/`：

- `index.html` / `login.html` —— 主页面与登录页
- `js/family-tree.js` —— 核心逻辑（D3 布局、渲染、弹窗交互）
- `css/` —— 古典族谱主题样式
- `img/` —— 龙纹水印等静态图片

> 注意：应用从 `target/classes/static` 提供静态资源。修改前端后需执行
> `./mvnw compile -pl family-tree-web` 复制到 target 方可生效。

## 开发提示

- **默认账号**：`admin`（内置真实族谱数据，勿污染）；测试请使用隔离账号 `botest / test123`。
- **端口**：应用监听 `8090`。
- **后端改动生效**：`service` / `persistence` / `common` 从本地仓库 SNAPSHOT JAR 加载，改动后需 `./mvnw install -pl <模块> -DskipTests` 并重启应用。
- **逻辑删除**：MyBatis-Plus 全局配置 `deleted` 字段逻辑删除。
- **编码规范**：遵循根目录 `AGENTS.md`（基于《Java 开发手册》华山版裁剪）。

## 项目结构

```
Family_Tree/
├── pom.xml                     # 父 POM（版本与模块管理）
├── family-tree-common/         # 公共层
├── family-tree-persistence/    # 持久层
├── family-tree-service/        # 业务层
├── family-tree-web/            # Web 层 + 前端静态资源
│   └── src/main/resources/
│       ├── application*.yml    # 多环境配置
│       ├── db/migration/       # Flyway 迁移（h2 / mysql）
│       └── static/             # 前端页面与资源
├── AGENTS.md                   # 编码规范
└── README.md
```

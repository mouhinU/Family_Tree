# Family Tree 族谱管理系统

一个面向家族族谱数字化管理的 Web 应用，支持以可视化树形结构维护家族成员、亲属关系、世代辈分与排次，采用古典族谱视觉风格（宣纸牌位式节点、朱砂配色、龙纹辈分水印）。

## 技术栈

| 分类       | 技术                                         | 版本                   |
|----------|--------------------------------------------|----------------------|
| 语言 / 运行时 | Java                                       | 21                   |
| 后端框架     | Spring Boot                                | 4.0.8                |
| AI 框架    | Spring AI（OpenAI 兼容，默认对接 DeepSeek）         | 2.0.0                |
| ORM      | MyBatis-Plus（spring-boot4-starter）         | 3.5.17               |
| JSON     | Jackson 3                                  | 3.x                  |
| 数据库      | H2（开发，文件模式）/ MySQL（生产）                     | H2 2.3.x / MySQL 8.x |
| 数据库迁移    | Flyway（H2 + MySQL 双轨）                      | 10.x（V1–V22）         |
| 密码加密     | Spring Security Crypto（BCrypt，存量 MD5 透明迁移） | —                    |
| 缓存       | Caffeine（族谱树缓存、登录限流）                       | 3.1.8                |
| 监控       | Micrometer + Actuator                      | —                    |
| 前端可视化    | D3.js                                      | 7.8.5                |
| PDF 导出   | jsPDF                                      | 2.5.1                |
| 构建       | Maven（多模块，内置 mvnw）                         | 3.9.x                |

## 架构设计

采用 DDD（领域驱动设计）分层架构，Maven 多模块工程：

```
web → application → domain ← infrastructure
              ↓
            common（全局依赖）
```

| 模块                           | 职责                                              | 依赖方向                      |
|------------------------------|-------------------------------------------------|---------------------------|
| `family-tree-common`         | 公共层：DTO/VO、枚举、常量、业务异常基类、统一返回 `Result`           | 被所有层依赖                    |
| `family-tree-domain`         | 领域层：实体、值对象、领域服务、仓储接口、领域事件。核心业务逻辑，不依赖基础设施        | → common                  |
| `family-tree-infrastructure` | 基础设施层：仓储实现、DO 实体、MyBatis-Plus Mapper、DO↔领域对象转换器 | → domain                  |
| `family-tree-application`    | 应用层：用例编排、事务管理、权限校验、DTO↔领域对象转换                   | → domain + infrastructure |
| `family-tree-web`            | 表现层：Controller、会话认证、安全过滤器、全局异常处理、静态资源           | → application             |

**依赖原则：** 领域层保持纯净，不引用基础设施层注解；应用层通过仓储接口操作数据，不直接访问 Mapper；跨聚合通信通过领域事件实现最终一致性。

## 功能特性

### 族谱核心

- **族谱树可视化**：D3.js 渲染古典牌位式节点（竖排姓名、双线框、绶带强调色），支持三种布局方向——上下（tb）、左→右（lr）、右→左（rl，传统阅读方向）。
- **成员管理**：增删改查家族节点，支持姓名、性别、出生/去世日期、颜色标注、备注、头像、同胞排次、字/号/讳、坟茔位置、配偶信息。
- **亲属关系**：配偶关系（含离异/丧偶标记与结婚/离婚日期）、亲子关系、过继/收养关系；支持"血亲配偶"
  （如表兄妹结婚）跨分支连线；改嫁/续弦场景自动处理配偶卡片挂载优先级。
- **世代辈分**：50 世辈分名（字辈）管理，节点显示世代号与辈分名，背景龙纹辈分水印并支持按世代圈点高亮。水印行列布局可自定义并持久化。
- **同胞排次**：为子女设置排次（birthOrder），支持按出生日期批量编号。
- **已故成员**：去世日期记录，已故节点统一置灰。"只看健在"模式支持：已故节点保留布局占位（卡片隐藏）、已故父/母节点子女自动重挂到健在配偶或离异前配偶名下、已故父节点到子节点的连线隐藏。
- **节点交互**：单击选中节点（高亮自身 + 配偶），双击打开详情弹框；点击连线触发走马灯光点动画（光点数量随线段长度动态计算），再次点击相同线取消、点击不同线切换。
- **搜索定位**：搜索族人后点击结果，画布平滑平移居中目标节点，并触发脉冲放大特效（朱红光晕 + 心跳式缩放）。
- **忌日提醒**：提醒用户近期已故长辈的忌日信息，支持自定义提醒天数范围。
- **时间线**：家族事件时间线可视化，展示出生、去世、婚姻等关键事件。
- **关系路径分析**：BFS 最短路径算法分析两个族人之间的亲属关系路径，以步骤列表形式呈现。
- **颜色标注**：默认 / 父系 / 母系 / 姻亲 / 过继 / 高亮，已故统一灰色，支持批量修改。
- **PDF 导出**：全量布局计算生成独立 SVG（含宣纸背景 + 龙纹辈分水印），栅格化后下载自定义尺寸 PDF。
- **数据导出**：导出全量家族节点和关系数据为 JSON 格式。

### 多用户与协作

- **家族管理**：创建家族、邀请码加入、邀请链接分享；三级角色权限（OWNER 族长 > ADMIN 管理员 > MEMBER 成员）。
- **成员角色**：OWNER 可设/取消管理员、移除任何人、修改家族信息（堂号/籍贯）；ADMIN 可移除普通成员；邀请码刷新与链接生成。
- **多家族切换**：用户可加入多个家族，随时切换当前活跃家族。
- **标记为我**：登录用户可标记自己在族谱中对应的节点，节点卡片显示金色边框与"我"徽章。
- **操作日志**：记录登录/注册/节点增删改/颜色修改等操作，OWNER/ADMIN 可分页查询并按类型筛选。
- **族言留言板**：家族成员可发布留言，底部轮播条实时滚动展示最新留言，支持悬停暂停和关闭。

### 祭奠与缅怀

- **敬献**：为已故长辈敬献祭品，详情弹框中展示祭奠统计面板与敬献动效（glow + 烟雾 + 火焰）。
- **送鲜花**：为已故长辈送鲜花，效果为白色与黄色菊花（CSS conic-gradient 绘制），素雅庄重。
- **长辈校验**：前后端双重校验，只能为辈分高于自己的已故成员祭奠。

### 安全

- BCrypt 加盐哈希（存量 MD5 登录时透明迁移）
- Session 固定防护（登录重建 Session）
- 登录暴力破解锁定（Caffeine，同一用户名连续 5 次失败锁定 10 分钟）
- CSRF Token 防护（Synchronizer Token Pattern）
- 安全响应头（X-Frame-Options、CSP、Referrer-Policy、Cache-Control）
- 写操作频率限制（Caffeine 滑动窗口，每用户每 60 秒最多 30 次写操作）
- 前端 XSS 防护（escapeHtml/escapeAttr 全量转义 + data-* 事件委托）
- 头像上传路径遍历防护
- MySQL 凭据外部化（环境变量占位）

## 快速开始

### 环境要求

- JDK 21
- Maven（项目内置 `mvnw`，无需全局安装）

### 启动（开发环境，H2）

```bash
# 首次运行需先将各模块安装到本地仓库
./mvnw install -DskipTests

# 启动 Web 模块（默认 dev profile，H2 文件库 ./data/family-tree）
cd family-tree-web
../mvnw spring-boot:run -Dspring-boot.run.profiles=dev
```

启动后访问 `http://localhost:8090`，H2 控制台位于 `http://localhost:8090/h2-console`。

> **注意**：`mvnw clean` 只删除 target/ 目录，不会影响 data/ 下的 H2 数据库文件。

### 启动（生产环境，MySQL）

```bash
./mvnw spring-boot:run -pl family-tree-web -Dspring-boot.run.profiles=mysql
```

需先创建 `family_tree` 数据库，并通过环境变量 `DB_USERNAME` / `DB_PASSWORD` 配置凭据。

### 配置说明

| Profile   | 数据源                                             | 迁移脚本目录               |
|-----------|-------------------------------------------------|----------------------|
| `dev`（默认） | H2 文件库 `./data/family-tree`（AUTO_SERVER 模式）     | `db/migration/h2`    |
| `mysql`   | MySQL `jdbc:mysql://localhost:3306/family_tree` | `db/migration/mysql` |

Flyway 在启动时自动执行迁移（V1~V22），`baseline-on-migrate: true`。

### AI 功能配置

AI 能力（智能录入、自然语言问答、家族故事、OCR 解析）基于 Spring AI 2.0（OpenAI 兼容协议，默认对接 DeepSeek）。默认关闭，启用方式：

| 配置项                    | 环境变量          | 默认值                          | 说明              |
|------------------------|---------------|------------------------------|-----------------|
| `ai.llm.enabled`       | `AI_LLM_ENABLED` | `false`                    | AI 功能总开关        |
| `spring.ai.openai.api-key`  | `AI_API_KEY`  | 空                     | 模型服务 API Key    |
| `spring.ai.openai.base-url` | `AI_BASE_URL` | `https://api.deepseek.com` | 模型服务地址（可切换任意 OpenAI 兼容服务） |
| `spring.ai.openai.chat.model` | `AI_MODEL` | `deepseek-chat`            | 模型名称            |

```bash
# 示例：启用 AI 功能
AI_LLM_ENABLED=true AI_API_KEY=sk-xxx ./mvnw spring-boot:run -pl family-tree-web
```

## 数据库模型

共 9 张表，通过 17 个 Flyway 迁移版本管理：

| 表名                  | 说明        | 核心字段                                                                       |
|---------------------|-----------|----------------------------------------------------------------------------|
| `sys_user`          | 用户账号      | username, password_hash, nickname, current_family_id, node_id              |
| `family`            | 家族/宗族     | name, invite_code, creator_id, hall_name, ancestral_home                   |
| `family_member`     | 用户-家族成员关系 | family_id, user_id, role (OWNER/ADMIN/MEMBER)                              |
| `family_node`       | 族谱节点（人物）  | name, gender, birth_date, death_date, generation, color_label, birth_order |
| `family_relation`   | 节点间关系     | from_node_id, to_node_id, relation_type (亲子/配偶/过继)                         |
| `family_generation` | 辈分名（字辈）   | family_id, generation, name                                                |
| `family_offering`   | 祭奠记录      | node_id, offering_type (敬献/鲜花)                                             |
| `operation_log`     | 操作审计日志    | operation_type, operation_desc, target_type, ip_address                    |
| `family_message`    | 家族留言板     | family_id, user_id, username, content                                      |

### 迁移历史

| 版本  | 内容                                                    |
|-----|-------------------------------------------------------|
| V1  | 初始化表结构（sys_user、family_node、family_relation）          |
| V2  | 关系表增加离异标记（is_divorced）与婚姻日期                           |
| V3  | 节点增加同胞排次（birth_order）                                 |
| V4  | 辈分名表（family_generation）                               |
| V5  | 用户表增加所属世代（sys_user.generation）                        |
| V6  | 祭奠记录表（family_offering）                                |
| V7  | 关系表增加丧偶标记（is_widowed）                                 |
| V8  | 多用户家族支持（family、family_member 表，各业务表增加 family_id）      |
| V9  | 用户表增加出生日期（sys_user.birth_date）                        |
| V10 | 用户表增加族谱节点关联（sys_user.node_id）                         |
| V11 | 操作日志表（operation_log）                                  |
| V12 | 传统族谱增强（农历日期、字/号/讳、堂号/籍贯/坟茔、外嫁女婚配、隐私模式）                |
| V13 | 辈分管理行列布局持久化（family.generation_cols / generation_rows） |
| V14 | 辈分表 user_id 改为可空（多用户改造适配）                             |
| V15 | 清理已废弃的香烛(1)和烧纸(2)祭奠记录，统一由敬献(4)替代                      |
| V16 | 搜索优化：组合索引 (family_id, name)；MySQL 增加 FULLTEXT 索引      |
| V17 | 家族留言板（family_message）                                 |

## API 概览

共 14 个控制器、46 个 API 端点：

| 前缀                       | 端点数 | 说明                                            |
|--------------------------|-----|-----------------------------------------------|
| `/api/auth`              | 6   | 注册 / 登录 / 登出 / 当前用户信息 / 修改个人资料 / 标记为我         |
| `/api/family`            | 10  | 创建 / 加入 / 查询 / 修改家族、成员管理、角色设置、邀请码、切换家族、我的家族列表 |
| `/api/node`              | 7   | 节点增删改查、列表、搜索、批量修改颜色                           |
| `/api/relation`          | 5   | 关系增删改查（配偶 / 亲子 / 过继）、单节点关系列表、全量关系列表           |
| `/api/tree`              | 2   | 完整族谱树（Caffeine 缓存）/ 子树                        |
| `/api/generation`        | 4   | 辈分名列表 / 批量保存 / 布局查询 / 布局保存                    |
| `/api/offering`          | 2   | 祭奠记录 / 节点祭奠统计                                 |
| `/api/message`           | 3   | 发布留言 / 分页留言列表 / 删除留言                          |
| `/api/timeline`          | 1   | 家族事件时间线（分页）                                   |
| `/api/death-anniversary` | 1   | 忌日提醒（可指定天数范围）                                 |
| `/api/relation-path`     | 1   | 两节点间关系路径分析（BFS）                               |
| `/api/operation-log`     | 1   | 操作日志分页查询（OWNER/ADMIN）                         |
| `/api/avatar`            | 2   | 头像上传 / 头像文件服务                                 |
| `/api/data`              | 1   | 全量数据导出（JSON）                                  |

## 前端

静态资源位于 `family-tree-web/src/main/resources/static/`：

**页面**

- `index.html` — 族谱主页面（卷轴动画、工具栏、SVG 画布、右键菜单、留言轮播）
- `login.html` — 登录/注册（支持 `?invite=XXX` 邀请链接自动跳转注册）
- `family-setup.html` — 家族初始化（创建新家族 / 加入已有家族）

**JS 模块**（`js/ft/` 目录，IIFE 组件化，共享 `window.FT` 命名空间）

| 模块                    | 职责                                                                  |
|-----------------------|---------------------------------------------------------------------|
| `core.js`             | 全局常量、状态管理（FT.state）、XSS 转义、Toast 提示、世代统计                            |
| `api.js`              | 统一 fetch 封装（CSRF Token 注入、401 自动跳登录、429 限流提示）、全树加载                  |
| `layout.js`           | 方向无关布局算法、"只看健在"子女重挂树（已故/离异子女挂到健在另一方）、"隐藏外嫁"过滤树                      |
| `render.js`           | D3 SVG 渲染（牌位式节点、连线走马灯动画、辈分水印、卷轴动画、缩放平移、搜索定位脉冲特效、单击/双击区分、"只看健在"连线过滤） |
| `export.js`           | 独立 SVG 构建 + jsPDF 导出（进度指示、最大画布 6000px）                              |
| `modals.js`           | 弹窗基础框架：showModal/closeModal + data-close-modal 事件委托                 |
| `modal-node.js`       | 节点弹窗：新增/编辑/删除/排次/辈分/详情/颜色/关联配偶/过继                                   |
| `modal-family.js`     | 家族弹窗：成员管理、个人资料、标记为我、切换家族                                            |
| `modal-offering.js`   | 祭奠弹窗：敬献/送花动效（glow + 烟雾 + 火焰）、统计面板                                   |
| `modal-tools.js`      | 工具弹窗：操作日志、时间线、关系路径分析、忌日提醒                                           |
| `modal-message.js`    | 留言板弹窗：发布留言、留言列表、删除留言                                                |
| `message-carousel.js` | 底部留言轮播：自动加载、无缝滚动、悬停暂停、关闭记忆（sessionStorage）                          |
| `context-menu.js`     | 右键菜单（10 个操作：添加子女/配偶/关联配偶/添加父母/过继/排次/标记为我/编辑/颜色/删除）                  |
| `main.js`             | 应用入口：工具栏事件绑定、搜索（防抖 + 下拉）、布局方向切换、健在/外嫁过滤                             |

> 注意：应用从 `target/classes/static` 提供静态资源。修改前端后需执行
> `./mvnw compile -pl family-tree-web` 复制到 target 并重启方可生效。

## 项目结构

```
Family_Tree/
├── pom.xml                             # 父 POM（版本与模块管理）
├── family-tree-common/                 # 公共层（DTO/VO、枚举、常量、异常、Result）
├── family-tree-domain/                 # 领域层（实体、仓储接口、领域服务、领域事件）
── family-tree-infrastructure/         # 基础设施层（DO、Mapper、Converter、仓储实现）
├── family-tree-application/            # 应用层（用例编排、事务、权限、DTO转换）
├── family-tree-web/                    # 表现层 + 前端静态资源
│   ├── src/main/java/.../web/
│   │   ├── FamilyTreeApplication.java  # 启动类
│   │   ├── config/                     # 全局异常处理、Web MVC 配置
│   │   ├── controller/                 # 14 个 REST 控制器
│   │   ├── filter/                     # CSRF 过滤器、安全响应头过滤器
│   │   └── interceptor/                # 登录拦截器、限流拦截器
│   └── src/main/resources/
│       ├── application*.yml            # 多环境配置
│       ├── db/migration/               # Flyway 迁移（h2 / mysql 双轨）
│       └── static/                     # 前端页面与资源
│           ├── index.html / login.html / family-setup.html
│           ├── css/style.css           # 古典族谱主题样式（CSS 变量）
│           ├── img/longwen.png         # 龙纹水印背景图
│           └── js/ft/                  # 14 个前端模块
├── deploy/                             # 部署配置
│   ├── DEPLOY.md                       # 部署指南
│   ├── nginx/family-tree.conf          # Nginx 反向代理模板
│   ├── monitoring/                     # Prometheus + Grafana 配置
│   └── scripts/                        # 运维脚本（备份/恢复/健康检查/清理/重启/回滚）
├── .github/workflows/                  # CI/CD
│   ├── ci.yml                          # 构建 + 测试（push/PR）
│   └── release.yml                     # 发布流程
├── AGENTS.md                           # 编码规范（基于《Java 开发手册》华山版）
├── OPTIMIZATION.md                     # 系统优化跟踪
└── README.md
```

## 开发提示

- **默认账号**：`admin`（内置真实族谱数据，勿污染）；测试请使用隔离账号 `botest / test123`。
- **端口**：应用监听 `8090`。
- **后端改动生效**：`domain` / `infrastructure` / `application` / `common` 从本地仓库 SNAPSHOT JAR 加载，改动后需
  `./mvnw install -pl <模块> -DskipTests` 并重启应用。
- **逻辑删除**：MyBatis-Plus 全局配置 `deleted` 字段逻辑删除。
- **编码规范**：遵循根目录 `AGENTS.md`（基于《Java 开发手册》华山版裁剪，含 DDD 分层规范）。
- **测试**：测试代码位于 `family-tree-application/src/test/`，使用 Mockito Mock 仓储接口，运行
  `./mvnw test -pl family-tree-application`。

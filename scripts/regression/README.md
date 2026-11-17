# 回归电池（scripts/regression）

端到端功能回归脚本，纯 Python 标准库（`urllib` + `http.cookiejar`），无需安装依赖。
用来在本地 scratch 环境上把认证、族谱主链路、权限矩阵、配置生效链、家族治理、相册、
私信、版本、导出、账号口令等主流程跑一遍，并把失败项打印成报表。

## ⚠️ 只能打本地 scratch 环境

脚本会**注册新账号、创建/删除家族与节点、改配置、冻结归档家族**。
这些动作不可逆地改变目标库，因此只允许指向独立的 scratch 容器 + 独立数据库。
**严禁把 `FT_REG_BASE` 指向生产地址**，也不要在共享库上跑。

## 环境准备

scratch 环境 = Phase 1 修复后的镜像 + 独立库（示例：端口 8093、库 `family_tree_v51reg`）。

```bash
# 1. 构建含修复的镜像
#    1a. 离线/网络差时：本地打包 jar 后只套一层 distroless 运行时（与 Dockerfile 运行阶段等价）
/Users/mac/Tools/apache-maven-3.6.3/bin/mvn -B package            # 顺带跑全量单测（当前基线 487 用例）
mkdir -p /tmp/ft-v51-ctx && cp family-tree-web/target/family-tree-web-1.0.0-SNAPSHOT.jar /tmp/ft-v51-ctx/app.jar
# /tmp/ft-v51-ctx/Dockerfile：FROM gcr.io/distroless/java21-debian12 + COPY app.jar + java -jar 入口
docker build -t family-tree:v51-local /tmp/ft-v51-ctx
#    1b. 走仓库 Dockerfile（多阶段，构建期要联网拉 Maven 依赖）
docker build -t family-tree:v51-local .

# 2. 首次准备 scratch 库（示例用宿主机 3307 的 MySQL，库名单独一个）
#    可从生产备份导一份副本，之后复用即可（Flyway 会自动补迁移）
#    注意 mysqldump 不要加 --databases：那样会带 USE `family_tree`，导入时会写回生产库
docker exec knowledge-mysql mysqldump -uroot -p123456 --single-transaction --routines --triggers family_tree \
  > /tmp/ft-v51reg-seed.sql
docker exec knowledge-mysql mysql -uroot -p123456 \
  -e "CREATE DATABASE IF NOT EXISTS family_tree_v51reg DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;"
docker exec -i knowledge-mysql mysql -uroot -p123456 family_tree_v51reg < /tmp/ft-v51reg-seed.sql

# 3. 起回归容器（/tmp 下挂数据与日志目录，照片目录落在卷内）
docker run -d --name family-tree-reg-v51 -p 8093:8090 \
  -e DB_URL='jdbc:mysql://host.docker.internal:3307/family_tree_v51reg?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Shanghai' \
  -e DB_USERNAME=root -e DB_PASSWORD=123456 \
  -e SPRING_PROFILES_ACTIVE=mysql \
  -e SPRING_DATA_REDIS_HOST=host.docker.internal -e SPRING_DATA_REDIS_PORT=6380 -e SPRING_DATA_REDIS_DATABASE=7 \
  -e PLATFORM_ADMIN_USERNAMES=admin \
  -e FAMILY_TREE_PHOTO_DIR=/app/data/photos \
  -v /tmp/ft-v51reg-data:/app/data -v /tmp/ft-v51reg-logs:/app/logs \
  family-tree:v51-local

# 4. 等到健康
until curl -s -o /dev/null http://127.0.0.1:8093/api/auth/me; do sleep 2; done
```

账号沿用生产备份里的口径（scratch 库是备份的副本）：
平台白名单管理员 `admin`，家族成员账号 `蒙子羽`；新建的测试账号统一口令 `Reg@2026reg`。
按需覆盖：

| 环境变量 | 默认值 | 说明 |
|---|---|---|
| `FT_REG_BASE` | `http://127.0.0.1:8093` | 目标环境根地址 |
| `FT_REG_OWNER_USER` / `FT_REG_OWNER_PWD` | `admin` / `123456` | 平台白名单管理员 |
| `FT_REG_MEMBER_USER` / `FT_REG_MEMBER_PWD` | `蒙子羽` / `123456` | 同家族普通成员 |
| `FT_REG_TEST_PWD` | `Reg@2026reg` | 电池新建账号的统一口令 |

## 三只电池

```bash
cd scripts/regression
python3 r3_readonly.py     # R3 认证/会话/CSRF、R4 只读面、R5 权限矩阵、R8 日志读、D-04 邀请码、D-08 已知项
python3 r4_r9_write.py     # R4 节点/关系/版本写、R8 快照、R9 辈分/留言/私信/纪念日/祭祀/相册/导出/AI/时间线
python3 r6_r10_platform.py # R6 三级配置生效链与平台开关、R7 家族治理、R10 注册建族与一人一家族、R9 账号口令
```

退出码 0 表示没有 FAIL；命中「已知缺陷」的用例记为 KNOWN，不影响退出码。

当前基线（D-01/D-05/D-07 修复后的 `family-tree:v55-local` + 全新 scratch 库
`family_tree_v55reg`、端口 8097、Redis 库 11）：三只电池共 282 项，FAIL 0，KNOWN 2
（均为 r3 的 D-08 未知路径与路径参数类型）。
D-05 家族层配置改回默认已修复，r6 的读回行已改为正例断言并全部通过。
D-01 版本回滚已落地：r4 的回滚段改为「回滚生效 + 写入 ROLLBACK 历史行」两条正例断言（103 项无 KNOWN）。
D-07 家族成员上限改由平台配置 `MAX_FAMILY_MEMBERS` 承担，r6 新增「收紧上限→加入被拒→提交默认值→放行
→回到代码默认」共 5 行，与既有总量上限、节点上限两行一并全部通过。

三只电池分段为 r3 64 项（PASS 62 / KNOWN 2）、r4 103 项、r6 115 项（FAIL 0 / KNOWN 0），退出码均为 0；
全量单测基线 487 例。

电池内新建的测试账号（`regv50a`、`regpwd1` 等）用户名固定，**同一库重复跑会被「用户名已存在」拦住**。
要复跑得先复置 scratch 库：删库后按上面第 2 步重新导入 `/tmp/ft-v51reg-seed.sql`，再重启容器。

## 结果口径

`ft_reg_common.py` 里的 `KNOWN_DEFECTS` 登记尚未修复的缺陷编号：

| 编号 | 现状 |
|---|---|
| D-08 | 未知路径返回 HTTP 200 + `code:500` |

带 `known="D-xx"` 的用例**失败记 KNOWN、意外通过记 FIXED 并计入失败**，
所以修完一个缺陷后要回头把这些行改成正常断言并去掉 `known=`，否则电池会一直红。

## 编写约定

- 断言按 `Result.code` 判定，HTTP 状态只在门拦截（401/403/429）时才与 code 一致；
- 写请求要带 CSRF：`check(..., csrf=True)` 会先从 `/api/auth/me` 取新令牌；
- 写接口按用户限流 30 次/60 秒，命中 429 时骨架会自动等窗口重置重试，
  因此尽量给每个用例分配独立会话，别在一个会话里堆写请求；
- 破坏性配置改动（平台开关、家族冻结/归档、上限）只作用于电池自己创建的「回归家族*」，
  跑完不还原也没关系，不要动真实家族的数据。

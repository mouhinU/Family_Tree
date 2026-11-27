# B 阶段：展示型跨族人物图谱测试报告

- 测试日期：2026-11-26
- 对应需求：在 A 阶段后实现 B 展示型图谱，每阶段执行回归测试并确认不影响原有功能
- 测试结论：**通过**
- 数据库变更：**无**，未新增表、字段或索引

## 1. 交付范围

### 后端

- 新增平台管理员只读接口：
  - `GET /api/admin/graph/families/{familyId}/network`
- 返回指定家族的完整人物网络：
  - 族内全部人物节点
  - 族内亲子、夫妻、过继/收养关系
  - 跨族链接引用的外族端点人物
  - 跨族链接明细与数据质量问题
- 本族节点优先排列，外族端点随后排列。
- 缺失端点保留占位节点并输出异常，不因脏数据中断整张图谱。
- 跨族链接保留 5000 条截断保护；批量节点查询保留 500 个一批的保护。
- 接口使用 `@RequireSystemAdmin` 做平台管理员权限约束。
- 未修改 `FamilyTreeDomainService`，`family_node_link` 未进入原有族谱树构建、世代修复或族内亲缘计算链路。

### 前端

- 跨族图谱页签新增两种模式：
  - 治理视图：沿用 A 阶段家族级关系图
  - 展示视图：单家族人物网络
- 展示视图支持家族选择、人物节点缩放与拖拽、Tooltip、人物点击详情。
- 图中区分：
  - 本族人物与外族端点人物
  - 族内关系与跨族链接
  - 血缘同人与姻亲指认
  - 缺失端点与异常链接
- 详情面板展示人物节点、族内关系、跨族链接及异常信息。
- 原有族谱树页面与 `render.js` 未修改。

## 2. 自动化测试

### 2.1 B 阶段新增用例

| 测试类 | 用例 | 结果 |
|---|---|---|
| `AdminGraphApplicationServiceTest` | 人物网络返回族内节点、外族端点、族内关系与跨族链接 | 通过 |
| `AdminGraphApplicationServiceTest` | 缺失外族端点返回占位节点与异常 | 通过 |
| `AdminGraphApplicationServiceTest` | 未知家族拒绝访问 | 通过 |
| `AdminGraphApplicationServiceTest` | 空家族 ID 拒绝访问 | 通过 |
| `AdminGraphControllerTest` | 网络端点委托应用服务并返回统一响应 | 通过 |
| `AdminGraphControllerTest` | 网络端点具备 `@RequireSystemAdmin` | 通过 |

B 阶段新增 6 个用例，其中应用服务 4 个、控制器 2 个；控制器权限断言与原有用例合并执行。

### 2.2 图谱相关测试汇总

| 测试类 | 用例数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `CrossFamilyLinkQualityDomainServiceTest` | 5 | 0 | 0 | 0 |
| `AdminGraphApplicationServiceTest` | 9 | 0 | 0 | 0 |
| `AdminGraphControllerTest` | 4 | 0 | 0 | 0 |
| `CrossFamilyLinkRepositoryImplTest` | 9 | 0 | 0 | 0 |
| `FamilyRepositoryImplTest` | 1 | 0 | 0 | 0 |
| **合计** | **28** | **0** | **0** | **0** |

### 2.3 定向测试

执行命令：

```bash
./mvnw -pl family-tree-web -am \
  -Dtest=AdminGraphApplicationServiceTest,AdminGraphControllerTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`BUILD SUCCESS`，六个 Maven 模块全部成功。

### 2.4 全量 clean 回归

执行命令：

```bash
./mvnw clean package
```

结果：

```text
MVN_EXIT=0
BUILD SUCCESS
Family Tree ........................................ SUCCESS
Family Tree Common ................................. SUCCESS
Family Tree Domain ................................. SUCCESS
Family Tree Infrastructure ......................... SUCCESS
Family Tree Application ............................ SUCCESS
Family Tree Web .................................... SUCCESS
```

Surefire 汇总：

```text
REPORT_FILES 65
TOTAL tests=708 failures=0 errors=0 skipped=0
```

即：708 个用例，0 失败，0 错误，0 跳过。相比 A 阶段报告中的 703 个用例，本阶段净增 5 个测试方法用例。

### 2.5 静态检查

以下命令均通过：

```bash
node --check family-tree-web/src/main/resources/static/js/ft/admin-graph.js
node --check family-tree-web/src/main/resources/static/js/ft/admin.js
git diff --check
```

## 3. 浏览器验证

使用本地静态页面与 Mock API 验证，未连接生产环境；验证后临时页面已删除，本地静态服务已停止。

| 场景 | 结果 |
|---|---|
| A 阶段治理图谱正常渲染 | 通过：2 个家族节点、1 条聚合边 |
| 切换展示视图并加载家族列表 | 通过 |
| 展示视图人物网络渲染 | 通过：3 个人物节点、2 条边 |
| 本族与外族人物颜色区分 | 通过 |
| 族内关系与跨族链类型区分 | 通过 |
| 人物节点点击详情 | 通过：显示相关族内关系与跨族链接 |
| 返回全网详情 | 通过 |
| 跨族链接类型筛选 | 通过：筛除姻亲链接后仅保留 1 条族内关系 |
| 切回治理视图 | 通过：恢复 2 个家族节点、1 条聚合边 |
| 模式切换后的统计与说明文案 | 通过 |
| 浏览器运行时错误 | 无 |

展示视图截图保存在本机临时目录：`/tmp/ft-phase-b-network.png`。

## 4. 既有功能影响检查

- 全量 708 个既有及新增用例通过，未发现测试回归。
- 未修改原族谱树构建服务、树形数据转换和 `render.js`。
- A 阶段治理图谱接口与交互在全量测试及浏览器模式切换中保持可用。
- 未执行数据库迁移，未修改生产数据，未执行生产部署、提交或推送。

## 5. 结论与限制

B 阶段后端、前端、浏览器 Mock 验证和全量 clean 回归均通过。当前实现保持只读，未改变原有族谱树与详情功能的数据链路。

本机 Redis 不可用，因此未完成真实登录态下的隔离环境端到端验证；浏览器验证使用 Mock API，覆盖了两种图谱模式、人物详情、筛选和模式切换。生产部署不在本阶段执行范围内。

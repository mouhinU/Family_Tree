# A 阶段：治理型跨族图谱测试报告

- 测试日期：2026-11-26
- 对应需求：先实现 A 治理型跨族图谱，再执行回归测试；不得影响原有功能
- 测试结论：**通过**
- 数据库变更：**无**，未新增表、字段或索引

## 1. 交付范围

### 后端

- 新增平台管理员只读接口：
  - `GET /api/admin/graph`：家族级跨族图谱总览
  - `GET /api/admin/graph/families/{familyId}`：单家族链接下钻
- 新增跨族链接质量规则，覆盖：
  - 端点节点缺失
  - 端点家族缺失
  - 节点与链接记录家族不一致
  - 同家族端点
  - 非法链接类型
  - 非法来源
  - 端点顺序异常
  - 非账号来源缺少确认人
- 新增批量节点查询、家族对聚合、异常统计与截断保护。
- 两个接口均使用 `@RequireSystemAdmin` 做平台管理员权限约束。
- `family_node_link` 未进入原有族谱树构建、世代修复、级联删除或族内亲缘计算链路。

### 前端

- 平台管理页新增「跨族图谱」页签。
- 提供家族名称搜索、链接类型筛选、仅看异常、刷新与重置视图。
- 使用 D3 展示家族节点与家族对聚合边，支持缩放、拖拽、 Tooltip 和节点点击下钻。
- 下钻面板展示端点节点、链接类型、来源、确认人、成链时间和异常信息。
- 未修改原有族谱树渲染器。

## 2. 自动化测试

### 2.1 图谱相关测试

| 测试类 | 用例数 | 失败 | 错误 | 跳过 |
|---|---:|---:|---:|---:|
| `CrossFamilyLinkQualityDomainServiceTest` | 5 | 0 | 0 | 0 |
| `AdminGraphApplicationServiceTest` | 5 | 0 | 0 | 0 |
| `AdminGraphControllerTest` | 3 | 0 | 0 | 0 |
| `CrossFamilyLinkRepositoryImplTest` | 9 | 0 | 0 | 0 |
| `FamilyRepositoryImplTest` | 1 | 0 | 0 | 0 |
| **合计** | **23** | **0** | **0** | **0** |

其中本阶段新增用例 15 个，其余为同测试类中的既有仓储用例。

### 2.2 全量 clean 回归

执行命令：

```bash
./mvnw -B clean test
```

结果：

```text
MVN_EXIT=0
BUILD SUCCESS
Reactor Summary:
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
TOTAL [703, 0, 0, 0]
```

即：703 个用例，0 失败，0 错误，0 跳过。

### 2.3 静态检查

以下命令均通过：

```bash
node --check family-tree-web/src/main/resources/static/js/ft/admin.js
node --check family-tree-web/src/main/resources/static/js/ft/admin-graph.js
git diff --check
```

## 3. 浏览器验证

使用本地静态页面与 Mock API 验证前端交互，未连接生产环境。

| 场景 | 结果 |
|---|---|
| 平台管理页启动并显示图谱页签 | 通过 |
| 图谱总览统计卡渲染 | 通过 |
| D3 节点与聚合边可见 | 通过 |
| 节点坐标与边坐标正常生成 | 通过 |
| 点击家族节点加载下钻明细 | 通过 |
| 下钻表格展示节点与链接 | 通过 |
| 异常链接说明正确展示 | 通过 |
| 姻亲类型筛选 | 通过：2 个家族、1 条边 |
| 仅看异常筛选 | 通过：2 个家族、1 条边 |
| 搜索无结果空状态 | 通过 |
| 清除筛选后恢复完整图谱 | 通过：3 个家族、2 条边 |
| 浏览器运行时错误 | 无 |

验证期间发现并修复了一个前端缺陷：D3 `forceLink` 需要边的 `source/target` 字段，而接口返回的是 `srcFamilyId/dstFamilyId`。已在绘图前完成字段映射，并统一使用字符串 ID 进行节点匹配；修复后节点与边均正常渲染。

## 4. 隔离环境启动检查

- 使用独立 H2 数据库启动应用，40 个 H2 迁移执行成功。
- 静态页面、认证配置与图谱前端资源可正常加载。
- 完整登录态端到端验证受本机 Redis 不可用限制，未完成；前端交互改由 Mock API 验证。

## 5. 结论与限制

A 阶段后端、前端和自动化回归均通过，未发现对既有模块的测试回归。未执行生产部署、生产写入、提交或推送。

仍需在具备 Redis 的隔离环境中补做一次真实登录态下的图谱端到端验证；该限制不影响本阶段单元测试与前端 Mock 验证结论。

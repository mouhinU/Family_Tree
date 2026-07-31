# AGENTS.md — Family_Tree 编码规范

> 本规范基于《Java开发手册》v1.5.0（华山版），结合 Family_Tree 项目技术栈（Spring Boot 3.4.1 / Java 21 / MyBatis-Plus
> 3.5.9 / H2 + MySQL / Flyway / D3.js）进行定制化裁剪。**所有 AI 生成的代码必须严格遵循以下规则。**

---

## 一、命名规约

### 1.1 基本命名

- 命名不能以下划线 `_` 或美元符号 `$` 开头或结尾。
- 严禁拼音与英文混合，更不允许直接使用中文命名。国际通用名称（如 `hangzhou`）可视同英文。
- 杜绝不规范的缩写，避免望文不知义（反例：`AbsClass`、`condi`）。

### 1.2 风格要求

| 元素                      | 风格             | 正例                                     | 反例                              |
|-------------------------|----------------|----------------------------------------|---------------------------------|
| 类名                      | UpperCamelCase | `UserService`、`TreeNodeVO`         | `userService`、`TreeNodeVo`  |
| 方法名 / 参数名 / 成员变量 / 局部变量 | lowerCamelCase | `localValue`、`getHttpMessage()`        | `LocalValue`、`gethttpmessage()` |
| 常量                      | 全大写 + 下划线分隔    | `MAX_STOCK_COUNT`、`CACHE_EXPIRED_TIME` | `MAX_COUNT`                     |
| 包名                      | 全小写，单数形式       | `com.mouhin.family.tree.util`          | `com.mouhin.Family.Tree.Utils`  |

- 类名例外（保持全大写后缀）：`DO` / `BO` / `DTO` / `VO` / `AO` / `PO` / `UID`。
- 抽象类以 `Abstract` 或 `Base` 开头；异常类以 `Exception` 结尾；测试类以 `Test` 结尾。
- 枚举类名带 `Enum` 后缀，枚举成员全大写下划线分隔（如 `ProcessStatusEnum.SUCCESS`）。

### 1.3 POJO 布尔属性

- **POJO 类中布尔类型变量不要加 `is` 前缀**，否则部分框架解析会引起序列化错误。
- 反例：`Boolean isDeleted` → 应改为 `Boolean deleted`。

### 1.4 接口与实现

- Service / DAO 层：接口名不加修饰，实现类用 `Impl` 后缀。正例：`CacheServiceImpl` 实现 `CacheService`。
- 形容能力的接口名取形容词：`Translatable`。
- 接口方法不加 `public abstract` 等修饰符，保持简洁。

### 1.5 各层方法命名

| 操作     | 前缀                           |
|--------|------------------------------|
| 获取单个对象 | `get`                        |
| 获取多个对象 | `list`（复数结尾，如 `listObjects`） |
| 获取统计值  | `count`                      |
| 插入     | `save` / `insert`            |
| 删除     | `remove` / `delete`          |
| 修改     | `update`                     |

### 1.6 领域模型命名

- 数据对象：`xxxDO`（xxx 为数据表名）
- 数据传输对象：`xxxDTO`（xxx 为业务领域名称）
- 展示对象：`xxxVO`（xxx 为网页名称）
- 禁止命名成 `xxxPOJO`。

### 1.7 设计模式命名

- 使用设计模式时在命名中体现：`OrderFactory`、`LoginProxy`、`ResourceObserver`。

### 1.8 变量命名细节

- 表示类型的名词放在词尾：`startTime`、`workQueue`、`nameList`。
- 避免子父类成员变量同名，避免不同代码块局部变量同名。

---

## 二、常量定义

- **禁止魔法值**：所有常量必须预先定义，不允许直接出现在代码中。
- `long` / `Long` 赋值时使用大写 `L`，不用小写 `l`（避免与数字 `1` 混淆）。
- 按功能归类维护常量，不要用一个常量类维护所有常量。正例：`CacheConsts`、`ConfigConsts`。
- 变量值仅在固定范围内变化时，使用 `enum` 类型定义。
- 常量复用层次：跨应用共享 → 应用内共享 → 子工程内共享 → 包内共享 → 类内共享。

---

## 三、代码格式

- **缩进**：采用 4 个空格缩进，禁止使用 tab 字符。
- **大括号**：左大括号前不换行，左大括号后换行，右大括号前换行，右大括号后有 `else` 不换行，终止的右大括号后必须换行。空大括号直接写
  `{}`。
- **空格**：
    - 左小括号和字符之间无空格，右小括号和字符之间无空格。
    - `if` / `for` / `while` / `switch` / `do` 等保留字与括号之间加空格。
    - 任何二目、三目运算符左右两边各一个空格。
    - 注释双斜线与注释内容之间有且仅有一个空格。
    - 方法参数逗号后必须加空格。
- **单行字符数**不超过 120 个，超出换行时第二行缩进 4 个空格，运算符和点号与下文一起换行。
- **单方法行数**推荐不超过 80 行。
- 不同逻辑、不同语义的代码之间插入一个空行分隔，不要插入多个空行。
- IDE 编码设置 UTF-8，换行符使用 Unix 格式（LF）。

---

## 四、OOP 规约

- 使用类名（而非对象引用）访问静态变量和静态方法。
- 所有覆写方法必须加 `@Override` 注解。
- 可变参数必须放在参数列表最后，避免使用 `Object` 类型。
- 不允许修改已有接口方法签名；过时接口加 `@Deprecated` 并说明替代方案。
- 不能使用过时的类或方法。
- `equals` 比较：使用常量或确定有值的对象调用 `equals`，推荐使用 `Objects.equals()`。
- 整型包装类之间的值比较，全部使用 `equals`。
- 浮点数等值判断：基本类型不用 `==`，包装类型不用 `equals`，使用误差范围或 `BigDecimal`。
- `BigDecimal` 禁止使用 `new BigDecimal(double)`，使用 `new BigDecimal("0.1")` 或 `BigDecimal.valueOf(0.1)`。
- DO 类属性类型必须与数据库字段类型匹配（`bigint` → `Long`）。
- POJO 类属性必须使用包装数据类型；RPC 方法返回值和参数必须使用包装数据类型；局部变量推荐使用基本数据类型。
- DO/DTO/VO 等 POJO 类不要设定任何属性默认值。
- 构造方法里禁止加入业务逻辑，初始化逻辑放在 `init` 方法中。
- POJO 类必须写 `toString` 方法，继承时加 `super.toString`。
- 禁止 POJO 类中同时存在 `isXxx()` 和 `getXxx()`。
- 循环体内字符串连接使用 `StringBuilder.append`。
- 类内方法顺序：公有/保护方法 > 私有方法 > getter/setter。
- setter 中不加业务逻辑，getter/setter 保持纯粹。

---

## 五、集合处理

- 覆写 `equals` 必须覆写 `hashCode`；Set 存储对象和 Map 键必须同时覆写两者。
- `ArrayList.subList` 不可强转为 `ArrayList`。
- `keySet()` / `values()` / `entrySet()` 返回的集合不可添加元素。
- `Collections.emptyList()` / `singletonList()` 等返回不可变集合，不可修改。
- 对原集合的增加/删除会导致 `subList` 遍历异常。
- 集合转数组使用 `toArray(new String[0])`，不用无参 `toArray()`。
- `addAll()` 的输入集合参数必须进行 NPE 判断。
- `Arrays.asList()` 返回的对象不可 `add` / `remove` / `clear`。
- 泛型通配符：`<? extends T>` 不能 `add`，`<? super T>` 不能 `get`（PECS 原则）。
- **禁止在 `foreach` 循环里进行 `remove` / `add`**，使用 `Iterator` 方式，并发时加锁。
- `Comparator` 实现必须满足自反性、传递性、一致性。
- 集合初始化时指定初始大小。`HashMap` 初始容量 = (元素个数 / 0.75) + 1。
- 使用 `entrySet` 遍历 Map（JDK8+ 使用 `Map.forEach`），不用 `keySet`。
- 注意 Map K/V 是否允许 `null`：`ConcurrentHashMap` 不允许 `null`。
- 利用 Set 唯一性去重，避免 `List.contains` 遍历。

---

## 六、并发处理

- 单例对象及其方法必须保证线程安全。
- 创建线程/线程池时指定有意义的线程名称。
- **线程资源必须通过线程池提供**，不允许自行显式创建线程。
- **线程池不允许使用 `Executors` 创建**，必须通过 `ThreadPoolExecutor` 明确参数。
- `SimpleDateFormat` 线程不安全，定义为 `static` 时必须加锁；JDK8+ 推荐使用 `DateTimeFormatter`。
- **必须回收自定义的 `ThreadLocal` 变量**，使用 `try-finally` 调用 `remove()`。
- 锁粒度从严：无锁数据结构 > 锁区块 > 锁方法体 > 对象锁 > 类锁。
- 多资源加锁必须保持一致的加锁顺序，避免死锁。
- `lock.lock()` 必须在 `try` 代码块之外，`lock` 与 `try` 之间不能有可能抛异常的代码。
- `tryLock` 方式必须先判断当前线程是否持有锁。
- 并发修改同一记录必须加锁：乐观锁（冲突率 < 20%）或悲观锁（资金相关），乐观锁重试次数 ≥ 3。
- 定时任务使用 `ScheduledExecutorService`，不用 `Timer`。
- 双重检查锁实现延迟初始化时，目标属性声明为 `volatile`。
- `ThreadLocal` 使用 `static` 修饰。

### 本项目线程池规范

```java
// 正例：使用 ThreadPoolExecutor 明确参数
@Bean("familyTreeExecutor")
public ThreadPoolExecutor familyTreeExecutor() {
    return new ThreadPoolExecutor(
        4,                                    // corePoolSize
        8,                                    // maximumPoolSize
        60L, TimeUnit.SECONDS,               // keepAliveTime
        new LinkedBlockingQueue<>(1024),      // workQueue
        new ThreadFactoryBuilder().setNameFormat("family-tree-pool-%d").build(),
        new ThreadPoolExecutor.CallerRunsPolicy()
    );
}
```

---

## 七、控制语句

- `switch` 块内每个 `case` 必须以 `break` / `continue` / `return` 终止，或注释说明 fall-through；必须有 `default` 且放在最后。
- `switch` 变量为 `String` 且来自外部参数时，必须先做 `null` 判断。
- `if` / `else` / `for` / `while` / `do` 语句中**必须使用大括号**。
- 高并发场景避免"等于"判断作为中断条件，使用区间判断。
- 表达异常分支时少用 `if-else`，超过 3 层的 `if-else` 使用卫语句、策略模式或状态模式。
- 条件判断中不执行复杂语句，将结果赋值给有意义的布尔变量。
- 不在表达式中插入赋值语句。
- 循环体内考量性能：定义对象、获取连接等操作移至循环体外。
- 避免取反逻辑运算符。
- 接口入参保护：批量操作接口必须限制入参大小。

---

## 八、注释规约

- 类、类属性、类方法注释使用 `/** Javadoc */` 格式，不用 `//`。
- 所有抽象方法必须用 Javadoc 注释，说明功能、参数、返回值、异常。
- **所有类必须添加 `@author` 和 `@date`**。
- 方法内部单行注释在被注释语句上方另起一行，使用 `//`；多行注释使用 `/* */`。
- 所有枚举类型字段必须有注释。
- 推荐用中文注释，专有名词保持英文。
- 代码修改时注释同步修改。
- 谨慎注释掉代码，无用代码直接删除。
- 特殊标记格式：
    - `// TODO (作者, 日期, [预计处理时间]) 描述`
    - `// FIXME (作者, 日期, [预计处理时间]) 描述`

### 本项目类注释模板

```java
/**
 * 用户服务实现类
 *
 * @author mouhinU
 * @date 2026-06-30
 */
@Service
public class UserServiceImpl implements UserService {
    // ...
}
```

---

## 九、异常处理

- 可通过预检查规避的 `RuntimeException` 不用 `catch` 处理（如 `NullPointerException`、`IndexOutOfBoundsException`）。
- 异常不用于流程控制。
- `catch` 时区分稳定代码和非稳定代码，尽可能区分异常类型。
- 捕获异常必须处理，不能空 `catch`；不想处理则抛给调用者。最外层必须处理异常并转化为用户可理解的内容。
- `try` 块在事务代码中，`catch` 后如需回滚必须手动回滚。
- `finally` 块必须关闭资源对象和流对象（推荐 `try-with-resources`）。
- 禁止在 `finally` 中使用 `return`。
- 捕获异常与抛出异常必须完全匹配，或捕获异常是抛异常的父类。
- RPC / 二方包调用必须使用 `Throwable` 拦截。
- 方法返回值可为 `null`，但必须注释说明；防止 NPE 是调用者的责任。
- 使用 `Optional` 防止 NPE。
- 避免直接 `new RuntimeException()`，使用有业务含义的自定义异常。
- 遵循 DRY 原则，抽取共性校验方法。

### 本项目异常处理规范

```java
// 全局异常处理已在 Web 模块的 GlobalExceptionHandler 中实现
// 业务异常使用自定义异常类，携带错误码；单参构造器默认错误码 BIZ_ERROR
public class BusinessException extends RuntimeException {
    private final String errorCode;

    public BusinessException(String errorCode, String errorMessage) {
        super(errorMessage);
        this.errorCode = errorCode;
    }

    public BusinessException(String errorMessage) {
        this("BIZ_ERROR", errorMessage);
    }
}
```

---

## 十、日志规约

- **使用 SLF4J API**，不直接使用 Log4j / Logback API。

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
private static final Logger logger = LoggerFactory.getLogger(XxxService.class);
```

- 日志文件至少保存 15 天。
- 日志输出使用占位符：`logger.debug("Processing trade with id: {} and symbol: {}", id, symbol)`。
- `trace` / `debug` / `info` 级别输出必须进行日志级别开关判断。
- 避免重复打印日志，设置 `additivity=false`。
- 异常日志包含案发现场信息和堆栈信息：`logger.error(params + "_" + e.getMessage(), e)`。
- 生产环境禁止输出 `debug` 日志；有选择地输出 `info` 日志。
- `error` 级别只记录系统逻辑出错、异常或重要错误信息。
- 尽量用英文描述日志错误信息。

---

## 十一、单元测试

- 遵循 AIR 原则：Automatic（自动化）、Independent（独立性）、Repeatable（可重复）。
- 全自动执行，非交互式，使用 `assert` 验证，不用 `System.out`。
- 测试用例之间不互相调用，不依赖执行顺序。
- 测试粒度至多是类级别，一般是方法级别。
- 测试代码写在 `src/test/java` 目录下。
- 语句覆盖率目标 70%，核心模块 100%。
- 遵循 BCDE 原则：Border（边界值）、Correct（正确输入）、Design（结合设计）、Error（错误输入）。
- 数据库相关测试使用程序插入数据，设定自动回滚机制。

---

## 十二、安全规约

- 用户个人页面/功能必须进行权限控制校验（水平权限 + 垂直权限）。
- 用户敏感数据必须脱敏展示（如手机号 `137****0969`）。
- SQL 参数严格使用参数绑定（`#{}`），**禁止字符串拼接 SQL**。
- 用户请求参数必须做有效性验证（防 SQL 注入、ReDoS、内存溢出等）。
- 禁止向 HTML 输出未经安全过滤或转义的用户数据。
- 表单 / AJAX 提交必须执行 CSRF 安全验证。
- 短信 / 邮件 / 支付等平台资源必须实现防重放机制（数量限制、疲劳度控制、验证码）。
- 用户生成内容必须实现防刷和违禁词过滤。

---

## 十三、MySQL 数据库

### 13.1 建表规约

- 是/否字段使用 `is_xxx`（`unsigned tinyint`，1 是 0 否），POJO 中映射时去掉 `is` 前缀。
- 表名、字段名只用小写字母和数字，禁止数字开头，禁止两个下划线中间只有数字。
- 表名不用复数名词。
- 禁用保留字（`desc`、`range`、`match`、`delayed` 等）。
- 索引命名：主键 `pk_字段名`、唯一索引 `uk_字段名`、普通索引 `idx_字段名`。
- 小数类型用 `decimal`，禁止 `float` / `double`。
- 等长字符串用 `char`；`varchar` 不超过 5000，超过用 `text` 独立表。
- **表必备三字段**：`id`（`bigint unsigned` 主键）、`create_time`（`datetime`）、`update_time`（`datetime`）。
- 表命名遵循"业务名称_表的作用"：`alipay_task`、`trade_config`。

### 13.2 索引规约

- 业务唯一字段必须建唯一索引。
- **超过三个表禁止 `JOIN`**；`JOIN` 字段类型必须一致且需有索引。
- `varchar` 索引必须指定长度（一般 20，区分度达 90% 以上）。
- 禁止左模糊 / 全模糊搜索，走搜索引擎。
- `ORDER BY` 利用索引有序性，放在组合索引最后。
- 利用覆盖索引避免回表。
- 组合索引区分度最高的在最左边。
- 防止字段类型隐式转换导致索引失效。

### 13.3 SQL 语句

- 使用 `count(*)` 统计行数，不用 `count(列名)` 或 `count(常量)`。
- `sum()` 注意 NPE：使用 `IFNULL(SUM(column), 0)`。
- 使用 `ISNULL()` 判断 NULL 值。
- 分页查询先判断 `count` 是否为 0，为 0 直接返回。
- **禁止使用外键和级联**。
- **禁止使用存储过程**。
- 数据订正先 `SELECT` 确认再执行。
- `IN` 操作控制在 1000 个元素以内。

### 13.4 ORM 映射

- **禁止使用 `SELECT *`**，明确写出需要的字段。
- POJO 布尔属性不加 `is`，数据库字段加 `is_`，在 `resultMap` 中映射。
- 必须定义 `resultMap`，不用 `resultClass` 直接返回。
- SQL 参数使用 `#{}`，**禁止 `${}`**（防 SQL 注入）。
- 不允许 `HashMap` / `Hashtable` 作为查询结果输出。
- 更新记录时必须同时更新 `update_time`（`gmt_modified`）字段。
- 不写大而全的更新接口，只更新有改动的字段。
- `@Transactional` 不滥用，考虑回滚方案（缓存、搜索引擎、消息补偿等）。

---

## 十四、工程结构

### 14.1 本项目分层架构

```
family-tree-web        → Web 层（Controller、会话认证、全局异常处理、静态资源/前端页面）
family-tree-service    → 业务层（节点、关系、树形结构、辈分、用户服务）
family-tree-persistence→ DAO 层（MyBatis-Plus Mapper + DO 实体）
family-tree-common     → 公共模块（DTO/VO、Result、常量、枚举、业务异常）
```

各业务模块统一采用 **接口 + impl** 分层模式：

- 接口定义在 `tree.service` 包（如 `com.mouhin.family.tree.service.UserService`）
- 实现类在 `tree.service.impl` 包（如 `com.mouhin.family.tree.service.impl.UserServiceImpl`）
- Controller 注入接口类型，Spring 自动匹配实现

### 14.2 分层依赖规则

- 上层依赖下层，不允许反向依赖。
- `web` → `service` → `persistence` → `common`。
- `common` 模块不依赖任何业务模块。

### 14.3 分层异常处理

- **DAO 层**（persistence）：不捕获异常，由 MyBatis-Plus / Spring 向上抛出。
- **Service 层**：业务校验失败抛出 `BusinessException`；系统异常必须记录出错日志到磁盘，带上参数信息。
- **Web 层**：不继续往上抛异常，由 `GlobalExceptionHandler` 统一转化为 `Result`（错误码 + 错误信息）返回。

### 14.4 领域模型

| 模型    | 说明                       | 所在模块                                          |
|-------|--------------------------|-----------------------------------------------|
| DO    | 与数据库表一一对应                | `family-tree-persistence`                     |
| DTO   | 数据传输对象                   | `family-tree-common`                          |
| VO    | 展示层对象                    | `family-tree-common`                          |
| Query | 查询对象（超过 2 个参数禁止用 Map 传输） | 各层接收                                          |

---

## 十五、设计规约

- 存储方案和数据结构设计必须评审通过并沉淀文档。
- 状态超过 3 个使用状态图表达。
- 调用链路涉及对象超过 3 个使用时序图表达。
- 充分评估异常流程与业务边界。
- 类设计遵循单一职责原则。
- 优先使用聚合/组合而非继承。
- 依赖倒置：依赖抽象类与接口。
- 开闭原则：对扩展开放，对修改关闭。
- 共性业务抽取公共模块，避免重复代码（DRY）。

---

## 十六、其它重要规约

- 正则表达式利用预编译功能，不在方法体内定义 `Pattern`。
- 使用 `System.currentTimeMillis()` 获取毫秒数，不用 `new Date().getTime()`。
- 日期格式化年份用小写 `y`（`yyyy-MM-dd HH:mm:ss`），JDK8+ 推荐 `DateTimeFormatter`。
- 任何数据结构的构造或初始化都应指定大小，避免无限增长。
- 及时清理不再使用的代码段或配置信息。
- 不要在视图模板中加入复杂逻辑。
- `Math.random()` 返回 `double`（0 ≤ x < 1），获取整数随机数用 `Random.nextInt()` / `nextLong()`。
- 作者信息统一填写 `@author Family-Tree` + `@date`
- 自动新增.gitignore文件

---

## 十七、Maven 依赖规范

- 依赖版本统一在父 POM `<dependencyManagement>` 中管理。
- 子模块 POM 不指定版本号，继承父 POM。
- 禁止同一 `GroupId` + `ArtifactId` 出现不同 `Version`。
- 线上不依赖 `SNAPSHOT` 版本。
- 同族依赖使用统一版本变量（如 `${spring.version}`）。

---

## 代码生成检查清单

AI 生成代码时，逐条自检：

1. 命名是否符合驼峰规范？常量是否全大写下划线分隔？
2. 是否存在魔法值？
3. 代码格式是否 4 空格缩进？单行是否超过 120 字符？
4. POJO 布尔属性是否避免了 `is` 前缀？
5. 集合操作是否处理了 NPE？`foreach` 中是否有 `remove` / `add`？
6. 线程池是否使用 `ThreadPoolExecutor`？线程是否命名？
7. `ThreadLocal` 是否在 `finally` 中 `remove()`？
8. `switch` 是否有 `default`？`case` 是否终止或注释？
9. 类是否有 Javadoc（`@author` + `@date`）？抽象方法是否有注释？
10. 日志是否使用 SLF4J？是否使用占位符？
11. SQL 是否使用 `#{}` 参数绑定？是否避免了 `SELECT *`？
12. 异常是否被正确处理？是否存在空 `catch`？
13. 安全校验是否到位（权限、参数校验、SQL 注入防护）？
14. 表是否包含 `id`、`create_time`、`update_time` 三字段？

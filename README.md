# 中国企业信息查询

本项目用于查询和保存中国企业公开信息，不依赖商业 API 或密钥。当前主要数据来源是国家企业信用信息公示系统：推荐使用项目自带的 Edge 扩展复用用户普通浏览器会话，用户仅在官网要求时手工完成验证码、安全验证或多候选选择。

## 运行环境

- Windows 10/11；
- Java 17 或更高版本；
- Maven 3.9 或更高版本（从源码启动时需要）；
- Microsoft Edge。

项目仅监听 `127.0.0.1:8080`，企业任务、企业缓存和栏目原文保存在本机 `data` 目录，不会上传 Cookie、账号、密码或本地名单。

## 构建与启动

首次从源码构建：

```bash
mvn clean package
```

开发方式启动：

```bash
mvn spring-boot:run
```

也可以运行构建后的 JAR：

```bash
java -jar target/company-profile-query-0.1.0-SNAPSHOT.jar
```

访问 http://127.0.0.1:8080/

Windows 下重新打包前应先关闭正在运行的项目，否则 JAR 被占用时 Maven 无法替换文件。

## Edge 扩展安装

1. 在 Edge 地址栏打开 `edge://extensions/`。
2. 开启“开发人员模式”。
3. 点击“加载解压缩的扩展”。
4. 选择项目中的 `edge-extension` 文件夹。
5. 将“企业信息自动采集助手”固定到工具栏。

代码更新后，需要在 `edge://extensions/` 页面点击一次“重新加载”，否则 Edge 仍会使用旧版脚本。

## 使用流程

1. 准备 CSV 或 XLSX 企业名单，优先使用“单位名称”“企业名称”或“公司名称”作为表头。
2. 只有一列时，系统默认第一列为企业名称。
3. 上传名单后，系统自动去重，并优先匹配本地已确认企业档案。
4. 在项目页面复制 Edge 扩展配对信息，粘贴到扩展后点击“保存并开始”。
5. 扩展将当前企业名称填入官网搜索框；用户手动点击官网查询并完成验证码或安全验证。
6. 进入结果页后，点击扩展中的“我已完成验证，继续”。
7. 唯一匹配时，扩展在同一个标签页进入详情页，读取所有可见的“全部展开”内容，解析企业基本信息、股东及出资、对外投资、任职信息、经营异常和严重违法失信信息。
8. 搜索结果存在多个候选时扩展会暂停，由用户选择正确企业并进入详情页后继续。
9. 采集成功后自动写入本地任务和企业档案，不需要再次点击保存；基本信息已确认但扩展栏目缺失时进入“待审核”，并继续处理下一家。
10. 页面结构变化、独立分页未加载或采集失败时，点击该企业的“人工补录”，粘贴官网全文并点击“确认并保存”。
11. 点击“导出结果表”导出基本信息 CSV；点击“导出其余信息清单”导出股东、出资、投资、任职、注册资本、经营范围和异常记录等明细。

也可以不导入名单，直接在首页输入一个公司名称，点击“开始单企业查询”。系统会创建只有一条记录的企业任务，使用完整的栏目采集、审核和导出流程。

页面刷新后可以从“恢复历史任务”中重新打开最近 100 个任务。

## 状态说明

| 状态 | 含义 |
|---|---|
| 待采集 | 本地没有可直接采用的档案，需要访问官网 |
| 待审核 | 基本信息已保存，但一个或多个扩展栏目缺失或只能部分解析 |
| 存在冲突 | 当前官网企业与任务名称不一致，或需要用户选择正确企业 |
| 已完成 | 基本信息以及已展示栏目的状态均已确认 |

## 保存和导出

- Edge 扩展采集成功后自动保存到本机 H2 数据库。
- 人工粘贴需要点击“确认并保存”。
- 企业基本信息会进入本地缓存，相同正式名称或已确认别名可在后续任务中复用。
- 删除任务时会同时删除该任务对应的企业分组和栏目原文，不会删除可供其他任务复用的企业缓存。
- CSV 使用 UTF-8 BOM，统一社会信用代码按文本导出。

## 常见问题

- 官网返回 521/403 或“IP 请求异常”：立即停止扩展，不要连续刷新或重试，等待官网解除限制后再继续。
- 官网可以普通打开但扩展没有响应：在 `edge://extensions/` 重新加载扩展，并重新从项目页面复制配对信息。
- 项目重启后配对码失效：重新复制配对信息即可，历史任务和已保存档案不会丢失。
- 控制台出现官网 CSP Worker 提示：这是官网安全策略提示，不应通过注入脚本绕过；当前扩展不会自动点击官网查询按钮。
- 官网独立分页或懒加载内容未出现：系统会标记为“待审核”，使用人工全文补录完善。

官网可能出现验证码、分页、动态加载和访问频率限制。项目不会绕过验证码、访问频率限制或其他访问控制；页面不再提供旧版独立 Playwright 入口，官网不可访问时请使用人工复制全文补录。

## 主要接口

```text
POST /api/company/lookup
POST /api/company/profiles
POST /api/company/profiles/batch
GET  /api/company/credit-code/parse?creditCode=...
GET  /api/company/providers
POST /api/company/import/preview
POST /api/company/import/save

POST /api/company-batches
POST /api/company-batches/manual
GET  /api/company-batches
GET  /api/company-batches/{jobId}
GET  /api/company-batches/{jobId}/companies
POST /api/company-batches/{jobId}/companies/{companyId}/import
POST /api/company-batches/{jobId}/companies/{companyId}/confirm
POST /api/company-batches/{jobId}/companies/{companyId}/all-sections/preview
POST /api/company-batches/{jobId}/companies/{companyId}/all-sections/confirm
GET  /api/company-batches/{jobId}/companies/{companyId}/sections
GET  /api/company-batches/{jobId}/export
GET  /api/company-batches/{jobId}/other-information
GET  /api/company-batches/{jobId}/export/other-information
DELETE /api/company-batches/{jobId}

GET  /api/edge-extension/pairing
GET  /api/edge-extension/jobs/{jobId}/next
POST /api/edge-extension/jobs/{jobId}/companies/{companyId}/capture

POST /api/company/browser/tasks
GET  /api/company/browser/tasks/{taskId}
POST /api/company/browser/tasks/{taskId}/continue
POST /api/company/browser/tasks/{taskId}/save
DELETE /api/company/browser/tasks/{taskId}
```

项目已移除学生就业模板、毕业去向字段、考生号和学生基础信息校验，不再读取或回写毕业生就业表。历史就业数据库表可以继续存在，但新版本不再使用。

`registeredAddressAreaCode` 表示注册地址对应的行政区划代码；`registrationAuthorityCode` 表示登记机关代码，两者不能混用，也不会从统一社会信用代码推导注册地址行政区划代码。

数据库结构见 [docs/schema.sql](docs/schema.sql)。

## 验证

```bash
mvn test
```

当前版本包含企业身份匹配、栏目解析、缓存、CSV 导入导出、Edge 扩展桥接和任务清理等自动化测试。

# 中国企业信息查询

本项目用于查询和保存中国企业公开信息，不依赖商业 API 或密钥。当前主要数据来源是国家企业信用信息公示系统，用户在普通浏览器中完成验证后，将页面文字复制到本项目解析。

## 启动

```bash
mvn spring-boot:run
```

或运行：

```bash
java -jar target/company-profile-query-0.1.0-SNAPSHOT.jar
```

访问 http://127.0.0.1:8080/

## 使用流程

1. 准备 CSV 或 XLSX 企业名单，优先使用“单位名称”“企业名称”或“公司名称”作为表头。
2. 只有一列时，系统默认第一列为企业名称。
3. 上传名单后，系统自动去重，并优先匹配本地已确认企业档案。
4. 复制当前企业名称，打开国家企业信用信息公示系统，完成搜索和验证。
5. 复制企业页面文字，粘贴到当前企业任务中并解析。
6. 核对企业名称、统一社会信用代码、法定代表人、经营状态、行业、企业类型和注册地址。
7. 确认后保存到本地企业档案，系统自动切换下一家企业。
8. 全部处理完成后导出独立的企业查询结果 CSV。

也可以不导入名单，直接在首页输入一个公司名称，点击“开始单企业查询”。系统会创建只有一条记录的企业任务，使用完整的栏目采集、审核和导出流程。

官网可能出现 403、521、验证码、分页或动态页面。项目不会绕过这些访问控制，人工浏览器复制粘贴是主要采集流程。

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
GET  /api/company-batches/{jobId}
GET  /api/company-batches/{jobId}/companies
POST /api/company-batches/{jobId}/companies/{companyId}/import
POST /api/company-batches/{jobId}/companies/{companyId}/confirm
GET  /api/company-batches/{jobId}/export
DELETE /api/company-batches/{jobId}

POST /api/company/browser/tasks
GET  /api/company/browser/tasks/{taskId}
POST /api/company/browser/tasks/{taskId}/continue
POST /api/company/browser/tasks/{taskId}/save
DELETE /api/company/browser/tasks/{taskId}
```

项目已移除学生就业模板、毕业去向字段、考生号和学生基础信息校验，不再读取或回写毕业生就业表。历史就业数据库表可以继续存在，但新版本不再使用。

`registeredAddressAreaCode` 表示注册地址对应的行政区划代码；`registrationAuthorityCode` 表示登记机关代码，两者不能混用，也不会从统一社会信用代码推导注册地址行政区划代码。

数据库结构见 [docs/schema.sql](docs/schema.sql)。

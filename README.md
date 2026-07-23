# 中国企业信息查询

根据中国企业或单位名称查询并保存以下信息：

- 统一社会信用代码
- 单位行业
- 单位性质
- 注册地址行政区划代码

项目不依赖商业 API 或密钥。目前外部查询仅支持国家企业信用信息公示系统；本地数据库用于保存人工录入或网页解析得到的企业档案。

## 启动

```bash
mvn spring-boot:run
```

或运行已打包文件：

```bash
java -jar target/company-profile-query-0.1.0-SNAPSHOT.jar
```

页面地址：`http://127.0.0.1:8080/`

## 使用流程

批量补全就业数据（推荐）：

1. 在就业模板“学生就业数据”工作表中填写考生号、姓名、毕业去向和单位名称。
2. 打开项目首页，在“就业单位信息批量补全”中上传 `.xlsx` 文件。
3. 系统自动核对学生信息、按单位名称去重，并优先复用本地企业档案。
4. 对“待采集”单位，在普通 Edge/Chrome 中打开国家企业信用信息公示系统，复制结果页或详情页文字并粘贴到对应任务。
5. 核对行业、单位性质和注册地址行政区划代码；处理模板已有值冲突。
6. 所有单位完成后下载最终版。原始文件不会被覆盖；检查版可在任务未完成时随时导出。

同一单位对应多名学生时只需采集和确认一次。上传文件及任务只保存在本机，默认保留 30 天，也可以在页面立即删除。

单单位查询流程：

推荐流程：

1. 在普通 Edge/Chrome 中访问国家企业信用信息公示系统。
2. 手工完成访问验证并打开企业结果页或详情页。
3. 复制页面文字，粘贴到项目的“导入官网页面内容”区域。
4. 点击“解析并预览”，核对结果后点击“确认保存”。
5. 以后可在“单位信息查询”区域按名称或信用代码从本地缓存查询。

浏览器辅助流程：

1. 在“国家企业信用信息公示系统查询”区域输入企业名称或统一社会信用代码。
2. 点击“开始官方网页查询”。系统会打开使用长期共享档案的浏览器窗口。
3. 如果官网要求验证码或人工验证，请在该窗口中完成。
4. 返回本项目页面，点击“继续解析当前页面”。
5. 检查解析结果后点击“保存查询结果”。同一时间只允许一个浏览器任务运行。

官网返回 521、访问频率限制或验证码时，项目无法绕过网站的访问控制，需要等待官网恢复或人工完成验证。

## 主要接口

```text
POST /api/company/lookup
POST /api/company/profiles
POST /api/company/profiles/batch
POST /api/company/lookup/batch
GET  /api/company/credit-code/parse?creditCode=...
GET  /api/company/providers
POST /api/company/import/preview
POST /api/company/import/save

POST   /api/employment-jobs
GET    /api/employment-jobs/{jobId}
GET    /api/employment-jobs/{jobId}/companies
GET    /api/employment-jobs/{jobId}/areas?query=...
POST   /api/employment-jobs/{jobId}/companies/{companyId}/import
POST   /api/employment-jobs/{jobId}/companies/{companyId}/confirm
GET    /api/employment-jobs/{jobId}/export?mode=draft|final
DELETE /api/employment-jobs/{jobId}

POST   /api/company/browser/tasks
GET    /api/company/browser/tasks/{taskId}
POST   /api/company/browser/tasks/{taskId}/continue
POST   /api/company/browser/tasks/{taskId}/save
DELETE /api/company/browser/tasks/{taskId}
```

`registeredAddressAreaCode` 表示注册地址对应的行政区划代码；`registrationAuthorityCode` 表示统一社会信用代码第 3—8 位中的登记管理机关代码，两者不能混用。

数据库结构见 [docs/schema.sql](docs/schema.sql)。

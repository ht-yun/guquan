# Equity Query

可嵌入其他项目的股权查询模块。当前版本提供 Spring Boot REST API、可替换数据源接口、东方财富公开接口数据源、查询记录持久化。

## 功能范围

- 输入 `股票代码/SECUCODE + 人名` 查询，例如 `600519.SH`。
- 输入 `公司名称 + 人名` 查询，若匹配多个上市公司则返回候选列表。
- 通过东方财富公开接口查询上市公司十大股东、十大流通股东。
- 返回公开名单中的持股数量、持股比例、数据来源和报告日期。
- 保存每次查询记录。
- 通过 `EquityDataProvider` 替换或扩展数据源。

注意：当前只查东方财富公开披露的股东名单。未查到只能说明“未在东方财富公开十大股东/十大流通股东名单中查到”，不能证明该人没有持股。

## 启动

```bash
mvn spring-boot:run
```

默认端口：`8080`

浏览器测试页：

```http
GET http://localhost:8080/
```

## 查询接口

```http
POST /api/equity/query
Content-Type: application/json
```

示例 1：使用股票代码查询。

```json
{
  "personName": "香港中央结算有限公司",
  "creditCode": "600519.SH",
  "operatorId": "demo",
  "queryReason": "业务查询"
}
```

示例 2：使用公司名称查询。

```json
{
  "personName": "香港中央结算有限公司",
  "companyName": "贵州茅台",
  "operatorId": "demo",
  "queryReason": "业务查询"
}
```

## 查询记录

```http
GET /api/equity/records?size=20
GET /api/equity/records/{queryId}
```

## 数据源状态

```http
GET /api/equity/provider
```

## 数据源替换

实现 `EquityDataProvider` 即可接入其他真实数据源。

```java
public interface EquityDataProvider {

    String providerName();

    List<CompanyCandidate> searchCompany(String companyName);

    Optional<CompanyCandidate> getCompanyByCreditCode(String creditCode);

    List<ShareholderHolding> getIndustrialRegistryHoldings(CompanyCandidate company);

    List<ShareholderHolding> getListedCompanyHoldings(CompanyCandidate company);
}
```

后续可继续增加：

- `BusinessApiEquityDataProvider`：企查查、天眼查、爱企查、启信宝等。
- `PublicRegistryDataProvider`：工商登记公开信息。
- `CninfoListedCompanyDataProvider`：巨潮资讯、交易所公告等。

## 状态说明

- `FOUND`：已找到匹配持股信息。
- `NOT_FOUND`：未在当前公开数据源中找到匹配信息。
- `COMPANY_AMBIGUOUS`：公司名称匹配多个主体，需要补充股票代码。
- `PERSON_AMBIGUOUS`：预留给后续身份消歧。
- `DATA_SOURCE_ERROR`：数据源异常。
- `INVALID_REQUEST`：请求参数不足。

## 表结构

生产库表结构参考 [docs/schema.sql](docs/schema.sql)。

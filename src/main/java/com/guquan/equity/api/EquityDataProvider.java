package com.guquan.equity.api;

import com.guquan.equity.model.CompanyCandidate;
import com.guquan.equity.model.PersonHolding;
import com.guquan.equity.model.ShareholderHolding;
import java.util.List;
import java.util.Optional;

public interface EquityDataProvider {

    String providerName();

    List<CompanyCandidate> searchCompany(String companyName);

    Optional<CompanyCandidate> getCompanyByCreditCode(String creditCode);

    List<ShareholderHolding> getIndustrialRegistryHoldings(CompanyCandidate company);

    List<ShareholderHolding> getListedCompanyHoldings(CompanyCandidate company);

    /**
     * 根据人员姓名，在所有上市公司股东列表中搜索该人的持股。
     * 返回带公司上下文的持股列表，每行代表该人出现在某家公司股东名单中的一条记录。
     * <p>
     * 默认实现返回空列表，便于未实现此方法的数据源保持兼容。
     * </p>
     */
    default List<PersonHolding> searchPersonHoldings(String personName) {
        return List.of();
    }
}

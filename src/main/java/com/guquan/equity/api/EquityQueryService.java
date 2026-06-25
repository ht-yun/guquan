package com.guquan.equity.api;

import com.guquan.equity.model.EquityQueryRequest;
import com.guquan.equity.model.EquityQueryResult;

import com.guquan.equity.model.PersonSearchRequest;
import com.guquan.equity.model.PersonSearchResult;

public interface EquityQueryService {

    EquityQueryResult query(EquityQueryRequest request);

    PersonSearchResult searchPerson(PersonSearchRequest request);
}

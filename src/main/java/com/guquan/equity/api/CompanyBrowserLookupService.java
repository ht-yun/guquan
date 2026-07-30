package com.guquan.equity.api;

import com.guquan.equity.model.CompanyBrowserTask;
import com.guquan.equity.model.CompanyBrowserTaskRequest;

public interface CompanyBrowserLookupService {

    CompanyBrowserTask start(CompanyBrowserTaskRequest request);

    CompanyBrowserTask get(String taskId);

    CompanyBrowserTask continueTask(String taskId);

    CompanyBrowserTask selectCandidate(String taskId, String creditCode);

    void close(String taskId);
}

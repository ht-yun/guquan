package com.guquan.equity.api;

import com.guquan.equity.model.CompanyBrowserTask;
import com.guquan.equity.model.CompanyBrowserTaskRequest;

public interface CompanyBrowserLookupService {

    CompanyBrowserTask start(CompanyBrowserTaskRequest request);

    CompanyBrowserTask get(String taskId);

    CompanyBrowserTask continueTask(String taskId);

    void close(String taskId);
}

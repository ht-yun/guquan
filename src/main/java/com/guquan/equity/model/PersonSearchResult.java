package com.guquan.equity.model;

import java.util.List;
import lombok.Builder;
import lombok.Data;

/**
 * 自然人持股搜索结果。
 */
@Data
@Builder
public class PersonSearchResult {

    private String queryId;

    private String personName;

    /** 手机号（回显） */
    private String phoneNumber;

    /** 身份证号（回显） */
    private String idNumber;

    /** 若指定了公司名，回显 */
    private String companyName;

    /** 匹配到的持股列表 */
    private List<PersonHolding> holdings;

    private QueryStatus status;

    private String confidence;

    private String message;
}

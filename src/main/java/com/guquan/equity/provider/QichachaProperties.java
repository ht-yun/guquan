package com.guquan.equity.provider;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 企查查 API 配置。
 * 在 application.yml 中配置：
 * <pre>
 * qichacha:
 *   enabled: false
 *   app-key: 你的AppKey
 *   app-secret: 你的AppSecret
 * </pre>
 * 申请地址：https://open.qichacha.com/
 */
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "qichacha")
public class QichachaProperties {

    /** 是否启用企查查数据源 */
    private boolean enabled = false;

    /** 企查查开放平台 AppKey */
    private String appKey;

    /** 企查查开放平台 AppSecret */
    private String appSecret;

    public boolean isAvailable() {
        return enabled && appKey != null && !appKey.isBlank();
    }
}

package com.guquan.equity.provider;

import java.nio.file.Path;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "company-browser")
public class CompanyBrowserProperties {

    private boolean enabled = true;
    private boolean headless = false;
    private int timeoutMillis = 15000;
    private int maxTaskSeconds = 180;
    private Path userDataDir = Path.of(".browser-data/company-profile");
    private Path screenshotDir = Path.of(".screenshots/company-profile");
}

package com.guquan.equity.provider;

import java.nio.file.Path;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "employment-jobs")
public class EmploymentJobProperties {

    private Path storageDir = Path.of("data", "employment-jobs");
    private int retentionDays = 30;
    private long maxFileSizeBytes = 20L * 1024 * 1024;
    private int maxEmploymentRows = 10_000;
}

package com.guquan.equity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.guquan.equity.provider.CompanyBrowserProperties;
import com.guquan.equity.provider.EmploymentJobProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties({CompanyBrowserProperties.class, EmploymentJobProperties.class})
public class CompanyProfileQueryApplication {

    public static void main(String[] args) {
        SpringApplication.run(CompanyProfileQueryApplication.class, args);
    }
}

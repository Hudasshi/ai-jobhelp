package com.qznu.jobhelper;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan(
        basePackages = "com.qznu.jobhelper",
        annotationClass = Mapper.class
)
public class JobHelperApplication {

    public static void main(String[] args) {
        SpringApplication.run(JobHelperApplication.class, args);
    }

}

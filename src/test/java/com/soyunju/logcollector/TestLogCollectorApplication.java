package com.soyunju.logcollector;

import org.springframework.boot.SpringApplication;

public class TestLogCollectorApplication {

    public static void main(String[] args) {
        SpringApplication.from(LogCollectorApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}

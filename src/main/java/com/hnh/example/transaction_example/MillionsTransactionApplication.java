package com.hnh.example.transaction_example;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

@SpringBootApplication
@EnableAspectJAutoProxy
public class MillionsTransactionApplication {
    public static void main(String[] args) {
        SpringApplication.run(MillionsTransactionApplication.class, args);
    }
}

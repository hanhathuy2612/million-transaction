package com.hnh.example.transaction_example.service.payment;

import java.util.concurrent.Callable;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PaymentGuardService {
    private final Semaphore writeLimiter = new Semaphore(100);

    @Value("${app.payment.guard.timeout:1000}")
    private long timeoutMs;

    public <T> T guardWrite(Callable<T> task) {
        try {
            if (!writeLimiter.tryAcquire(1, timeoutMs, TimeUnit.MILLISECONDS)) {
                throw new RuntimeException("System busy, try again later");
            }
            return task.call();
        } catch (Exception e) {
            if (e instanceof RuntimeException)
                throw (RuntimeException) e;
            throw new RuntimeException("Operation failed", e);
        } finally {
            writeLimiter.release();
        }
    }
}

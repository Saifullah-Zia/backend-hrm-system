package com.hrm.system.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.lang.reflect.Method;
import java.util.concurrent.Executor;

@Slf4j
@Configuration
@EnableAsync
public class AsyncConfig implements AsyncConfigurer {

    @Bean(name = "startupTaskExecutor")
    public Executor startupTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(1);
        executor.setMaxPoolSize(1);
        executor.setQueueCapacity(2);
        executor.setThreadNamePrefix("startup-maint-");
        executor.initialize();
        return executor;
    }

    @Bean(name = "payrollEmailExecutor")
    public Executor payrollEmailExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(4);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("payroll-email-");
        executor.initialize();
        return executor;
    }

    /**
     * NOTE: intentionally NOT overriding getAsyncExecutor() here.
     * startupTaskExecutor is a single-thread, capacity-2 pool meant for
     * startup/maintenance tasks -- it must not silently become the default
     * executor for every unqualified @Async method (e.g. AuditLogExecutor.log),
     * which would serialize all audit writes through a 1-thread queue and
     * start dropping/blocking under load. Leaving getAsyncExecutor()
     * un-overridden keeps Spring's normal default-executor resolution
     * (SimpleAsyncTaskExecutor, or a bean literally named "taskExecutor"
     * if one exists). Opt specific methods into startupTaskExecutor via
     * @Async("startupTaskExecutor") where that pool is actually wanted.
     */

    /**
     * @Async void methods swallow exceptions by default -- if something
     * throws before reaching your own try/catch (e.g. a proxy/config
     * issue, or a RuntimeException thrown by Spring itself), it disappears
     * silently. This surfaces it in logs instead.
     */
    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return (Throwable ex, Method method, Object... params) ->
                log.error("Uncaught async exception in {}.{}() with args {}",
                        method.getDeclaringClass().getSimpleName(),
                        method.getName(), params, ex);
    }
}
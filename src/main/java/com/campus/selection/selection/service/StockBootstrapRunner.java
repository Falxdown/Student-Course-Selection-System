package com.campus.selection.selection.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@Order(20)
@RequiredArgsConstructor
public class StockBootstrapRunner implements ApplicationRunner {

    private final StockRedisService stockRedisService;

    @Override
    public void run(ApplicationArguments args) {
        try {
            stockRedisService.initializeAll();
        } catch (RuntimeException ex) {
            // 中间件尚未就绪时不阻塞应用启动，首次访问课程库存时会再回源同步。
            log.error("Initial stock bootstrap failed; the service will retry on demand", ex);
        }
    }
}

package com.campus.selection.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

@Configuration
public class RedisScriptConfig {

    @Bean
    public DefaultRedisScript<Long> stockDeductScript() {
        return script("lua/stock_deduct.lua");
    }

    @Bean
    public DefaultRedisScript<Long> stockReleaseScript() {
        return script("lua/stock_release.lua");
    }

    @Bean
    public DefaultRedisScript<Long> tokenBucketScript() {
        return script("lua/token_bucket.lua");
    }

    private DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(new ClassPathResource(path)));
        script.setResultType(Long.class);
        return script;
    }
}

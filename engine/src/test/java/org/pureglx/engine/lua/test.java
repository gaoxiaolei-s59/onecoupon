package org.pureglx.engine.lua;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Singleton;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;

import java.util.List;

@SpringBootTest
public class test {
    private final String STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH = "lua/stock_decrement_and_save_user_receive.lua";

    @Autowired
    public StringRedisTemplate stringRedisTemplate;

    @Test
    public void testLua() {
        DefaultRedisScript<List> redisScript = Singleton.get(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH, () -> {
            DefaultRedisScript<List> defaultRedisScript = new DefaultRedisScript<>();
            defaultRedisScript.setScriptSource(
                    new ResourceScriptSource(new ClassPathResource(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH))
            );
            defaultRedisScript.setResultType(List.class);
            return defaultRedisScript;
        });

        List result = stringRedisTemplate.execute(
                redisScript,
                ListUtil.of("coupon_engine:template:2031563926153838593", "222222"),
                String.valueOf(3600), "1"
        );

        /**
         * -- {0, count} 成功，count 为领取后的次数
         * -- {1, 0}     库存不足
         * -- {2, count} 达到上限，count 为当前已领取次数
         */
        int code = ((Number) result.get(0)).intValue();
        int count = ((Number) result.get(1)).intValue();
        System.out.println(code);
        System.out.println(count);

    }

}

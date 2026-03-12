package org.puregxl.merchant.admin;


import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.Test;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateLogDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class CouponTemplateLogTest {


    @Autowired
    private CouponTemplateLogMapper couponTemplateLogMapper;

    private final ExecutorService executorService = new ThreadPoolExecutor(
            10,
            10,
            9999,
            TimeUnit.SECONDS,
            new SynchronousQueue<>(),
            new ThreadPoolExecutor.CallerRunsPolicy()
    );

    private static final int maxCount = 5000;

    private final List<Snowflake> snowflakes = new ArrayList<>(20);

    public CouponTemplateLogDO buildCouponTemplateLogDO() {
        CouponTemplateLogDO couponTemplateLogDO = new CouponTemplateLogDO();
        couponTemplateLogDO.setShopNumber(10001L);
        couponTemplateLogDO.setCouponTemplateId("1");
        couponTemplateLogDO.setOperatorId("1810518709471555585");
        couponTemplateLogDO.setOperationLog("修改优惠券模板库存和领取规则");
        couponTemplateLogDO.setOriginalData("{\"stock\":1000}");
        couponTemplateLogDO.setModifiedData("{\"stock\":1500}");
        return couponTemplateLogDO;
    }


    public void beforeData() {
        for (int i = 0 ; i < 20 ; i++) {
            snowflakes.add(new Snowflake(i));
        }
    }

    @Test
    public void testShardingSphere() throws InterruptedException {
        snowflakes.clear();
        beforeData();
        for (int i  = 0 ; i < maxCount ; i++) {
            executorService.execute(() -> {
                CouponTemplateLogDO build = buildCouponTemplateLogDO();
                build.setShopNumber(snowflakes.get(RandomUtil.randomInt(20)).nextId());
                couponTemplateLogMapper.insert(build);
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
    }
}

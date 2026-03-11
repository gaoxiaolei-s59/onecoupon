package org.puregxl.merchant.admin;


import cn.hutool.core.lang.Snowflake;
import cn.hutool.core.util.RandomUtil;
import org.junit.jupiter.api.Test;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@SpringBootTest
public class TestMerchantAdminAbstractChainHandler {

    @Autowired
    private CouponTemplateMapper couponTemplateMapper;

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

    public CouponTemplateDO build(){

        CouponTemplateDO couponTemplateDO = new CouponTemplateDO();
        couponTemplateDO.setName("新用户满100减20券");
        couponTemplateDO.setShopNumber(10001L);
        couponTemplateDO.setSource(0);
        couponTemplateDO.setTarget(1);
        couponTemplateDO.setGoods("SPU10001");
        couponTemplateDO.setType(1);
        couponTemplateDO.setValidStartTime(LocalDateTime.now());
        couponTemplateDO.setValidEndTime(LocalDateTime.now().plusDays(30));
        couponTemplateDO.setStock(1000);
        couponTemplateDO.setReceiveRule("{\"limitPerUser\":1,\"receiveStartTime\":\"2026-03-10 00:00:00\",\"receiveEndTime\":\"2026-03-31 23:59:59\"}");
        couponTemplateDO.setConsumeRule("{\"minimumAmount\":100,\"discountAmount\":20,\"canStack\":false}");
        couponTemplateDO.setStatus(0);
        return couponTemplateDO;
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
                CouponTemplateDO build = build();
                build.setShopNumber(snowflakes.get(RandomUtil.randomInt(20)).nextId());
                couponTemplateMapper.insert(build);
            });
        }

        executorService.shutdown();
        executorService.awaitTermination(10, TimeUnit.MINUTES);
    }

}

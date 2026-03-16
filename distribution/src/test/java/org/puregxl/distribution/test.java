package org.puregxl.distribution;

import org.junit.jupiter.api.Test;
import org.puregxl.distribution.dao.mapper.CouponTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class test {
    @Autowired
    public CouponTemplateMapper couponTemplateMapper;
    @Test
    void test() {
        couponTemplateMapper.decrementCouponTemplateStock(2031698834727518208L,2031698834817851401L,  1);
    }
}

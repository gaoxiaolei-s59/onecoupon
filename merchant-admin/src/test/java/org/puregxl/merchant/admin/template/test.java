package org.puregxl.merchant.admin.template;

import org.junit.jupiter.api.Test;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class test {

    @Autowired
    public CouponTemplateMapper couponTemplateMapper;


    @Test
    void test() {
        couponTemplateMapper.increaseNumberCouponTemplate(2031698834727518208L, Long.parseLong("2031698834817851401"), 1);

    }
}

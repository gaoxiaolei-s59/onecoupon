package org.puregxl.merchant.admin.service.filter;

import com.alibaba.fastjson2.JSON;
import org.puregxl.framework.exception.ClientException;
import org.puregxl.merchant.admin.common.enums.DiscountTargetEnum;
import org.puregxl.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import org.puregxl.merchant.admin.service.basic.chain.MerchantAdminAbstractChainHandler;
import org.springframework.stereotype.Component;

import java.util.Date;


import static org.puregxl.merchant.admin.common.enums.ChainEnum.MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY;

@Component
public class CouponTemplateBaseVerityChainFilter implements MerchantAdminAbstractChainHandler<CouponTemplateSaveReqDTO> {

    private static final int maxStock = 20000000;

    @Override
    public void handler(CouponTemplateSaveReqDTO requestParam) {
        boolean flag = false;
        for (DiscountTargetEnum value : DiscountTargetEnum.values()) {
            if (requestParam.getTarget() == value.getType()) {
                flag = true;
            }
        }
        if (!flag) {
            throw new ClientException("优惠类型不存在");
        }

        Date now = new Date();
        //有效期时间不能早于当前时间 当前时间不能晚于有效期时间
        if (!requestParam.getValidStartTime().before(now) || !requestParam.getValidEndTime().after(now)) {

        }

        if (requestParam.getStock() < 0 || requestParam.getSource() > maxStock) {
            throw new ClientException("库存信息错误");
        }

        if (!JSON.isValid(requestParam.getReceiveRule())) {
            throw new ClientException("领取规则JSON格式错误");
        }

        if (!JSON.isValid(requestParam.getConsumeRule())) {
            throw new ClientException("消耗规则JSON格式错误");
        }
    }

    @Override
    public String mark() {
        return MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY.name();
    }

    @Override
    public int getOrder() {
        return 30;
    }
}

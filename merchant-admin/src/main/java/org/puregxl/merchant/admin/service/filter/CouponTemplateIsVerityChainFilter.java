package org.puregxl.merchant.admin.service.filter;

import cn.hutool.core.util.ObjectUtil;
import org.puregxl.merchant.admin.common.enums.DiscountTargetEnum;
import org.puregxl.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import org.puregxl.merchant.admin.service.basic.chain.MerchantAdminAbstractChainHandler;
import org.springframework.stereotype.Component;

import static org.puregxl.merchant.admin.common.enums.ChainEnum.MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY;
@Component
public class CouponTemplateIsVerityChainFilter implements MerchantAdminAbstractChainHandler<CouponTemplateSaveReqDTO> {
    @Override
    public void handler(CouponTemplateSaveReqDTO requestParam) {
        if (ObjectUtil.equals(requestParam.getTarget(), DiscountTargetEnum.PRODUCT_SPECIFIC)) {
            //调用商品中台服务
        }
    }

    @Override
    public String mark() {
        return MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY.name();
    }

    @Override
    public int getOrder() {
        return 20;
    }
}

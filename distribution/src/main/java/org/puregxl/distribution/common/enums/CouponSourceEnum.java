package org.puregxl.distribution.common.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 券来源 0：领券中心 1：平台发放 2：店铺领取
 */
@RequiredArgsConstructor
public enum CouponSourceEnum {
    COUPON_CENTER(0, "领券中心"),
    PLATFORM_DISTRIBUTION(1, "平台发放"),
    SHOP_RECEIVE(2, "店铺领取");

    @Getter
    private final int source;

    @Getter
    private final String name;
}
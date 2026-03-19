package org.pureglx.engine.common.constant;

public class EngineRedisConstant {

    /**
     * 优惠卷模版缓存key
     */
    public static final String COUPON_TEMPLATE_KEY = "coupon_engine:template:%s";

    /**
     * 优惠券模板缓存分布式锁 Key
     */
    public static final String LOCK_COUPON_TEMPLATE_KEY = "coupon_engine:lock:template:%s";

    /**
     * 优惠券模板缓存空值 Key
     */
    public static final String COUPON_TEMPLATE_IS_NULL_KEY = "coupon_engine:template_is_null:%s";

    /**
     * 限制用户领取优惠券模板次数缓存 Key
     */
    public static final String USER_COUPON_TEMPLATE_LIMIT_KEY = "coupon_engine:user-template-limit:%s_%s";

    /**
     * 用户已领取优惠券列表模板 Key
     */
    public static final String USER_COUPON_TEMPLATE_LIST_KEY = "coupon_engine:user-template-list:%s";


    /**
     * 优惠券结算单分布式锁 Key
     */
    public static final String LOCK_COUPON_SETTLEMENT_KEY = "one-coupon_engine:lock:coupon-settlement:%d";

}

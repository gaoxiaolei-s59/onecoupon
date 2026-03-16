package org.puregxl.distribution.common.constant;

/**
 * 消息队列常量
 */
public class RocketMQConstant {
    /**
     * 延迟队列topic - 用于处理优惠卷的过期
     */
    public static final String COUPON_TOPIC = "one-coupon-merchant-admin-service-delay-topic";


    /**
     * 消费者组 （延迟队列topic - 用于处理优惠卷的过期）
     */
    public static final String COUPON_TOPIC_GROUP = "one-coupon-merchant-admin-service-consumer-group";

    /**
     * 延迟队列 - 用于处理可靠传输
     */
    public static final String COUPON_TASK_TOPIC = "one-coupon-merchant-admin-service-delay-task-topic";


    /**
     * 消费者组 （延迟队列topic - 用于处理优惠卷的过期）
     */
    public static final String COUPON_TOPIC_TASK_GROUP = "one-coupon-merchant-admin-service-task-consumer-group";


    /**
     * 消费者组 （立即发送优惠卷）
     */
    public static final String COUPON_ACTUAL_TOPIC = "one-coupon-merchant-admin-service-actual-consumer-topic";


    /**
     * 消费者组 （立刻发送优惠卷）
     */
    public static final String COUPON_ACTUAL_TOPIC_GROUP = "one-coupon-merchant-admin-service-actual-consumer-group";


}

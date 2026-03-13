package org.puregxl.merchant.admin.mq.consumer;


import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.puregxl.merchant.admin.common.enums.CouponTemplateStatusEnum;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateMapper;
import org.springframework.stereotype.Component;

import static org.puregxl.merchant.admin.common.constant.RocketMQConstant.COUPON_TOPIC;
import static org.puregxl.merchant.admin.common.constant.RocketMQConstant.COUPON_TOPIC_GROUP;

@Slf4j(topic = "CouponTemplateDelayExecuteStatusConsumer")
@Component
@RocketMQMessageListener(
        topic = COUPON_TOPIC,
        consumerGroup = COUPON_TOPIC_GROUP
)
@RequiredArgsConstructor
public class CouponTemplateDelayExecuteStatusConsumer implements RocketMQListener<JSONObject> {

    private final CouponTemplateMapper couponTemplateMapper;

    /**
     * 设置消息的状态为不可用
     * @param message
     */
    @Override
    public void onMessage(JSONObject message) {
        log.info("[消费者]-定时设置优惠卷过期-执行消费逻辑, 消息体: {}", message.toString());
        LambdaUpdateWrapper<CouponTemplateDO> updateWrapper = Wrappers.lambdaUpdate(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getShopNumber, message.getLong("shopNumber"))
                .eq(CouponTemplateDO::getId, message.getLong("couponTemplateId"))
                .set(CouponTemplateDO::getStatus, CouponTemplateStatusEnum.ENDED.getStatus());

        couponTemplateMapper.update(updateWrapper);
    }
}

package org.puregxl.merchant.admin.mq.consumer;


import com.alibaba.fastjson.JSON;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.puregxl.merchant.admin.common.enums.CouponTemplateStatusEnum;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateMapper;
import org.puregxl.merchant.admin.mq.base.MessageWrapper;
import org.puregxl.merchant.admin.mq.event.CouponTemplateDelayEvent;
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
public class CouponTemplateDelayExecuteStatusConsumer implements RocketMQListener<MessageWrapper<CouponTemplateDelayEvent>> {

    private final CouponTemplateMapper couponTemplateMapper;

    /**
     * 设置消息的状态为不可用
     */
    @Override
    public void onMessage(MessageWrapper<CouponTemplateDelayEvent> messageWrapper) {
        // 开头打印日志，平常可 Debug 看任务参数，线上可报平安（比如消息是否消费，重新投递时获取参数等）
        log.info("[消费者] 优惠券模板定时执行@变更模板表状态 - 执行消费逻辑，消息体：{}", JSON.toJSONString(messageWrapper));

        // 修改指定优惠券模板状态为已结束
        CouponTemplateDelayEvent message = messageWrapper.getMessage();
        LambdaUpdateWrapper<CouponTemplateDO> updateWrapper = Wrappers.lambdaUpdate(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getShopNumber, message.getShopNumber())
                .eq(CouponTemplateDO::getId, message.getCouponTemplateId())
                .set(CouponTemplateDO::getStatus, CouponTemplateStatusEnum.ENDED.getStatus());
        couponTemplateMapper.update(updateWrapper);
    }
}

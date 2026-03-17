package org.puregxl.distribution.mq.consumer;

import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.puregxl.distribution.common.constant.RocketMQConstant;
import org.puregxl.distribution.common.enums.CouponTaskStatusEnum;
import org.puregxl.distribution.common.enums.CouponTemplateStatusEnum;
import org.puregxl.distribution.dao.entity.CouponTaskDO;
import org.puregxl.distribution.dao.entity.CouponTemplateDO;
import org.puregxl.distribution.dao.mapper.CouponTaskMapper;
import org.puregxl.distribution.dao.mapper.CouponTemplateMapper;
import org.puregxl.distribution.dao.mapper.UserCouponMapper;
import org.puregxl.distribution.mq.base.MessageWrapper;
import org.puregxl.distribution.mq.event.CouponTaskExecuteEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Objects;

@RocketMQMessageListener(
        topic = RocketMQConstant.COUPON_ACTUAL_TOPIC,
        consumerGroup = RocketMQConstant.COUPON_ACTUAL_TOPIC_GROUP
)
@Slf4j
@RequiredArgsConstructor
@Component
public class CouponTaskExecuteConsumer implements RocketMQListener<MessageWrapper<CouponTaskExecuteEvent>> {

    private final CouponTaskMapper couponTaskMapper;
    private final CouponTemplateMapper couponTemplateMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserCouponMapper userCouponMapper;

    @Override
    public void onMessage(MessageWrapper<CouponTaskExecuteEvent> messageWrapper) {
        log.info("[消费者] 优惠券推送任务正式执行 - 执行消费逻辑，消息体：{}", JSON.toJSONString(messageWrapper));
        Long couponTaskId = messageWrapper.getMessage().getCouponTaskId();
        CouponTaskDO couponTaskDO = couponTaskMapper.selectById(couponTaskId);
        //先判断优惠卷任务的状态是否为正在执行
        if (!Objects.equals(couponTaskDO.getStatus(), CouponTaskStatusEnum.IN_PROGRESS.getStatus())) {
            log.warn("[消费者] 优惠券推送任务正式执行 - 推送任务记录异常，id：{}", couponTaskDO.getId());
            return;
        }

        //判断优惠卷状态是否正常
        LambdaQueryWrapper<CouponTemplateDO> queryWrappers = Wrappers.lambdaQuery(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getId, couponTaskDO.getCouponTemplateId())
                .eq(CouponTemplateDO::getShopNumber, couponTaskDO.getShopNumber());

        CouponTemplateDO couponTemplateDO = couponTemplateMapper.selectOne(queryWrappers);
        if (!Objects.equals(couponTemplateDO.getStatus(), CouponTemplateStatusEnum.ACTIVE.getStatus())) {
            log.error("[消费者] 优惠券推送任务正式执行 - 推送任务记录异常 - 优惠卷已经过期或者被设置为不存在：{}", couponTemplateDO.getId());
            return;
        }

        //执行消费逻辑
//        ReadExcelDistributionListener readExcelDistributionListener = new ReadExcelDistributionListener(
//                couponTemplateDO,
//                couponTemplateMapper,
//                stringRedisTemplate,
//                userCouponMapper,
//                couponTaskId,
//                couponTaskMapper
//        );
//
//        EasyExcel.read(couponTaskDO.getFileAddress(), CouponTaskExcelObject.class, readExcelDistributionListener).sheet().doRead();;
    }
}

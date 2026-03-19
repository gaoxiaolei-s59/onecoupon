package org.pureglx.engine.mq.consumer;


import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.pureglx.engine.common.constant.EngineRedisConstant;
import org.pureglx.engine.common.constant.RocketMQConstant;
import org.pureglx.engine.common.context.UserContext;
import org.pureglx.engine.common.enums.CouponStatusEnum;
import org.pureglx.engine.dao.entity.UserCouponDO;
import org.pureglx.engine.dao.mapper.CouponTemplateMapper;
import org.pureglx.engine.dao.mapper.UserCouponMapper;
import org.pureglx.engine.dto.req.CouponTemplateRedeemReqDTO;
import org.pureglx.engine.dto.resp.CouponTemplateQueryRespDTO;
import org.pureglx.engine.mq.base.MessageWrapper;
import org.pureglx.engine.mq.event.UserCouponDelayCloseEvent;
import org.pureglx.engine.mq.event.UserCouponRedeemEvent;
import org.pureglx.engine.mq.producer.UserCouponDelayCloseProducer;
import org.puregxl.framework.exception.ServiceException;
import org.puregxl.framework.indepence.NoMQDuplicateConsume;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@RocketMQMessageListener(
        topic = RocketMQConstant.COUPON_ENGINE_SERVICE_SUER_REDEEM_CLOSE_TOPIC,
        consumerGroup = RocketMQConstant.COUPON_ENGINE_SERVICE_SUER_REDEEM_CLOSE_GROUP
)
@Component
@Slf4j(topic = "UserCouponRedeemEventConsumer")
@RequiredArgsConstructor
public class UserCouponRedeemEventConsumer implements RocketMQListener<MessageWrapper<UserCouponRedeemEvent>> {


    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserCouponDelayCloseProducer userCouponDelayCloseProducer;



    @Transactional(rollbackFor = Exception.class)
    @NoMQDuplicateConsume(
            keyPrefix = "user-coupon-redeem:",
            key = "#messageWrapper.keys",
            keyTimeout = 600
    )
    @Override
    public void onMessage(MessageWrapper<UserCouponRedeemEvent> userCouponRedeemEventMessageWrapper) {
        //打印日志
        log.info("[消费者] 用户兑换优惠券 - 执行消费逻辑，消息体：{}", JSON.toJSONString(userCouponRedeemEventMessageWrapper));
        //提取消息体的变量参数
        UserCouponRedeemEvent message = userCouponRedeemEventMessageWrapper.getMessage();
        CouponTemplateRedeemReqDTO requestParam = message.getRequestParam();
        Integer receiveCount = message.getReceiveCount();
        CouponTemplateQueryRespDTO couponTemplate = message.getCouponTemplate();

        int decremented = couponTemplateMapper.decrementCouponTemplateStock(Long.parseLong(requestParam.getShopNumber()), Long.parseLong(requestParam.getCouponTemplateId()), 1);
        if (!SqlHelper.retBool(decremented)) {
            throw new ServiceException("优惠券已被领取完啦");
        }
        Date now = new Date();

        DateTime validEndTime = DateUtil.offsetHour(now, JSON.parseObject(couponTemplate.getConsumeRule()).getInteger("validityPeriod"));
        UserCouponDO userCouponDO = UserCouponDO.builder()
                .userId(Long.parseLong(UserContext.getUserId()))
                .couponTemplateId(Long.parseLong(requestParam.getCouponTemplateId()))
                .receiveTime(now)
                .receiveCount(receiveCount)
                .validStartTime(now)
                .validEndTime(validEndTime)
                .source(requestParam.getSource())
                .status(CouponStatusEnum.UNUSED.getCode())
                .build();

        //插入数据
        userCouponMapper.insert(userCouponDO);


        //用户领取优惠卷缓存记录
        String userCatchListKey = String.format(EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId());
        String userCouponItemCacheKey = StrUtil.builder()
                .append(requestParam.getCouponTemplateId())
                .append("_")
                .append(userCouponDO.getId())
                .toString();

        stringRedisTemplate.opsForZSet().add(userCatchListKey, userCouponItemCacheKey, now.getTime());

        try{
            Double score = stringRedisTemplate.opsForZSet().score(userCatchListKey, userCouponItemCacheKey);
            //如果没查到可能是执行失败再次执行
            if (score == null) {
                stringRedisTemplate.opsForZSet().add(userCatchListKey, userCouponItemCacheKey, now.getTime());
            }
        } catch (Throwable ex) {
            log.warn("[消费者] 用户兑换优惠券 - 查询Redis用户优惠券记录为空或抛异常，可能Redis宕机或主从复制数据丢失，基础错误信息：{}", ex.getMessage());
            // 如果直接抛异常大概率 Redis 宕机了，所以应该写个延时队列向 Redis 重试放入值。为了避免代码复杂性，这里直接写新增，大家知道最优解决方案即可
            stringRedisTemplate.opsForZSet().add(userCatchListKey, userCouponItemCacheKey, now.getTime());
        }

        // 发送延时消息队列，等待优惠券到期后，将优惠券信息从缓存中删除
        UserCouponDelayCloseEvent userCouponDelayCloseEvent = UserCouponDelayCloseEvent.builder()
                .userId(UserContext.getUserId())
                .userCouponId(String.valueOf(userCouponDO.getId()))
                .couponTemplateId(couponTemplate.getId())
                .delayTime(couponTemplate.getValidEndTime().getTime()).build();

        SendResult sendResult = userCouponDelayCloseProducer.sendMessage(userCouponDelayCloseEvent);
        // 发送消息失败解决方案简单且高效的逻辑之一：打印日志并报警，通过日志搜集并重新投递
        if (ObjectUtil.notEqual(sendResult.getSendStatus().name(), "SEND_OK")) {
            log.warn("[消费者] 用户兑换优惠券 - 执行消费逻辑，发送优惠券关闭延时队列失败，消息参数：{}", JSON.toJSONString(userCouponDelayCloseEvent));
        }
    }

}


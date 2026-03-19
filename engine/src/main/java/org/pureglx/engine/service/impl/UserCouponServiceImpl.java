package org.pureglx.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.date.DateTime;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.StrUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.pureglx.engine.common.constant.EngineRedisConstant;
import org.pureglx.engine.common.context.UserContext;
import org.pureglx.engine.common.enums.CouponStatusEnum;
import org.pureglx.engine.common.enums.RedisStockDecrementErrorEnum;
import org.pureglx.engine.dao.entity.CouponSettlementDO;
import org.pureglx.engine.dao.entity.UserCouponDO;
import org.pureglx.engine.dao.mapper.CouponSettlementMapper;
import org.pureglx.engine.dao.mapper.CouponTemplateMapper;
import org.pureglx.engine.dao.mapper.UserCouponMapper;
import org.pureglx.engine.dto.req.CouponCreatePaymentReqDTO;
import org.pureglx.engine.dto.req.CouponTemplateQueryReqDTO;
import org.pureglx.engine.dto.req.CouponTemplateRedeemReqDTO;
import org.pureglx.engine.dto.resp.CouponTemplateQueryRespDTO;
import org.pureglx.engine.mq.event.UserCouponDelayCloseEvent;
import org.pureglx.engine.mq.event.UserCouponRedeemEvent;
import org.pureglx.engine.mq.producer.UserCouponDelayCloseProducer;
import org.pureglx.engine.mq.producer.UserCouponRedeemEventProducer;
import org.pureglx.engine.service.CouponTemplateService;
import org.pureglx.engine.service.UserCouponService;
import org.puregxl.framework.exception.ClientException;
import org.puregxl.framework.exception.ServiceException;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;


@Service
@RequiredArgsConstructor
@Slf4j
public class UserCouponServiceImpl extends ServiceImpl<UserCouponMapper, UserCouponDO> implements UserCouponService {


    private final CouponTemplateService couponTemplateService;
    private final StringRedisTemplate stringRedisTemplate;
    private final TransactionTemplate transactionTemplate;
    private final CouponTemplateMapper couponTemplateMapper;
    private final UserCouponMapper userCouponMapper;
    private final UserCouponDelayCloseProducer userCouponDelayCloseProducer;
    private final UserCouponRedeemEventProducer userCouponRedeemEventProducer;
    private final RedissonClient redissonClient;
    private final CouponSettlementMapper couponSettlementMapper;

    @Value("${one-coupon.user-coupon-list.save-cache.type}")
    private String userCouponListSaveCacheType;

    private static final String STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH = "lua/stock_decrement_and_save_user_receive.lua";


    @Override
    public void redeemUserCoupon(CouponTemplateRedeemReqDTO requestParam) {
        //查询卷是否存在
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplate(BeanUtil.toBean(requestParam, CouponTemplateQueryReqDTO.class));
        if (couponTemplate == null) {
            throw new ClientException("用户卷不存在 检查数据的合法性");
        }
        //检查当前时间是否合法
        boolean isInTime = DateUtil.isIn(new Date(), couponTemplate.getValidStartTime(), couponTemplate.getValidEndTime());
        if (!isInTime) {
            throw new ClientException("不满足优惠卷使用时间");
        }

        /**
         * 获取消耗规则的每人限制领取数量
         */
        JSONObject jsonObject = JSON.parseObject(couponTemplate.getReceiveRule());
        String limitPerPerson = jsonObject.getString("limitPerPerson");
        String couponTemplateCacheKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        String userCouponTemplateLimitCacheKey = String.format(EngineRedisConstant.USER_COUPON_TEMPLATE_LIMIT_KEY, UserContext.getUserId(), requestParam.getCouponTemplateId());

        //lua脚本 基于给出的key判断当前用户有没有领取条件 如果有加入到redis重 没有的话返回false 和 用户领取的次数 正常扣减库存
        DefaultRedisScript<List> redisScript = Singleton.get(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH, () -> {
            DefaultRedisScript<List> defaultRedisScript = new DefaultRedisScript<>();
            defaultRedisScript.setScriptSource(
                    new ResourceScriptSource(new ClassPathResource(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH))
            );
            defaultRedisScript.setResultType(List.class);
            return defaultRedisScript;
        });


        long expireSeconds = Math.max(
                1,
                (couponTemplate.getValidEndTime().getTime() - System.currentTimeMillis()) / 1000
        );
        List result = stringRedisTemplate.execute(
                redisScript,
                ListUtil.of(couponTemplateCacheKey, userCouponTemplateLimitCacheKey),
                String.valueOf(expireSeconds), limitPerPerson
        );

        if (result == null || result.size() < 2) {
            throw new ClientException("Lua script return result is invalid");
        }

        /**
         * -- {0, count} 成功，count 为领取后的次数
         * -- {1, 0}     库存不足
         * -- {2, count} 达到上限，count 为当前已领取次数
         */
        int code = ((Number) result.get(0)).intValue();
        int count = ((Number) result.get(1)).intValue();

        if (RedisStockDecrementErrorEnum.isFail(code)) {
            throw new ServiceException(RedisStockDecrementErrorEnum.formFailMessage(code));
        }

        transactionTemplate.executeWithoutResult(status -> {
            //先执行扣减库存的操作
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
                    .receiveCount(count)
                    .validStartTime(now)
                    .validEndTime(validEndTime)
                    .source(requestParam.getSource())
                    .status(CouponStatusEnum.UNUSED.getCode())
                    .build();

            //插入数据
            userCouponMapper.insert(userCouponDO);

            // 保存优惠券缓存集合有两个选项：direct 在流程里直接操作，binlog 通过解析数据库日志后操作
            try {
                if (StrUtil.equals(userCouponListSaveCacheType, "direct")) {
                    String userCatchListKey = String.format(EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId());
                    String userCouponItemCacheKey = StrUtil.builder()
                            .append(requestParam.getCouponTemplateId())
                            .append("_")
                            .append(userCouponDO.getId())
                            .toString();

                    stringRedisTemplate.opsForZSet().add(userCatchListKey, userCouponItemCacheKey, now.getTime());

                    // 由于 Redis 在持久化或主从复制的极端情况下可能会出现数据丢失，而我们对指令丢失几乎无法容忍，因此我们采用经典的写后查询策略来应对这一问题
                    try{
                        Double score = stringRedisTemplate.opsForZSet().score(userCatchListKey, userCouponItemCacheKey);
                        //如果没查到可能是执行失败再次执行
                        if (score == null) {
                            stringRedisTemplate.opsForZSet().add(userCatchListKey, userCouponItemCacheKey, now.getTime());
                        }
                    } catch (Throwable ex) {
                        log.warn("查询Redis用户优惠券记录为空或抛异常，可能Redis宕机或主从复制数据丢失，基础错误信息：{}", ex.getMessage());
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
                        log.warn("发送优惠券关闭延时队列失败，消息参数：{}", JSON.toJSONString(userCouponDelayCloseEvent));
                    }
                }
            } catch (Exception ex) {
                status.setRollbackOnly();
                // 优惠券已被领取完业务异常
                if (ex instanceof ServiceException) {
                    throw (ServiceException) ex;
                }
                if (ex instanceof DuplicateKeyException) {
                    log.error("用户重复领取优惠券，用户ID：{}，优惠券模板ID：{}", UserContext.getUserId(), requestParam.getCouponTemplateId());
                    throw new ServiceException("用户重复领取优惠券");
                }
                throw new ServiceException("优惠券领取异常，请稍候再试");
            }
        });
    }

    /**
     * 基于消息队列重构的v2版本
     * @param requestParam
     */
    @Override
    public void redeemUserCouponByMQ(CouponTemplateRedeemReqDTO requestParam) {
        //查询卷是否存在
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplate(BeanUtil.toBean(requestParam, CouponTemplateQueryReqDTO.class));
        if (couponTemplate == null) {
            throw new ClientException("用户卷不存在 检查数据的合法性");
        }
        //检查当前时间是否合法
        boolean isInTime = DateUtil.isIn(new Date(), couponTemplate.getValidStartTime(), couponTemplate.getValidEndTime());
        if (!isInTime) {
            throw new ClientException("不满足优惠卷使用时间");
        }

        /**
         * 获取消耗规则的每人限制领取数量
         */
        JSONObject jsonObject = JSON.parseObject(couponTemplate.getReceiveRule());
        String limitPerPerson = jsonObject.getString("limitPerPerson");
        String couponTemplateCacheKey = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        String userCouponTemplateLimitCacheKey = String.format(EngineRedisConstant.USER_COUPON_TEMPLATE_LIMIT_KEY, UserContext.getUserId(), requestParam.getCouponTemplateId());

        //lua脚本 基于给出的key判断当前用户有没有领取条件 如果有加入到redis重 没有的话返回false 和 用户领取的次数 正常扣减库存
        DefaultRedisScript<List> redisScript = Singleton.get(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH, () -> {
            DefaultRedisScript<List> defaultRedisScript = new DefaultRedisScript<>();
            defaultRedisScript.setScriptSource(
                    new ResourceScriptSource(new ClassPathResource(STOCK_DECREMENT_AND_SAVE_USER_RECEIVE_LUA_PATH))
            );
            defaultRedisScript.setResultType(List.class);
            return defaultRedisScript;
        });


        long expireSeconds = Math.max(
                1,
                (couponTemplate.getValidEndTime().getTime() - System.currentTimeMillis()) / 1000
        );
        List result = stringRedisTemplate.execute(
                redisScript,
                ListUtil.of(couponTemplateCacheKey, userCouponTemplateLimitCacheKey),
                String.valueOf(expireSeconds), limitPerPerson
        );

        if (result == null || result.size() < 2) {
            throw new ClientException("Lua script return result is invalid");
        }

        /**
         * -- {0, count} 成功，count 为领取后的次数
         * -- {1, 0}     库存不足
         * -- {2, count} 达到上限，count 为当前已领取次数
         */
        int code = ((Number) result.get(0)).intValue();
        int count = ((Number) result.get(1)).intValue();

        if (RedisStockDecrementErrorEnum.isFail(code)) {
            throw new ServiceException(RedisStockDecrementErrorEnum.formFailMessage(code));
        }

        UserCouponRedeemEvent userCouponRedeemEvent = UserCouponRedeemEvent.builder()
                .requestParam(requestParam)
                .receiveCount(count)
                .couponTemplate(couponTemplate)
                .userId(UserContext.getUserId()).build();

        SendResult sendResult = userCouponRedeemEventProducer.sendMessage(userCouponRedeemEvent);

        if (ObjectUtil.notEqual(sendResult.getSendStatus().name(), "SEND_OK")) {
            log.warn("发送优惠券兑换消息失败，消息参数：{}", JSON.toJSONString(userCouponRedeemEvent));
        }
    }

    /**
     * 创建核销订单 - 一般由系统调用 - 在用户使用优惠卷的时候发生
     * @param requestParam
     */
    @Override
    public void createPaymentRecord(CouponCreatePaymentReqDTO requestParam) {
        //锁住优惠卷
        RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_SETTLEMENT_KEY, requestParam.getCouponId()));
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ClientException("正在创建优惠券结算单，请稍候再试");
        }

        try {

        } catch () {

        } finally {

        }

    }


}

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
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.pureglx.engine.common.constant.EngineRedisConstant;
import org.pureglx.engine.common.context.UserContext;
import org.pureglx.engine.common.enums.CouponStatusEnum;
import org.pureglx.engine.common.enums.RedisStockDecrementErrorEnum;
import org.pureglx.engine.common.enums.UserCouponStatusEnum;
import org.pureglx.engine.dao.entity.CouponSettlementDO;
import org.pureglx.engine.dao.entity.UserCouponDO;
import org.pureglx.engine.dao.mapper.CouponSettlementMapper;
import org.pureglx.engine.dao.mapper.CouponTemplateMapper;
import org.pureglx.engine.dao.mapper.UserCouponMapper;
import org.pureglx.engine.dto.req.*;
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

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.pureglx.engine.common.constant.EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY;


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
                    String userCatchListKey = String.format(USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId());
                    String userCouponItemCacheKey = StrUtil.builder()
                            .append(requestParam.getCouponTemplateId())
                            .append("_")
                            .append(userCouponDO.getId())
                            .toString();

                    stringRedisTemplate.opsForZSet().add(userCatchListKey, userCouponItemCacheKey, now.getTime());

                    // 由于 Redis 在持久化或主从复制的极端情况下可能会出现数据丢失，而我们对指令丢失几乎无法容忍，因此我们采用经典的写后查询策略来应对这一问题
                    try {
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
     *
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
     *
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
            //查询优惠卷相关
            LambdaQueryWrapper<CouponSettlementDO> couponSettlementDOLambdaQueryWrapper = Wrappers.lambdaQuery(CouponSettlementDO.class)
                    .eq(CouponSettlementDO::getUserId, UserContext.getUserId())
                    .eq(CouponSettlementDO::getCouponId, requestParam.getCouponId())
                    .in(CouponSettlementDO::getStatus, 0, 2);

            if (couponSettlementMapper.selectOne(couponSettlementDOLambdaQueryWrapper) != null) {
                throw new ServiceException("请检查优惠券是否已使用");
            }

            LambdaQueryWrapper<UserCouponDO> settlementDOLambdaQueryWrapper = Wrappers.lambdaQuery(UserCouponDO.class)
                    .eq(UserCouponDO::getId, requestParam.getCouponId())
                    .eq(UserCouponDO::getUserId, UserContext.getUserId());

            UserCouponDO userCouponDO = userCouponMapper.selectOne(settlementDOLambdaQueryWrapper);

            if (ObjectUtil.isNull(userCouponDO)) {
                throw new ServiceException("请检查优惠卷是否存在");
            }

            if (userCouponDO.getValidEndTime().before(new Date())) {
                throw new ServiceException("优惠卷时间已经过期");
            }

            if (userCouponDO.getStatus() != 0) {
                throw new ServiceException("优惠券使用状态异常");
            }

            CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplate(new CouponTemplateQueryReqDTO(requestParam.getShopNumber(), UserContext.getUserId()));
            JSONObject consumeRule = JSONObject.parseObject(couponTemplate.getConsumeRule());
            //计算折扣金额
            BigDecimal discountAmount;
            // 商品专属优惠券
            if (couponTemplate.getTarget().equals(0)) {
                Optional<CouponCreatePaymentGoodsReqDTO> matchedGoods = requestParam.getGoodsList().stream()
                        .filter(each -> each.getGoodsNumber().equals(couponTemplate.getShopNumber()))
                        .findFirst();
                if (matchedGoods.isEmpty()) {
                    throw new ClientException("商品信息与优惠券模板不符");
                }
                CouponCreatePaymentGoodsReqDTO couponCreatePaymentGoodsReqDTO = matchedGoods.get();
                BigDecimal maximumDiscountAmount = consumeRule.getBigDecimal("maximumDiscountAmount");
                //验证折扣金额
                if (!couponCreatePaymentGoodsReqDTO.getGoodsPayableAmount().
                        equals(couponCreatePaymentGoodsReqDTO.getGoodsAmount().subtract(maximumDiscountAmount))) {
                    throw new ClientException("商品折扣后金额异常");
                }

                discountAmount = maximumDiscountAmount;

            } else {// 店铺专属
                if (couponTemplate.getSource() == 0 && !couponTemplate.getShopNumber().equals(requestParam.getShopNumber())) {
                    throw new ClientException("店铺编号不一致");
                }
                BigDecimal termsOfUse = consumeRule.getBigDecimal("termsOfUse");

                if (requestParam.getOrderAmount().compareTo(termsOfUse) < 0) {
                    throw new ClientException("未满使用金额");
                }

                BigDecimal maximumDiscountAmount = consumeRule.getBigDecimal("maximumDiscountAmount");

                switch (couponTemplate.getType()) {
                    case 0: // 立减券
                        discountAmount = maximumDiscountAmount;
                        break;
                    case 1: // 满减券
                        discountAmount = maximumDiscountAmount;
                        break;
                    case 2: // 折扣券
                        BigDecimal discountRate = consumeRule.getBigDecimal("discountRate");
                        discountAmount = requestParam.getOrderAmount().multiply(discountRate);
                        if (discountAmount.compareTo(maximumDiscountAmount) >= 0) {
                            discountAmount = maximumDiscountAmount;
                        }
                        break;
                    default:
                        throw new ClientException("无效的优惠券类型");
                }
            }

            BigDecimal subtract = requestParam.getOrderAmount().subtract(discountAmount);

            if (subtract.compareTo(requestParam.getPayableAmount()) != 0) {
                throw new ClientException("折扣后金额不一致");
            }

            // 通过编程式事务减小事务范围
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    // 创建优惠券结算单记录
                    CouponSettlementDO couponSettlementDO = CouponSettlementDO.builder()
                            .orderId(requestParam.getOrderId())
                            .couponId(requestParam.getCouponId())
                            .userId(Long.parseLong(UserContext.getUserId()))
                            .status(0)
                            .build();
                    couponSettlementMapper.insert(couponSettlementDO);

                    // 变更用户优惠券状态
                    LambdaUpdateWrapper<UserCouponDO> userCouponUpdateWrapper = Wrappers.lambdaUpdate(UserCouponDO.class)
                            .eq(UserCouponDO::getId, requestParam.getCouponId())
                            .eq(UserCouponDO::getUserId, Long.parseLong(UserContext.getUserId()))
                            .eq(UserCouponDO::getStatus, UserCouponStatusEnum.UNUSED.getCode());
                    UserCouponDO updateUserCouponDO = UserCouponDO.builder()
                            .status(UserCouponStatusEnum.LOCKING.getCode())
                            .build();
                    userCouponMapper.update(updateUserCouponDO, userCouponUpdateWrapper);
                } catch (Exception ex) {
                    log.error("创建优惠券结算单失败", ex);
                    status.setRollbackOnly();
                    throw ex;
                }
            });

            // 从用户可用优惠券列表中删除优惠券
            String userCouponItemCacheKey = StrUtil.builder()
                    .append(userCouponDO.getCouponTemplateId())
                    .append("_")
                    .append(userCouponDO.getId())
                    .toString();
            stringRedisTemplate.opsForZSet().remove(String.format(USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId()), userCouponItemCacheKey);

        } finally {
            lock.unlock();
        }

    }

    /**
     * 核销优惠卷 一般在用户支付完成后
     *
     * @param requestParam
     */
    @Override
    public void processPayment(CouponCreateProcessPaymentDTO requestParam) {
        RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_SETTLEMENT_KEY, requestParam.getCouponId()));
        boolean tryLock = lock.tryLock();

        if (!tryLock) {
            throw new ClientException("正在创建优惠券结算单，请稍候再试");
        }


        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    // 变更优惠券结算单状态为已支付
                    //已经支付
                    LambdaQueryWrapper<CouponSettlementDO> settlementDOLambdaQueryWrapper = Wrappers.lambdaQuery(CouponSettlementDO.class)
                            .eq(CouponSettlementDO::getCouponId, requestParam.getCouponId())
                            .eq(CouponSettlementDO::getUserId, UserContext.getUserId());

                    CouponSettlementDO couponSettlementDO = couponSettlementMapper.selectOne(settlementDOLambdaQueryWrapper);

                    if (couponSettlementDO == null) {
                        throw new ClientException("优惠卷不存在");
                    }

                    CouponSettlementDO build = CouponSettlementDO.builder()
                            .status(2) //设置成已经支付
                            .id(couponSettlementDO.getId()).build();
                    int updateById = couponSettlementMapper.updateById(build);
                    if (!SqlHelper.retBool(updateById)) {
                        log.error("核销优惠券结算单异常，请求参数：{}", com.alibaba.fastjson.JSON.toJSONString(requestParam));
                        throw new ServiceException("核销优惠券结算单异常");
                    }
                    // 变更用户优惠券状态
                    LambdaQueryWrapper<UserCouponDO> userCouponDOLambdaQueryWrapper = Wrappers.lambdaQuery(UserCouponDO.class)
                            .eq(UserCouponDO::getId, couponSettlementDO.getId())
                            .eq(UserCouponDO::getUserId, UserContext.getUserId())
                            .eq(UserCouponDO::getStatus, UserCouponStatusEnum.LOCKING.getCode());

                    UserCouponDO userCouponDO = UserCouponDO.builder()
                            .status(UserCouponStatusEnum.USED.getCode())
                            .build();

                    int userCouponUpdated = userCouponMapper.update(userCouponDO, userCouponDOLambdaQueryWrapper);

                    if (!SqlHelper.retBool(userCouponUpdated)) {
                        log.error("修改用户优惠券记录状态已使用异常，请求参数：{}", com.alibaba.fastjson.JSON.toJSONString(requestParam));
                        throw new ServiceException("修改用户优惠券记录状态异常");
                    }
                } catch (ClientException e) {
                    throw new RuntimeException(e);
                } catch (ServiceException e) {
                    log.error("核销优惠卷异常", e);
                    status.setRollbackOnly();
                    throw e;
                }
            });
        } finally {
            lock.unlock();
        }

    }

    /**
     * 退款优惠卷
     * @param requestParam
     */
    @Override
    public void processRefund(CouponProcessRefundReqDTO requestParam) {
        RLock lock = redissonClient.getLock(String.format(EngineRedisConstant.LOCK_COUPON_SETTLEMENT_KEY, requestParam.getCouponId()));
        boolean tryLock = lock.tryLock();
        if (!tryLock) {
            throw new ClientException("正在执行优惠券退款，请稍候再试");
        }

        try {
            transactionTemplate.executeWithoutResult(status -> {
                try {
                    Long userId = Long.parseLong(UserContext.getUserId());

                    LambdaQueryWrapper<CouponSettlementDO> settlementQueryWrapper = Wrappers.lambdaQuery(CouponSettlementDO.class)
                            .eq(CouponSettlementDO::getStatus, 2)
                            .eq(CouponSettlementDO::getCouponId, requestParam.getCouponId())
                            .eq(CouponSettlementDO::getUserId, userId);
                    CouponSettlementDO couponSettlementDO = couponSettlementMapper.selectOne(settlementQueryWrapper);
                    if (couponSettlementDO == null) {
                        throw new ClientException("未找到已支付的优惠券结算单");
                    }

                    CouponSettlementDO settlementUpdateDO = CouponSettlementDO.builder()
                            .id(couponSettlementDO.getId())
                            .status(3)
                            .build();
                    int settlementUpdated = couponSettlementMapper.updateById(settlementUpdateDO);
                    if (!SqlHelper.retBool(settlementUpdated)) {
                        log.error("退款优惠券结算单异常，请求参数：{}", com.alibaba.fastjson.JSON.toJSONString(requestParam));
                        throw new ServiceException("退款优惠券结算单异常");
                    }

                    LambdaQueryWrapper<UserCouponDO> userCouponQueryWrapper = Wrappers.lambdaQuery(UserCouponDO.class)
                            .eq(UserCouponDO::getId, requestParam.getCouponId())
                            .eq(UserCouponDO::getUserId, userId)
                            .eq(UserCouponDO::getStatus, UserCouponStatusEnum.USED.getCode());
                    UserCouponDO currentUserCouponDO = userCouponMapper.selectOne(userCouponQueryWrapper);
                    if (currentUserCouponDO == null) {
                        throw new ClientException("未找到已使用的用户优惠券记录");
                    }

                    UserCouponDO userCouponUpdateDO = UserCouponDO.builder()
                            .status(UserCouponStatusEnum.UNUSED.getCode())
                            .build();
                    int userCouponUpdated = userCouponMapper.update(userCouponUpdateDO, userCouponQueryWrapper);
                    if (!SqlHelper.retBool(userCouponUpdated)) {
                        log.error("退款恢复用户优惠券异常，请求参数：{}", com.alibaba.fastjson.JSON.toJSONString(requestParam));
                        throw new ServiceException("退款恢复用户优惠券异常");
                    }

                    // 仍在有效期内的券才重新放回可用缓存列表
                    if (StrUtil.equals(userCouponListSaveCacheType, "direct")
                            && currentUserCouponDO.getValidEndTime() != null
                            && currentUserCouponDO.getValidEndTime().after(new Date())) {
                        String userCouponListKey = String.format(USER_COUPON_TEMPLATE_LIST_KEY, UserContext.getUserId());
                        String userCouponItemCacheKey = StrUtil.builder()
                                .append(currentUserCouponDO.getCouponTemplateId())
                                .append("_")
                                .append(currentUserCouponDO.getId())
                                .toString();
                        Date scoreTime = Optional.ofNullable(currentUserCouponDO.getReceiveTime()).orElse(new Date());
                        stringRedisTemplate.opsForZSet().add(userCouponListKey, userCouponItemCacheKey, scoreTime.getTime());
                    }
                } catch (ClientException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (ServiceException e) {
                    status.setRollbackOnly();
                    throw e;
                } catch (Exception ex) {
                    log.error("退款优惠券异常，请求参数：{}", com.alibaba.fastjson.JSON.toJSONString(requestParam), ex);
                    status.setRollbackOnly();
                    throw new ServiceException("退款优惠券异常");
                }
            });
        } finally {
            lock.unlock();
        }
    }

}

package org.puregxl.merchant.admin.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.baomidou.mybatisplus.extension.toolkit.SqlHelper;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.starter.annotation.LogRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.puregxl.framework.exception.ClientException;
import org.puregxl.framework.exception.ServiceException;
import org.puregxl.merchant.admin.common.constant.MerchantAdminRedisConstant;
import org.puregxl.merchant.admin.common.context.UserContext;
import org.puregxl.merchant.admin.common.enums.CouponTemplateStatusEnum;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateMapper;
import org.puregxl.merchant.admin.dto.req.CouponTemplateNumberReqDTO;
import org.puregxl.merchant.admin.dto.req.CouponTemplatePageReqDTO;
import org.puregxl.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import org.puregxl.merchant.admin.dto.resp.CouponTemplatePageRespDTO;
import org.puregxl.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;
import org.puregxl.merchant.admin.service.CouponTemplateService;
import org.puregxl.merchant.admin.service.basic.chain.MerchantAdminContext;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static org.puregxl.merchant.admin.common.constant.RocketMQConstant.COUPON_TOPIC;
import static org.puregxl.merchant.admin.common.enums.ChainEnum.MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY;


@Service
@RequiredArgsConstructor
@Slf4j
public class CouponTemplateServiceImpl extends ServiceImpl<CouponTemplateMapper, CouponTemplateDO> implements CouponTemplateService {

    private final CouponTemplateMapper couponTemplateMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final MerchantAdminContext<CouponTemplateSaveReqDTO> merchantAdminContext;
    private final RocketMQTemplate rocketMQTemplate;


    @LogRecord(
            success = """
                    创建优惠券：{{#requestParam.name}}， \
                    优惠对象：{COMMON_ENUM_FUNCTION{'DiscountTargetEnum' + '_' + #requestParam.target}}， \
                    优惠类型：{COMMON_ENUM_FUNCTION{'DiscountTypeEnum' + '_' + #requestParam.type}}， \
                    库存数量：{{#requestParam.stock}}， \
                    优惠商品编码：{{#requestParam.goods}}， \
                    有效期开始时间：{{#requestParam.validStartTime}}， \
                    有效期结束时间：{{#requestParam.validEndTime}}， \
                    领取规则：{{#requestParam.receiveRule}}， \
                    消耗规则：{{#requestParam.consumeRule}};
                    """,
            type = "CouponTemplate",
            bizNo = "{{#bizNo}}",
            extra = "{{#requestParam.toString()}}"
    )
    @Override
    public void createCouponTemplate(CouponTemplateSaveReqDTO requestParam) {

        merchantAdminContext.execute(MERCHANT_ADMIN_CREATE_COUPON_TEMPLATE_KEY.name(), requestParam);

        //新增信息到数据库中
        CouponTemplateDO couponTemplateDO = BeanUtil.toBean(requestParam, CouponTemplateDO.class);
        couponTemplateDO.setShopNumber(UserContext.getShopNumber());
        couponTemplateDO.setStatus(CouponTemplateStatusEnum.ACTIVE.getStatus());
        couponTemplateMapper.insert(couponTemplateDO);
        //模版id需要回调加入到日志上下文中
        LogRecordContext.putVariable("bizNo", couponTemplateDO.getId());

        //调用Redis服务把信息放入redis

        // 缓存预热：通过将数据库的记录序列化成 JSON 字符串放入 Redis 缓存
        CouponTemplateQueryRespDTO actualRespDTO = BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
        Map<String, Object> cacheTargetMap = BeanUtil.beanToMap(actualRespDTO, false, true);
        Map<String, String> actualCacheTargetMap = cacheTargetMap.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                ));

        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        stringRedisTemplate.opsForHash().putAll(couponTemplateCacheKey, actualCacheTargetMap);
        //设置过期时间
        stringRedisTemplate.expire(couponTemplateCacheKey, 30, TimeUnit.MINUTES);

        //计算消息时间戳
        long deliverTimeStamp = couponTemplateDO.getValidEndTime().getTime();

        //使用rocketmq发送定时消息
        String couponTemplateDelayCloseTopic = COUPON_TOPIC;

        JSONObject messageBody = new JSONObject();
        messageBody.put("couponTemplateId", couponTemplateDO.getId());
        messageBody.put("shopNumber", UserContext.getShopNumber());
        //构建消息体

        String messageKeys = UUID.randomUUID().toString();
        Message<JSONObject> message = MessageBuilder
                .withPayload(messageBody)
                .setHeader(MessageConst.PROPERTY_KEYS, messageKeys)
                .build();

        SendResult sendResult;
        try {
            //发送延时消息
            sendResult = rocketMQTemplate.syncSendDeliverTimeMills(couponTemplateDelayCloseTopic, message, deliverTimeStamp);
            log.info("[生产者] 优惠券模板延时关闭 - 发送结果：{}，消息ID：{}，消息Keys：{}", sendResult.getSendStatus(), sendResult.getMsgId(), messageKeys);
        } catch (Exception e) {
            log.error("[生产者]-优惠卷模版-生产者-发送消息失败，消息体: {}" ,couponTemplateDO.getId(), e);
        }
    }

    /**
     * 分页查询优惠卷模版
     * @param requestParam
     * @return
     */
    @Override
    public IPage<CouponTemplatePageRespDTO> pageQueryCouponTemplate(CouponTemplatePageReqDTO requestParam) {
        LambdaQueryWrapper<CouponTemplateDO> queryWrapper = Wrappers.lambdaQuery(CouponTemplateDO.class)
                .like(requestParam.getName() != null, CouponTemplateDO::getName, requestParam.getName())
                .eq(requestParam.getType() != null, CouponTemplateDO::getType, requestParam.getType())
                .like(requestParam.getGoods() != null, CouponTemplateDO::getGoods, requestParam.getGoods())
                .eq(requestParam.getTarget() != null, CouponTemplateDO::getTarget, requestParam.getTarget())
                .eq(CouponTemplateDO::getShopNumber, UserContext.getShopNumber());


        IPage<CouponTemplateDO> selectPage = couponTemplateMapper.selectPage(requestParam, queryWrapper);

        return selectPage.convert(each -> BeanUtil.toBean(each, CouponTemplatePageRespDTO.class));
    }


    /**
     * 增加制定优惠卷发行数量
     * @param requestParam
     */
    @LogRecord(
            success = "增加发行量：{{#requestParam.number}}",
            type = "CouponTemplate",
            bizNo = "{{#requestParam.couponTemplateId}}",
            extra = "{{#modifiedData}}" // 让 extra 绑定到上下文中名叫 modifiedData 的变量
    )
    @Override
    public void increaseNumberCouponTemplate(CouponTemplateNumberReqDTO requestParam) {
        LambdaQueryWrapper<CouponTemplateDO> wrappers = Wrappers.lambdaQuery(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getShopNumber, UserContext.getShopNumber())
                .eq(CouponTemplateDO::getId, requestParam.getCouponTemplateId());

        CouponTemplateDO couponTemplateDO = couponTemplateMapper.selectOne(wrappers);

        //查询数据是否存在
        if (couponTemplateDO == null) {
            throw new ClientException("模版不存在，请检查数据");
        }

        //此处数据已经存在，判断是否有效
        if (ObjectUtil.notEqual(couponTemplateDO.getStatus(), CouponTemplateStatusEnum.ACTIVE.getStatus())) {
            throw new ClientException("优惠卷已经过期");
        }

        // 记录优惠券模板修改前数据

        LogRecordContext.putVariable("originalData", JSON.toJSONString(couponTemplateDO));
        couponTemplateDO.setStock(couponTemplateDO.getStock() + requestParam.getNumber());
        LogRecordContext.putVariable("modifiedData", JSON.toJSONString(couponTemplateDO));
        // 设置数据库优惠券模板增加库存发行量
        int increased = couponTemplateMapper.increaseNumberCouponTemplate(UserContext.getShopNumber(), requestParam.getCouponTemplateId(), requestParam.getNumber());
        if (!SqlHelper.retBool(increased)) {
            throw new ServiceException("优惠券模板增加发行量失败");
        }

        //增加库存
        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        stringRedisTemplate.opsForHash().increment(couponTemplateCacheKey, "stock", requestParam.getNumber());
        stringRedisTemplate.expire(couponTemplateCacheKey, 30, TimeUnit.MINUTES);
    }

    /**
     * 查询优惠卷详细
     * @param couponTemplateId
     * @return
     */
    @Override
    public CouponTemplateQueryRespDTO findCouponTemplate(String couponTemplateId) {
        LambdaQueryWrapper<CouponTemplateDO> wrappers = Wrappers.lambdaQuery(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getId, couponTemplateId)
                .eq(CouponTemplateDO::getShopNumber, UserContext.getShopNumber());

        CouponTemplateDO couponTemplateDO = couponTemplateMapper.selectOne(wrappers);

        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        stringRedisTemplate.expire(couponTemplateCacheKey, 30, TimeUnit.MINUTES);
        return BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
    }

    /**
     * 结束优惠卷
     * @param couponTemplateId
     */
    @LogRecord(
            success = "结束优惠券",
            type = "CouponTemplate",
            bizNo = "{{#couponTemplateId}}"
    )
    @Override
    public void terminateCouponTemplate(String couponTemplateId) {
        LambdaQueryWrapper<CouponTemplateDO> queryWrapper = Wrappers.lambdaQuery(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getShopNumber, UserContext.getShopNumber())
                .eq(CouponTemplateDO::getId, couponTemplateId);

        CouponTemplateDO couponTemplateDO = couponTemplateMapper.selectOne(queryWrapper);

        if (couponTemplateDO == null) {
            throw new ClientException("模版不存在，请检查数据");
        }

        //更新数据
        LambdaQueryWrapper<CouponTemplateDO> updateWrapper = Wrappers.lambdaQuery(CouponTemplateDO.class)
                .eq(CouponTemplateDO::getShopNumber, UserContext.getShopNumber())
                .eq(CouponTemplateDO::getId, couponTemplateId);
        CouponTemplateDO builder = CouponTemplateDO.builder()
                .status(CouponTemplateStatusEnum.ENDED.getStatus()).build();

        // 记录优惠券模板修改前数据
        LogRecordContext.putVariable("originalData", JSON.toJSONString(couponTemplateDO));

        couponTemplateMapper.update(builder, updateWrapper);

        //设置缓存中的值为更新后的值
        String couponTemplateCacheKey = String.format(MerchantAdminRedisConstant.COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        stringRedisTemplate.opsForHash().put(couponTemplateCacheKey, "status", String.valueOf(CouponTemplateStatusEnum.ENDED.getStatus()));
    }

}

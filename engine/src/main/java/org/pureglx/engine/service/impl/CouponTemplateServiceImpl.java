package org.pureglx.engine.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.map.MapUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.RequiredArgsConstructor;
import org.pureglx.engine.common.constant.EngineRedisConstant;
import org.pureglx.engine.common.enums.CouponTemplateStatusEnum;
import org.pureglx.engine.dao.entity.CouponTemplateDO;
import org.pureglx.engine.dao.mapper.CouponTemplateMapper;
import org.pureglx.engine.dto.req.CouponTemplateQueryReqDTO;
import org.pureglx.engine.dto.resp.CouponTemplateQueryRespDTO;
import org.pureglx.engine.service.CouponTemplateService;
import org.puregxl.framework.exception.ClientException;
import org.redisson.api.RBloomFilter;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CouponTemplateServiceImpl extends ServiceImpl<CouponTemplateMapper, CouponTemplateDO> implements CouponTemplateService {

    private final RedissonClient redissonClient;
    private final StringRedisTemplate stringRedisTemplate;
    private final RBloomFilter<String> couponTemplateRBloomFilter;
    private final CouponTemplateMapper couponTemplateMapper;
    @Override
    public CouponTemplateQueryRespDTO findCouponTemplate(CouponTemplateQueryReqDTO requestParam) {
        //先查询缓存中有没有数据
        String couponTemplateKeys = String.format(EngineRedisConstant.COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        Map<Object, Object> couponTemplateCacheMap = stringRedisTemplate.opsForHash().entries(couponTemplateKeys);
        //判断过滤器中是否有数据
        if (!MapUtil.isEmpty(couponTemplateCacheMap)) {
            return BeanUtil.mapToBean(couponTemplateCacheMap, CouponTemplateQueryRespDTO.class, false, CopyOptions.create());
        }

        if (!couponTemplateRBloomFilter.contains(requestParam.getCouponTemplateId())) {
            throw new ClientException("优惠卷模版不存在");
        }

        //查询是否已经缓存了空值
        String couponTemplateIsNullKeys = String.format(EngineRedisConstant.COUPON_TEMPLATE_IS_NULL_KEY, requestParam.getCouponTemplateId());
        Boolean hasKeyFlag = stringRedisTemplate.hasKey(couponTemplateIsNullKeys);
        if (hasKeyFlag) {
            throw new ClientException("优惠卷模版不存在");
        }

        //需要进行缓存重建
        String lockKey = String.format(EngineRedisConstant.LOCK_COUPON_TEMPLATE_KEY, requestParam.getCouponTemplateId());
        RLock lock = redissonClient.getLock(lockKey);
        //先尝试获取锁 - 获取不到直接返回
        lock.lock();

        try {
            //执行重建逻辑
            hasKeyFlag = stringRedisTemplate.hasKey(couponTemplateIsNullKeys);
            if (hasKeyFlag) {
                throw new ClientException("优惠券模板不存在");
            }

            couponTemplateCacheMap = stringRedisTemplate.opsForHash().entries(couponTemplateKeys);

            if (MapUtil.isEmpty(couponTemplateCacheMap)) {
                LambdaQueryWrapper<CouponTemplateDO> queryWrapper = Wrappers.lambdaQuery(CouponTemplateDO.class)
                        .eq(CouponTemplateDO::getShopNumber, requestParam.getShopNumber())
                        .eq(CouponTemplateDO::getId, requestParam.getCouponTemplateId())
                        .eq(CouponTemplateDO::getStatus, CouponTemplateStatusEnum.ACTIVE);

                CouponTemplateDO couponTemplateDO = couponTemplateMapper.selectOne(queryWrapper);


                //说明数据不存在 插入空值
                if (couponTemplateDO == null) {
                    stringRedisTemplate.opsForValue().set(couponTemplateIsNullKeys, "", 30, TimeUnit.MINUTES);
                    throw new ClientException("优惠券模板不存在或已过期");
                }

                //如果查询到了 - 执行缓存重建的过程
                CouponTemplateQueryRespDTO actualRespDTO = BeanUtil.toBean(couponTemplateDO, CouponTemplateQueryRespDTO.class);
                Map<String, Object> cacheTargetMap = BeanUtil.beanToMap(actualRespDTO, false, true);
                Map<String, String> actualCacheTargetMap = cacheTargetMap.entrySet().stream()
                        .collect(Collectors.toMap(
                                Map.Entry::getKey,
                                entry -> entry.getValue() != null ? entry.getValue().toString() : ""
                        ));
                stringRedisTemplate.opsForHash().putAll(couponTemplateKeys, actualCacheTargetMap);

                stringRedisTemplate.expire(couponTemplateKeys, 30, TimeUnit.MINUTES);
                couponTemplateCacheMap = cacheTargetMap.entrySet()
                        .stream()
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
            }
        } finally {
            lock.unlock();
        }

        return BeanUtil.mapToBean(couponTemplateCacheMap, CouponTemplateQueryRespDTO.class, false, CopyOptions.create());
    }
}

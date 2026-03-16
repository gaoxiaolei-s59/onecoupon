package org.puregxl.distribution.service.handler.execel;

import cn.hutool.core.util.StrUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.distribution.common.enums.CouponSourceEnum;
import org.puregxl.distribution.common.enums.CouponStatusEnum;
import org.puregxl.distribution.common.enums.CouponTaskStatusEnum;
import org.puregxl.distribution.dao.entity.CouponTaskDO;
import org.puregxl.distribution.dao.entity.CouponTemplateDO;
import org.puregxl.distribution.dao.entity.UserCouponDO;
import org.puregxl.distribution.dao.mapper.CouponTaskMapper;
import org.puregxl.distribution.dao.mapper.CouponTemplateMapper;
import org.puregxl.distribution.dao.mapper.UserCouponMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

import static org.puregxl.distribution.common.constant.EngineRedisConstant.COUPON_TEMPLATE_KEY;
import static org.puregxl.distribution.common.constant.EngineRedisConstant.USER_COUPON_TEMPLATE_LIST_KEY;

@RequiredArgsConstructor
@Slf4j
public class ReadExcelDistributionListener extends AnalysisEventListener<CouponTaskExcelObject> {
    
    private final CouponTemplateDO couponTemplateDO;
    private final CouponTemplateMapper couponTemplateMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserCouponMapper userCouponMapper;
    private final Long couponTaskId;
    private final CouponTaskMapper couponTaskMapper;
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void invoke(CouponTaskExcelObject couponTaskExcelObject, AnalysisContext analysisContext) {
        //开始执行转发逻辑
        String coachKey = String.format(COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        Long stock = stringRedisTemplate.opsForHash().increment(coachKey, "stock", -1);
        if (stock < 0) {
            //库存不足
            return;
        }
        //扣减数据库的库存
        int decrementResult = couponTemplateMapper.decrementCouponTemplateStock(couponTemplateDO.getShopNumber(), couponTemplateDO.getId(), 1);

        if (decrementResult != 1) {
            return;
        }
        //记录当前时间
        Date now = new Date();

        //构建UserCouponDo
        UserCouponDO userCouponDo = UserCouponDO.builder()
                .userId(Long.parseLong(couponTaskExcelObject.getUserId()))
                .couponTemplateId(couponTemplateDO.getId())
                .receiveTime(now)
                .receiveCount(1)
                .validStartTime(couponTemplateDO.getValidStartTime())
                .validEndTime(couponTemplateDO.getValidEndTime())
                .source(CouponSourceEnum.PLATFORM_DISTRIBUTION.getSource())
                .status(CouponStatusEnum.UNUSED.getCode()).build();

        try {
            userCouponMapper.insert(userCouponDo);
        } catch (Exception e) {
            //（触发唯一索引约束）
            return;
        }

        // 添加优惠券到用户已领取的 Redis 优惠券列表中
        String userCouponCatchKey = String.format(USER_COUPON_TEMPLATE_LIST_KEY, couponTaskExcelObject.getUserId());

        //用户领取优惠卷记录放在ZSet集合 当前领取时间为score
        String value = StrUtil.builder().append(userCouponDo.getId()).append("-").append(couponTemplateDO.getId()).toString();
        stringRedisTemplate.opsForZSet().add(userCouponCatchKey, value, now.getTime());


    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        //执行完成后把数据库中的任务的状态设置成执行完成
        CouponTaskDO couponTaskDO = CouponTaskDO.builder()
                .id(couponTaskId)
                .completionTime(new Date())
                .status(CouponTaskStatusEnum.SUCCESS.getStatus()).build();
        couponTaskMapper.updateById(couponTaskDO);
    }
    
}

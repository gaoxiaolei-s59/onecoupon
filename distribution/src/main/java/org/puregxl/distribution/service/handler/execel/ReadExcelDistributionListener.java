package org.puregxl.distribution.service.handler.execel;

import cn.hutool.core.collection.ListUtil;
import cn.hutool.core.lang.Singleton;
import cn.hutool.core.map.MapBuilder;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.distribution.common.constant.DistributionRedisConstant;
import org.puregxl.distribution.dao.entity.CouponTaskDO;
import org.puregxl.distribution.dao.entity.CouponTaskFailDO;
import org.puregxl.distribution.dao.entity.CouponTemplateDO;
import org.puregxl.distribution.dao.mapper.CouponTaskFailMapper;
import org.puregxl.distribution.dao.mapper.CouponTaskMapper;
import org.puregxl.distribution.dao.mapper.UserCouponMapper;
import org.puregxl.distribution.mq.event.CouponTemplateDistributionEvent;
import org.puregxl.distribution.toolkit.StockDecrementReturnCombinedUtil;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scripting.support.ResourceScriptSource;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.puregxl.distribution.common.constant.EngineRedisConstant.COUPON_TEMPLATE_KEY;

@RequiredArgsConstructor
@Slf4j
public class ReadExcelDistributionListener extends AnalysisEventListener<CouponTaskExcelObject> {

    private final CouponTaskDO couponTaskDO;
    private final CouponTemplateDO couponTemplateDO;
    private final StringRedisTemplate stringRedisTemplate;
    private final UserCouponMapper userCouponMapper;
    private final Long couponTaskId;
    private final CouponTaskMapper couponTaskMapper;
    private final CouponTaskFailMapper couponTaskFailMapper;

    private int rowCount = 1;
    private static final String STOCK_DECREMENT_AND_BATCH_SAVE_USER_RECORD_LUA_PATH = "lua/stock_decrement_and_batch_save_user_record.lua";

    private static final int BATCH_USER_COUPON_SIZE = 5000;
    @Transactional(rollbackFor = Exception.class)
    @Override
    public void invoke(CouponTaskExcelObject couponTaskExcelObject, AnalysisContext analysisContext) {
        //先判断是否宕机过
        Long couponTaskId = couponTaskDO.getId();
        String TemplateTaskExecuteProgressKey = String.format(DistributionRedisConstant.TEMPLATE_TASK_EXECUTE_PROGRESS_KEY, couponTaskId);

        String progress = stringRedisTemplate.opsForValue().get(TemplateTaskExecuteProgressKey);

        if (StrUtil.isNotBlank(progress) && Integer.parseInt(progress) >= rowCount) {
            rowCount++;
            return;
        }

       //封装执行lua脚本的逻辑
        DefaultRedisScript<Long> longDefaultRedisScript = Singleton.get(STOCK_DECREMENT_AND_BATCH_SAVE_USER_RECORD_LUA_PATH, () -> {
            DefaultRedisScript<Long> redisScript = new DefaultRedisScript<>();
            redisScript.setScriptSource(new ResourceScriptSource(new ClassPathResource(STOCK_DECREMENT_AND_BATCH_SAVE_USER_RECORD_LUA_PATH)));
            redisScript.setResultType(Long.class);
            return redisScript;
        });

        //key
        String couponTemplateKey = String.format(COUPON_TEMPLATE_KEY, couponTemplateDO.getId());
        String batchUserSetKey = String.format(DistributionRedisConstant.TEMPLATE_TASK_EXECUTE_BATCH_USER_KEY, couponTaskId);

        //封装用户所在行数和用户id
        Map<Object, Object> userRowNumMap = MapUtil.builder()
                .put("userId", couponTaskExcelObject.getUserId())
                .put("rowCount", rowCount + 1).build();

        Long execute = stringRedisTemplate.execute(longDefaultRedisScript, ListUtil.of(couponTemplateKey, batchUserSetKey), JSONUtil.toJsonStr(userRowNumMap));

        boolean extractFirstField = StockDecrementReturnCombinedUtil.extractFirstField(execute);

        /*
          说明库存已经卖完了
         */
        if (!extractFirstField) {
            //同步缓存中的数据
            stringRedisTemplate.opsForValue().set(TemplateTaskExecuteProgressKey, String.valueOf(rowCount));
            MapBuilder<Object, Object> causeMap = MapUtil.builder()
                    .put("rowCount", rowCount + 1)
                    .put("cause", "优惠券模板无库存");

            CouponTaskFailDO couponTaskFailDO = CouponTaskFailDO.builder()
                    .batchId(couponTaskDO.getBatchId())
                    .jsonObject(JSONUtil.toJsonStr(causeMap)).build();

            couponTaskFailMapper.insert(couponTaskFailDO);
            return;
        }

        int batchUserSetSize = StockDecrementReturnCombinedUtil.extractSecondField(execute.intValue());

        /*
           如果用户行数不等于总条数, 先同步进度 （batchUserSetSize = BATCH_USER_COUPON_SIZE）
         */
        if (batchUserSetSize < BATCH_USER_COUPON_SIZE) {
            stringRedisTemplate.opsForValue().set(TemplateTaskExecuteProgressKey, String.valueOf(rowCount));
            rowCount++;
            return;
        }

        //执行到这里说明 - 操作以及全部完成 - 执行下一步落库逻辑
        CouponTemplateDistributionEvent build = CouponTemplateDistributionEvent.builder()
                .couponTaskId(couponTaskId)
                .couponTaskBatchId(couponTaskDO.getBatchId())
                .couponTemplateId(couponTemplateDO.getId())
                .notifyType(couponTaskDO.getNotifyType())
                .shopNumber(couponTemplateDO.getShopNumber())
                .couponTemplateConsumeRule(couponTemplateDO.getConsumeRule())
                .userId(couponTaskExcelObject.getUserId())
                .phone(couponTaskExcelObject.getPhone())
                .mail(couponTaskExcelObject.getMail())
                .batchUserSetSize(batchUserSetSize)
                .distributionEndFlag(Boolean.FALSE)
                .build();
        stringRedisTemplate.opsForValue().set(TemplateTaskExecuteProgressKey, String.valueOf(rowCount));
        ++rowCount;
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        CouponTemplateDistributionEvent build = CouponTemplateDistributionEvent.builder()
                .couponTaskId(couponTaskId)
                .couponTaskBatchId(couponTaskDO.getBatchId())
                .couponTemplateId(couponTemplateDO.getId())
                .notifyType(couponTaskDO.getNotifyType())
                .shopNumber(couponTemplateDO.getShopNumber())
                .couponTemplateConsumeRule(couponTemplateDO.getConsumeRule())
                .distributionEndFlag(Boolean.TRUE)
                .build();
//        couponExecuteDistributionProducer.sendMessage(couponTemplateExecuteEvent);
    }
    
}

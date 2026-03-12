package org.puregxl.merchant.admin.service.basic.chain.log;

import cn.hutool.core.util.StrUtil;
import com.mzt.logapi.beans.LogRecord;
import com.mzt.logapi.context.LogRecordContext;
import com.mzt.logapi.service.ILogRecordService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.puregxl.merchant.admin.common.context.UserContext;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateLogDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTemplateLogMapper;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Service
@Slf4j
public class DBLogRecordServiceImpl implements ILogRecordService {

    private final CouponTemplateLogMapper couponTemplateLogMapper;

    @Override
    public void record(LogRecord logRecord) {
        switch (logRecord.getType()) {
            case "CouponTemplate" : {
                CouponTemplateLogDO couponTemplateLogDO = CouponTemplateLogDO.builder()
                        .couponTemplateId(logRecord.getBizNo())
                        .operationLog(logRecord.getAction())
                        .shopNumber(UserContext.getShopNumber())
                        .operatorId(UserContext.getUserId())
                        .originalData(Optional.ofNullable(LogRecordContext.getVariable("originalData")).map(Object::toString).orElse(null))
                        .modifiedData(StrUtil.isBlank(logRecord.getExtra()) ? null : logRecord.getExtra())
                        .build();
                couponTemplateLogMapper.insert(couponTemplateLogDO);
            }
        }
    }

    @Override
    public List<LogRecord> queryLog(String bizNo, String type) {
        return List.of();
    }

    @Override
    public List<LogRecord> queryLogByBizNo(String bizNo, String type, String subType) {
        return List.of();
    }
}

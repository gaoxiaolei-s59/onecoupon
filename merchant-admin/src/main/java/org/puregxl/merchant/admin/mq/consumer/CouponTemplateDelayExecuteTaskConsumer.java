package org.puregxl.merchant.admin.mq.consumer;


import com.alibaba.excel.EasyExcel;
import com.alibaba.fastjson2.JSON;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.puregxl.merchant.admin.dao.entity.CouponTaskDO;
import org.puregxl.merchant.admin.dao.mapper.CouponTaskMapper;
import org.puregxl.merchant.admin.mq.base.MessageWrapper;
import org.puregxl.merchant.admin.mq.event.CouponTemplateDelayExecuteTaskEvent;
import org.puregxl.merchant.admin.service.handler.excel.RowCountListener;
import org.springframework.stereotype.Component;

import static org.puregxl.merchant.admin.common.constant.RocketMQConstant.COUPON_TASK_TOPIC;
import static org.puregxl.merchant.admin.common.constant.RocketMQConstant.COUPON_TOPIC_TASK_GROUP;

@Slf4j(topic = "CouponTemplateDelayExecuteTaskConsumer")
@Component
@RocketMQMessageListener(
        topic = COUPON_TASK_TOPIC,
        consumerGroup = COUPON_TOPIC_TASK_GROUP
)
@RequiredArgsConstructor
public class CouponTemplateDelayExecuteTaskConsumer implements RocketMQListener<MessageWrapper<CouponTemplateDelayExecuteTaskEvent>> {

    private final CouponTaskMapper couponTaskMapper;

    /**
     * 消费消息 检查是否正确处理逻辑 否则执行重试逻辑
     */
    @Override
    public void onMessage(MessageWrapper<CouponTemplateDelayExecuteTaskEvent> messageWrapper) {
        log.info("[消费者]-优惠卷传播可靠处理-执行消费逻辑, 消息体: {}", JSON.toJSONString(messageWrapper));
        
        CouponTemplateDelayExecuteTaskEvent message = messageWrapper.getMessage();
        if (message == null) {
            log.warn("[消费者]-优惠券传播可靠处理-消息体异常，缺少message对象: {}", JSON.toJSONString(messageWrapper));
            return;
        }
        
        String fileAddress = message.getFileAddress();
        Long couponTaskId = message.getCouponTaskId();

        //检查原来的逻辑有没有正常执行
        LambdaQueryWrapper<CouponTaskDO> queryWrapper = Wrappers.lambdaQuery(CouponTaskDO.class)
                .eq(CouponTaskDO::getId, couponTaskId);

        CouponTaskDO couponTaskDO = couponTaskMapper.selectOne(queryWrapper);

        if (couponTaskDO == null) {
            log.error("[消费者]-优惠券传播可靠处理-任务不存在, 消息体: {}", JSON.toJSONString(message));
            return;
        }

        if (couponTaskDO.getSendNum() == null) {
            //说明逻辑没有正常执行 执行重试逻辑
            refreshCouponTaskNum(fileAddress, couponTaskId);
        }

    }


    /**
     * 刷新优惠卷分发数据
     * @param file
     * @param couponTaskId
     */
    private void refreshCouponTaskNum(String fileAddress, Long couponTaskId) {
        // 通过 EasyExcel 监听器获取 Excel 中所有行数
        RowCountListener listener = new RowCountListener();
        
        try {
            java.net.URL url = new java.net.URL(fileAddress);
            java.io.InputStream inputStream = url.openStream();
            EasyExcel.read(inputStream, listener).sheet().doRead();
            
            // 为什么需要统计行数？因为发送后需要比对所有优惠券是否都已发放到用户账号
            int totalRows = listener.getRowCount();
            CouponTaskDO updateCouponTaskDO = CouponTaskDO.builder()
                    .id(couponTaskId)
                    .sendNum(totalRows)
                    .build();
            // 刷新保存到数据库的记录
            couponTaskMapper.updateById(updateCouponTaskDO);
            
        } catch (Exception e) {
            log.error("[消费者] - 读取 Excel 文件失败: {}", fileAddress, e);
        }
    }
}

package org.puregxl.merchant.admin.mq;

import com.alibaba.fastjson2.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;
import org.puregxl.merchant.admin.mq.event.CouponTemplateDelayEvent;
import org.puregxl.merchant.admin.mq.producer.CouponTemplateDelayExecuteStatusProductor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

import java.util.UUID;

import static org.puregxl.merchant.admin.common.constant.RocketMQConstant.COUPON_TOPIC;


@Slf4j
@SpringBootTest(properties = "merchant.admin.task.delay.timestamp=1000")
public class productor {

    @Autowired
    private RocketMQTemplate rocketMQTemplate;

    @Autowired
    private  CouponTemplateDelayExecuteStatusProductor couponTemplateDelayExecuteStatusProductor;

    @Autowired
    private org.puregxl.merchant.admin.mq.producer.CouponTemplateDelayExecuteTaskProductor couponTemplateDelayExecuteTaskProductor;


    @Test
    public void main() {
        String couponTemplateDelayCloseTopic = COUPON_TOPIC;

        JSONObject messageBody = new JSONObject();
        messageBody.put("couponTemplateId", "2031698834817851397");
        messageBody.put("shopNumber", "2031698834727550976");
        //构建消息体

        String messageKeys = UUID.randomUUID().toString();
        Message<JSONObject> message = MessageBuilder
                .withPayload(messageBody)
                .setHeader(MessageConst.PROPERTY_KEYS, messageKeys)
                .build();

        SendResult sendResult;
        try {
            //发送延时消息
            sendResult = rocketMQTemplate.syncSendDelayTimeMills(couponTemplateDelayCloseTopic, message, 1000);
            log.info("[生产者] 优惠券模板延时关闭 - 发送结果：{}，消息ID：{}，消息Keys：{}", sendResult.getSendStatus(), sendResult.getMsgId(), messageKeys);
        } catch (Exception e) {
            log.error("[生产者]-优惠卷模版-生产者-发送消息失败，消息体: {}" ,"123456789", e);
        }
    }

    @Test
    public void main1() {

        CouponTemplateDelayEvent couponTemplateDelayEvent = CouponTemplateDelayEvent.builder()
                .shopNumber(2031698838628253697L)
                .couponTemplateId(2031698838626279428L)
                .delayTime(System.currentTimeMillis() + 10_000).build();

        couponTemplateDelayExecuteStatusProductor.sendMessage(couponTemplateDelayEvent);

    }





}

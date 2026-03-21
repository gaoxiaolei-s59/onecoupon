package org.pureglx.engine.controller;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.pureglx.engine.dto.req.CouponCreatePaymentReqDTO;
import org.pureglx.engine.dto.req.CouponCreateProcessPaymentDTO;
import org.pureglx.engine.dto.req.CouponProcessRefundReqDTO;
import org.pureglx.engine.dto.req.CouponTemplateRedeemReqDTO;
import org.pureglx.engine.service.UserCouponService;
import org.puregxl.framework.result.Result;
import org.puregxl.framework.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户优惠卷模版
 */
@RestController
@Slf4j
@RequiredArgsConstructor
public class UserCouponController {

    private final UserCouponService userCouponService;

    @Operation(summary = "兑换优惠券模板", description = "存在较高流量场景，可类比“秒杀”业务")
    @PostMapping("/api/engine/user-coupon/redeem-v1")
    public Result<Void> redeemUserCoupon(@RequestBody CouponTemplateRedeemReqDTO requestParam) {
        userCouponService.redeemUserCoupon(requestParam);
        return Results.success();
    }

    @Operation(summary = "兑换优惠券模板(消息队列)", description = "存在较高流量场景，可类比“秒杀”业务")
    @PostMapping("/api/engine/user-coupon/redeem-v2")
    public Result<Void> redeemUserCouponByMQ(@RequestBody CouponTemplateRedeemReqDTO requestParam) {
        userCouponService.redeemUserCouponByMQ(requestParam);
        return Results.success();
    }

    @Operation(summary = "创建用户优惠券结算单", description = "用户下单时锁定使用的优惠券，一般由订单系统发起调用")
    @PostMapping("/api/engine/user-coupon/create-payment-record")
    public Result<Void> createPaymentRecord(@RequestBody CouponCreatePaymentReqDTO requestParam) {
        userCouponService.createPaymentRecord(requestParam);
        return Results.success();
    }

    @Operation(summary = "核销优惠卷", description = "支付完成结算优惠卷")
    @PostMapping("/api/engine/user-coupon/create-payment-record")
    public Result<Void> processPayment(@RequestBody CouponCreateProcessPaymentDTO requestParam) {
        userCouponService.processPayment(requestParam);
        return Results.success();
    }


    @Operation(summary = "退款优惠卷", description = "退款优惠卷")
    @PostMapping
    public Result<Void> processRefund(@RequestBody CouponProcessRefundReqDTO requestParam) {
        userCouponService.processRefund(requestParam);
        return Results.success();
    }

}

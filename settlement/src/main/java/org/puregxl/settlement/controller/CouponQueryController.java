package org.puregxl.settlement.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.puregxl.framework.result.Result;
import org.puregxl.framework.web.Results;
import org.puregxl.settlement.dto.req.QueryCouponsReqDTO;
import org.puregxl.settlement.dto.resp.QueryCouponsRespDTO;
import org.puregxl.settlement.service.CouponQueryService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "查询用户优惠券管理")
@RequiredArgsConstructor
public class CouponQueryController {


    private final CouponQueryService couponQueryService;

    @Operation(summary = "查询用户可用/不可用优惠券列表")
    @PostMapping("/api/settlement/coupon-query")
    public Result<QueryCouponsRespDTO> listQueryCoupons(@RequestBody QueryCouponsReqDTO requestParam) {
        return Results.success(couponQueryService.listQueryCoupons(requestParam));
    }

    @Operation(summary = "同步查询用户可用/不可用优惠券列表")
    @PostMapping("/api/settlement/coupon-query-sync")
    public Result<QueryCouponsRespDTO> listQueryCouponsBySync(@RequestBody QueryCouponsReqDTO requestParam) {
        return Results.success(couponQueryService.listQueryCouponsBySync(requestParam));
    }
}

package org.pureglx.engine.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.pureglx.engine.dto.req.CouponTemplateQueryReqDTO;
import org.pureglx.engine.dto.resp.CouponTemplateQueryRespDTO;
import org.pureglx.engine.service.CouponTemplateService;
import org.puregxl.framework.result.Result;
import org.puregxl.framework.web.Results;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "优惠卷模版")
public class CouponTemplateController {

    private final CouponTemplateService couponTemplateService;


    @PostMapping("/api/engine/coupon-template/query")
    @Operation(summary = "查询优惠卷模版")
    public Result<CouponTemplateQueryRespDTO> findCouponTemplate(CouponTemplateQueryReqDTO requestParam) {
        CouponTemplateQueryRespDTO result = couponTemplateService.findCouponTemplate(requestParam);
        return Results.success(result);
    }
}

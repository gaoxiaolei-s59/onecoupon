package org.puregxl.merchant.admin.controller;


import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.puregxl.framework.indepence.DuplicateSubmission;
import org.puregxl.framework.result.Result;
import org.puregxl.framework.web.Results;
import org.puregxl.merchant.admin.dto.req.CouponTaskCreateReqDTO;
import org.puregxl.merchant.admin.service.CouponTaskService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Tag(name = "优惠卷推送任务管理")
@RequiredArgsConstructor
public class CouponTaskController {

    private final CouponTaskService couponTaskService;

    @Operation(summary = "执行分发逻辑")
    @DuplicateSubmission(message = "请勿重复增加发行数量")
    @PostMapping("/api/merchant-admin/coupon-task/create")
    public Result<Void> createCouponTask(@RequestBody CouponTaskCreateReqDTO requestParam) {
        couponTaskService.createCouponTask(requestParam);
        return Results.success();
    }
}

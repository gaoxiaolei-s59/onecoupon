package org.puregxl.merchant.admin.controller;


import com.baomidou.mybatisplus.core.metadata.IPage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.puregxl.framework.indepence.DuplicateSubmission;
import org.puregxl.framework.result.Result;
import org.puregxl.framework.web.Results;
import org.puregxl.merchant.admin.dto.req.CouponTemplateNumberReqDTO;
import org.puregxl.merchant.admin.dto.req.CouponTemplatePageReqDTO;
import org.puregxl.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import org.puregxl.merchant.admin.dto.resp.CouponTemplatePageRespDTO;
import org.puregxl.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;
import org.puregxl.merchant.admin.service.CouponTemplateService;
import org.springframework.context.MessageSource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Tag(name = "优惠卷模版")
public class CouponTemplateController {

    private final CouponTemplateService couponTemplateService;
    private final MessageSource messageSource;


    /**
     * 创建优惠卷模版
     * @param requestParam
     * @return
     */
    @DuplicateSubmission
    @Operation(summary = "商家创建优惠卷模版")
    @PostMapping("/api/merchant-admin/coupon-template/create")
    public Result<Void> createCouponTemplate(@RequestBody CouponTemplateSaveReqDTO requestParam) {
        couponTemplateService.createCouponTemplate(requestParam);
        return Results.success();
    }


    /**
     * 分页查询
     * @param requestParam
     * @return
     */
    @GetMapping("/api/merchant-admin/coupon-template/page")
    @Operation(summary = "分页查询优惠卷模版")
    public Result<IPage<CouponTemplatePageRespDTO>> pageQueryCouponTemplate(CouponTemplatePageReqDTO requestParam) {
        IPage<CouponTemplatePageRespDTO> couponTemplatePageRespDTOIPage = couponTemplateService.pageQueryCouponTemplate(requestParam);
        return Results.success(couponTemplatePageRespDTOIPage);
    }

    /**
     * 增加优惠卷发行数
     * @param requestParam
     * @return
     */
    @PostMapping("/api/merchant-admin/coupon-template/increaseNumber")
    @Operation(summary = "增加发行数量")
    @DuplicateSubmission(message = "请勿重复增加发行数量")
    public Result<Void> increaseNumberCouponTemplate(CouponTemplateNumberReqDTO requestParam) {
        couponTemplateService.increaseNumberCouponTemplate(requestParam);
        return Results.success();
    }

    /**
     * 获取优惠卷详细
     * @return
     */
    @Operation(summary = "获取优惠卷详细")
    @GetMapping("/api/merchant-admin/coupon-template/find")
    public Result<CouponTemplateQueryRespDTO> findCouponTemplate(String couponTemplateId) {
        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplateById(couponTemplateId);
        return Results.success(couponTemplate);
    }


    /**
     * 结束优惠卷模版
     * @param couponTemplateId
     * @return
     */
    @Operation(summary = "结束优惠券模板")
    @PostMapping("/api/merchant-admin/coupon-template/terminate")
    public Result<Void> terminateCouponTemplate(String couponTemplateId) {
        couponTemplateService.terminateCouponTemplate(couponTemplateId);
        return Results.success();
    }

//    public Result<CouponTemplateQueryRespDTO> findCouponTemplate(CouponTemplateQueryReqDTO couponTemplateQueryReqDTO) {
//        CouponTemplateQueryRespDTO couponTemplate = couponTemplateService.findCouponTemplate(couponTemplateQueryReqDTO);
//        return
//    }
}

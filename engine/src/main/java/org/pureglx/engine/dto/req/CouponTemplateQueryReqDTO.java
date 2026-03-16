package org.pureglx.engine.dto.req;


import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponTemplateQueryReqDTO {


    /**
     * 店铺编号
     */
    @Schema(description = "店铺编号", example = "1810714735922956666", required = true)
    private String shopNumber;

    /**
     * 优惠券模板id
     */
    @Schema(description = "优惠券模板id", example = "1810966706881941507", required = true)
    private String couponTemplateId;
}

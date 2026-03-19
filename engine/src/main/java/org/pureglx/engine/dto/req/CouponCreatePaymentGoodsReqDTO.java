package org.pureglx.engine.dto.req;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CouponCreatePaymentGoodsReqDTO {

    /**
     * 商品编号
     */
    @Schema(description = "商品编号")
    private String goodsNumber;

    /**
     * 商品价格
     */
    @Schema(description = "商品价格")
    private BigDecimal goodsAmount;

    /**
     * 商品折扣后金额
     */
    @Schema(description = "商品折扣后金额")
    private BigDecimal goodsPayableAmount;
}
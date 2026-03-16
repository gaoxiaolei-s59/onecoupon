package org.puregxl.merchant.admin.dto.req;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponTemplateQueryReqDTO {


    /**
     * ID
     */
    private Long id;


    /**
     * 店铺编号
     */
    private Long shopNumber;


    /**
     * 优惠券状态 0：生效中 1：已结束
     */
    private Integer status;
}

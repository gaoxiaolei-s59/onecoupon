package org.pureglx.engine.dto.req;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponProcessRefundReqDTO {

    /**
     * 用户优惠券ID
     */
    private final Long couponId;
}

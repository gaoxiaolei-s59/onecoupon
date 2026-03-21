package org.pureglx.engine.dto.req;


import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CouponCreateProcessPaymentDTO {
    /**
     * 优惠卷id
     */
    private Long couponId;
}

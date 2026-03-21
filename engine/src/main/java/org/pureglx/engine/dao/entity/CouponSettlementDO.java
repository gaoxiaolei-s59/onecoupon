package org.pureglx.engine.dao.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@Builder
@TableName("t_coupon_settlement")
public class CouponSettlementDO {
    /**
     * id
     */
    private Long id;

    /**
     * '订单ID'
     */
    private Long orderId;

    /**
     * '用户ID'
     */
    private Long userId;

    /**
     * 优惠券id
     */
    private Long couponId;

    /**
     * 状态 0：锁定 1：已取消 2：已支付 3：已退款'
     */
    private int status;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;

    /**
     * 修改时间
     */
    @TableField(fill = FieldFill.UPDATE)
    private Date updateTime;
}

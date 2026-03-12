package org.puregxl.merchant.admin.dao.entity;


import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.*;

import java.util.Date;

@TableName("t_coupon_template_log")
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CouponTemplateLogDO {
    /**
     * id
     */
    private Long id;

    /**
     * 店铺编号
     */
    private Long shopNumber;

    /**
     * 优惠券模板ID
     */
    private String couponTemplateId;

    /**
     * 操作人
     */
    private String operatorId;

    /**
     * 操作日志
     */
    private String operationLog;

    /**
     * 原始数据
     */
    private String originalData;

    /**
     * 修改后数据
     */
    private String modifiedData;

    /**
     * 创建时间
     */
    @TableField(fill = FieldFill.INSERT)
    private Date createTime;
}

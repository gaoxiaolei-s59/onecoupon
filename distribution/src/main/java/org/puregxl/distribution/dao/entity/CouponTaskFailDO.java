package org.puregxl.distribution.dao.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@TableName("t_coupon_task_fail")
public class CouponTaskFailDO {

    /**
     * id
     */
    private Long id;

    /**
     * 批量id
     */
    private Long batchId;

    /**
     * JSON字符串，存储失败原因，Excel 行数等信息
     */
    @TableField(value = "`json_object`")
    private String jsonObject;
}

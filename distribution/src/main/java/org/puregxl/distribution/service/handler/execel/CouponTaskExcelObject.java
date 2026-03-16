package org.puregxl.distribution.service.handler.execel;

import com.alibaba.excel.annotation.ExcelProperty;
import lombok.Data;

@Data
public class CouponTaskExcelObject {

    @ExcelProperty("用户ID")
    private String userId;

    @ExcelProperty("手机号")
    private String phone;

    @ExcelProperty("邮箱")
    private String mail;
}


package org.puregxl.merchant.admin.common;

import cn.hutool.core.util.StrUtil;
import com.mzt.logapi.service.IParseFunction;
import org.puregxl.merchant.admin.common.enums.DiscountTargetEnum;
import org.puregxl.merchant.admin.common.enums.DiscountTypeEnum;
import org.springframework.stereotype.Component;

import java.util.List;


@Component
public class CommonEnumParseFunction implements IParseFunction {

    private static final String DIS_COUNT_TARGET_ENUM_SIM_NAME = DiscountTargetEnum.class.getSimpleName();

    private static final String DIS_COUNT_TYPE_ENUM_SIM_NAME = DiscountTypeEnum.class.getSimpleName();

    @Override
    public String functionName() {
        return "COMMON_ENUM_FUNCTION";
    }

    @Override
    public String apply(Object value) {
        try {
            List<String> parts = StrUtil.split(value.toString(), "_");

            if (parts.size() !=  2) {
                throw new IllegalArgumentException("\"格式错误，需要 '枚举类_具体值' 的形式。\"");
            }

            String enumName = parts.get(0);
            int enumValue =  Integer.parseInt(parts.get(1));

            String enumValueByType = findEnumValueByType(enumName, enumValue);

            return enumValueByType;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("第二个下划线的值需要为整数" + e);
        }
    }

    /**
     * 基于类名和变量寻找枚举值
     * @param name
     * @param type
     * @return
     */
    private String findEnumValueByType(String name, int type) {
        if (DIS_COUNT_TARGET_ENUM_SIM_NAME.equals(name)) {
            return DiscountTargetEnum.findValueByType(type);
        } else if (DIS_COUNT_TYPE_ENUM_SIM_NAME.equals(name)) {
            return DiscountTypeEnum.findValueByType(type);
        } else {
            throw new IllegalArgumentException("错误的枚举类名:" + name);
        }
    }
}

package org.puregxl.merchant.admin;


import cn.hutool.core.lang.Assert;
import org.junit.jupiter.api.Test;
import org.puregxl.merchant.admin.common.context.UserContext;
import org.puregxl.merchant.admin.common.context.UserInfoDTO;
import org.puregxl.merchant.admin.common.enums.DiscountTargetEnum;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

@SpringBootTest
public class SpelTest {

    @Test
    void test() {
        String userid = "1810518709471555585";
        UserContext.setUserContext(new UserInfoDTO(userid, "pdd45305558318", 1810714735922956666L));
        //org/puregxl/merchant/admin/common/context/UserContext.java
        String spel = "T(org.puregxl.merchant.admin.common.context.UserContext).getUserId()";
        ExpressionParser parser = new SpelExpressionParser();

        Expression expression = parser.parseExpression(spel);

        System.out.println(expression.getValue());
        try {
            Assert.equals(expression.getValue(), userid);
        } finally {
            UserContext.removeUserContext();
        }

    }

    @Test
    void testName () {
        String simpleName = DiscountTargetEnum.class.getSimpleName();
        System.out.println(simpleName);

    }
}

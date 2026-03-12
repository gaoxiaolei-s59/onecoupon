package org.puregxl.merchant.admin.common.context;

import java.util.Optional;

public class UserContext {

    private static final ThreadLocal<UserInfoDTO> userContext = new ThreadLocal<>();

    /**
     * 设置用户上下文
     * @param userInfoDto
     */
    public static void setUserContext(UserInfoDTO userInfoDto) {
        userContext.set(userInfoDto);
    }

    /**
     * 删除用户上下文
     */
    public static void removeUserContext() {
        userContext.remove();
    }

    /**
     * 获取用户名
     * @return
     */
    public static String getUserName() {
        UserInfoDTO userInfoDto = userContext.get();
        return Optional.ofNullable(userInfoDto).map(UserInfoDTO::getUserName).orElse(null);
    }


    /**
     * 获取用户id
     * @return
     */
    public static String getUserId() {
        UserInfoDTO userInfoDto = userContext.get();
        return Optional.ofNullable(userInfoDto).map(UserInfoDTO::getUserID).orElse(null);
    }

    /**
     * 获取商品序列号
     * @return
     */
    public static Long getShopNumber() {
        UserInfoDTO userInfoDto = userContext.get();
        return Optional.ofNullable(userInfoDto).map(UserInfoDTO::getShopNumber).orElse(null);
    }

}

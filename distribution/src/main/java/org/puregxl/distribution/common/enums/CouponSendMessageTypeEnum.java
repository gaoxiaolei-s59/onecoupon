package org.puregxl.distribution.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
@RequiredArgsConstructor
public enum CouponSendMessageTypeEnum {
    /**
     * 普通消息
     */
    DELAY_MESSAGE(0),

    /**
     * delay
     */
    DELIVER_MESSAGE(1),

    /**
     * delever
     */
    COMMON_MESSAGE(2);

    @Getter
    private final int type;
}

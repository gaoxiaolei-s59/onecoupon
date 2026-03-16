package org.puregxl.distribution.common.enums;


import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * 状态 0：未使用 1：锁定 2：已使用 3：已过期 4：已撤回
 */
@RequiredArgsConstructor
public enum CouponStatusEnum {

    UNUSED(0, "未使用"),
    LOCKED(1, "锁定"),
    USED(2, "已使用"),
    EXPIRED(3, "已过期"),
    REVOKED(4, "已撤回");

    @Getter
    private final Integer code;
    @Getter
    private final String desc;

    public static CouponStatusEnum of(Integer code) {
        if (code == null) {
            return null;
        }
        for (CouponStatusEnum e : values()) {
            if (e.code.equals(code)) {
                return e;
            }
        }
        return null;
    }


}

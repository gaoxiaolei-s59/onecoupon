package org.pureglx.engine.common.context;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserInfoDTO {
    /**
     * 用户id
     */
    private String userID;

    /**
     * 用户邮箱
     */
    private String userName;

    /**
     * 店铺编号
     */
    private Long shopNumber;

}

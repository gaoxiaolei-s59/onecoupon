package org.pureglx.engine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.pureglx.engine.dao.entity.UserCouponDO;
import org.pureglx.engine.dto.req.CouponCreatePaymentReqDTO;
import org.pureglx.engine.dto.req.CouponTemplateRedeemReqDTO;

public interface UserCouponService extends IService<UserCouponDO> {
    void redeemUserCoupon(CouponTemplateRedeemReqDTO requestParam);

    void redeemUserCouponByMQ(CouponTemplateRedeemReqDTO requestParam);

    void createPaymentRecord(CouponCreatePaymentReqDTO requestParam);
}

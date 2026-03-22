package org.puregxl.settlement.service;


import org.puregxl.settlement.dto.req.QueryCouponsReqDTO;
import org.puregxl.settlement.dto.resp.QueryCouponsRespDTO;

public interface CouponQueryService {
    QueryCouponsRespDTO listQueryCoupons(QueryCouponsReqDTO requestParam);

    QueryCouponsRespDTO listQueryCouponsBySync(QueryCouponsReqDTO requestParam);
}

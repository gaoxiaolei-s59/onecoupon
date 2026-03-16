package org.pureglx.engine.service;

import com.baomidou.mybatisplus.extension.service.IService;
import org.pureglx.engine.dao.entity.CouponTemplateDO;
import org.pureglx.engine.dto.req.CouponTemplateQueryReqDTO;
import org.pureglx.engine.dto.resp.CouponTemplateQueryRespDTO;

public interface CouponTemplateService extends IService<CouponTemplateDO> {

    CouponTemplateQueryRespDTO findCouponTemplate(CouponTemplateQueryReqDTO requestParam);
}

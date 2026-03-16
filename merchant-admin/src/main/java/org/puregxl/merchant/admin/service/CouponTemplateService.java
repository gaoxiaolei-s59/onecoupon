package org.puregxl.merchant.admin.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.service.IService;
import org.puregxl.merchant.admin.dao.entity.CouponTemplateDO;
import org.puregxl.merchant.admin.dto.req.CouponTemplateNumberReqDTO;
import org.puregxl.merchant.admin.dto.req.CouponTemplatePageReqDTO;
import org.puregxl.merchant.admin.dto.req.CouponTemplateSaveReqDTO;
import org.puregxl.merchant.admin.dto.resp.CouponTemplatePageRespDTO;
import org.puregxl.merchant.admin.dto.resp.CouponTemplateQueryRespDTO;


public interface CouponTemplateService extends IService<CouponTemplateDO> {
    void createCouponTemplate(CouponTemplateSaveReqDTO requestParam);

    IPage<CouponTemplatePageRespDTO> pageQueryCouponTemplate(CouponTemplatePageReqDTO requestParam);

    void increaseNumberCouponTemplate(CouponTemplateNumberReqDTO requestParam);

    CouponTemplateQueryRespDTO findCouponTemplateById(String couponTemplateId);

    void terminateCouponTemplate(String couponTemplateId);


}

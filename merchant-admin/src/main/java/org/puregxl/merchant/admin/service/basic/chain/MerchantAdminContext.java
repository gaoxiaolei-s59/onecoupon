package org.puregxl.merchant.admin.service.basic.chain;

import org.springframework.beans.BeansException;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.*;

@Component
public class MerchantAdminContext<T> implements ApplicationContextAware, CommandLineRunner {

    private ApplicationContext applicationContext;

    private final Map<String, List<MerchantAdminAbstractChainHandler>> abstractChainHandlerContainer = new HashMap<>();

    @Override
    public void run(String... args) throws Exception {
        Map<String, MerchantAdminAbstractChainHandler> chainMap = applicationContext.getBeansOfType(MerchantAdminAbstractChainHandler.class);
        chainMap.forEach((beanName, bean) -> {
            //判断当前的责任链的mark在不在map集合中
            String mark = bean.mark();
            if (!abstractChainHandlerContainer.containsKey(mark)) {
                List<MerchantAdminAbstractChainHandler> merchantAdminAbstractChainHandlerList = new ArrayList<>();
                merchantAdminAbstractChainHandlerList.add(bean);
                abstractChainHandlerContainer.put(mark, merchantAdminAbstractChainHandlerList);
            } else {
                abstractChainHandlerContainer.get(mark).add(bean);
            }
        });

        //执行排序逻辑
        abstractChainHandlerContainer.forEach((mark, merchantAdminList) -> {
            merchantAdminList.sort(Comparator.comparing(Ordered::getOrder));
        });

    }

    /**
     * 执行责任链的逻辑
     */
    public void execute(String mark, T requestParam) {
        List<MerchantAdminAbstractChainHandler> abstractChainHandlers = abstractChainHandlerContainer.get(mark);
        if (CollectionUtils.isEmpty(abstractChainHandlers)) {
            throw new RuntimeException("责任链解析出现异常");
        }
        abstractChainHandlers.forEach(each -> each.handler(requestParam));
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.applicationContext = applicationContext;
    }
}

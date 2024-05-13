package cn.zhangmenglong.platform.init;


import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.rabbitmq.RabbitMQ;
import cn.zhangmenglong.platform.utils.DnsDomainNameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
public class Init {

    @Autowired
    private DnsDomainNameUtils dnsDomainNameUtils;

    @Autowired
    private DnsDomainNameMapper dnsDomainNameMapper;

    @PostConstruct
    public void init() {

        DnsDomainName dnsDomainName = new DnsDomainName();
        dnsDomainName.setDomainNameStatus("0");

        List<DnsDomainName> dnsDomainNameList = dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);

        dnsDomainNameList.forEach(dnsDomainNameTemp -> {
            try {
                dnsDomainNameUtils.transformZone(dnsDomainNameTemp);
            } catch (Exception ignored) {}
        });
    }

}

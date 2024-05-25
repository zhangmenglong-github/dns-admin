package cn.zhangmenglong.platform.task;

import cn.zhangmenglong.common.core.domain.entity.SysDictData;
import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.DateUtils;
import cn.zhangmenglong.common.utils.DictUtils;
import cn.zhangmenglong.platform.constant.PlatformDomainNameConstants;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.tdengine.TdengineDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.SQLException;
import java.util.*;

@Component
public class DnsDomainNameTask {

    @Autowired
    private DnsDomainNameMapper dnsDomainNameMapper;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private MongoTemplate mongoTemplate;

    @Scheduled(cron = "0 1 0 * * ?")
    public void collectionResolutionGeoLog() {
        DnsDomainName dnsDomainName = new DnsDomainName();
        dnsDomainName.setDomainNameStatus("0");
        List<DnsDomainName> dnsDomainNameList = dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);
        List<SysDictData> countryNameCodeDict = DictUtils.getDictCache("country_name_code");
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -1);
        String yesterday = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, calendar.getTime());
        dnsDomainNameList.forEach(dnsDomainNameTemp -> {
            Map<String, Object> logMap = new HashMap<>();
            logMap.put("queryDomain", dnsDomainNameTemp.getDomainName());
            logMap.put("queryTime", yesterday);
            countryNameCodeDict.forEach(countryNameCode -> {
                Object geoCount = redisCache.getCacheObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + yesterday + ":" + dnsDomainNameTemp.getDomainName() + ":" + countryNameCode.getDictValue());
                if (geoCount != null) {
                    logMap.put(countryNameCode.getDictValue(), String.valueOf(geoCount));
                }
            });
            Object nullGeoCount = redisCache.getCacheObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + yesterday + ":" + dnsDomainNameTemp.getDomainName() + ":null");
            if (nullGeoCount != null) {
                logMap.put("null", String.valueOf(nullGeoCount));
            }
            mongoTemplate.insert(logMap, "dns_geo_log");
            countryNameCodeDict.forEach(countryNameCode -> {
                redisCache.deleteObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + yesterday + ":" + dnsDomainNameTemp.getDomainName() + ":" + countryNameCode.getDictValue());
            });
        });
    }

}

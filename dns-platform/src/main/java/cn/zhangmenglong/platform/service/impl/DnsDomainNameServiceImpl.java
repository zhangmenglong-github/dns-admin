package cn.zhangmenglong.platform.service.impl;

import java.net.IDN;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.concurrent.TimeUnit;

import cn.zhangmenglong.common.core.domain.entity.SysDictData;
import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.DateUtils;
import cn.zhangmenglong.common.utils.DictUtils;
import cn.zhangmenglong.common.utils.SecurityUtils;
import cn.zhangmenglong.common.utils.StringUtils;
import cn.zhangmenglong.common.utils.uuid.IdUtils;
import cn.zhangmenglong.platform.constant.PlatformDomainNameConstants;
import cn.zhangmenglong.platform.domain.dto.DnsDomainNameStatistics;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineQueryNameStatistics;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineQueryNameTypeStatistics;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineQueryTypeStatistics;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineStatistcs;
import cn.zhangmenglong.platform.mapper.DnsDomainNameRecordMapper;
import cn.zhangmenglong.platform.tdengine.TdengineDataSource;
import cn.zhangmenglong.platform.utils.DnsDomainNameUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.service.IDnsDomainNameService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;
import org.xbill.DNS.utils.base16;

/**
 * 域名Service业务层处理
 * 
 * @author dns
 * @date 2024-04-13
 */
@Service
public class DnsDomainNameServiceImpl implements IDnsDomainNameService 
{
    @Autowired
    private DnsDomainNameMapper dnsDomainNameMapper;

    @Autowired
    private DnsDomainNameRecordMapper dnsDomainNameRecordMapper;

    @Autowired
    private TdengineDataSource tdengineDataSource;

    @Autowired
    private DnsDomainNameUtils dnsDomainNameUtils;

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private MongoTemplate mongoTemplate;


    @Override
    public Map<String, Integer> selectDnsDomainNameStatisticsCountByUserId(Long userId) {
        return dnsDomainNameMapper.selectDnsDomainNameStatisticsCountByUserId(userId);
    }

    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatistics(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        String intervalType = (String) dnsDomainName.getParams().get("intervalType");
        Long timeStamp = Long.valueOf(String.valueOf(dnsDomainName.getParams().get("date")));
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());

        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue()) && StringUtils.isNotEmpty(intervalType)) {

            long start;
            long end;
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(timeStamp));
            if (intervalType.contentEquals("DAY")) {
                calendar.set(Calendar.HOUR_OF_DAY, 0);
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                start = calendar.getTimeInMillis();
                calendar.set(Calendar.HOUR_OF_DAY, 23);
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                end = calendar.getTimeInMillis();
            } else if (intervalType.contentEquals("HOUR")) {
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                start = calendar.getTimeInMillis();
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                end = calendar.getTimeInMillis();
            } else if (intervalType.contentEquals("MINUTE")) {
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                start = calendar.getTimeInMillis();
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                end = calendar.getTimeInMillis();
            } else {
                result.put("code", -1);
                result.put("message", "操作失败");
                return result;
            }
            List<DnsDomainNameTdengineStatistcs> dnsDomainNameTdengineStatistcsList = tdengineDataSource.queryStatistics(dnsDomainName.getDomainName(), start, end, intervalType);
            DnsDomainNameStatistics dnsDomainNameStatistics = new DnsDomainNameStatistics();
            dnsDomainNameStatistics.setTimeLine(new ArrayList<>());
            dnsDomainNameStatistics.setTimeData(new HashMap<>());
            Calendar nowCalendar = Calendar.getInstance();
            boolean queryDateIsToday = (nowCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) && (nowCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) && (nowCalendar.get(Calendar.DATE) == calendar.get(Calendar.DATE));
            if (intervalType.contentEquals("DAY")) {
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                int hourRange = queryDateIsToday ? nowCalendar.get(Calendar.HOUR_OF_DAY) : 23;
                for (int index = 0; index <= hourRange; index++) {
                    calendar.set(Calendar.HOUR_OF_DAY, index);
                    dnsDomainNameStatistics.getTimeLine().add(DateUtils.parseDateToStr(DateUtils.HH_MM_SS, calendar.getTime()));
                }
                dnsDomainNameStatistics.getTimeLine().forEach(timeLine -> {
                    boolean timeLineHasData = false;
                    for (DnsDomainNameTdengineStatistcs dnsDomainNameTdengineStatistcs : dnsDomainNameTdengineStatistcsList) {
                        if (DateUtils.parseDateToStr(DateUtils.HH, dnsDomainNameTdengineStatistcs.getQueryTime()).contentEquals(timeLine.split(":")[0])) {
                            List<Long> queryCount = dnsDomainNameStatistics.getTimeData().get("queryCount");
                            queryCount = (queryCount == null) ? new ArrayList<>() : queryCount;
                            queryCount.add(dnsDomainNameTdengineStatistcs.getQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("queryCount", queryCount);
                            List<Long> udpQueryCount = dnsDomainNameStatistics.getTimeData().get("udpQueryCount");
                            udpQueryCount = (udpQueryCount == null) ? new ArrayList<>() : udpQueryCount;
                            udpQueryCount.add(dnsDomainNameTdengineStatistcs.getUdpQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("udpQueryCount", udpQueryCount);
                            List<Long> tcpQueryCount = dnsDomainNameStatistics.getTimeData().get("tcpQueryCount");
                            tcpQueryCount = (tcpQueryCount == null) ? new ArrayList<>() : tcpQueryCount;
                            tcpQueryCount.add(dnsDomainNameTdengineStatistcs.getTcpQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("tcpQueryCount", tcpQueryCount);
                            List<Long> dnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("dnssecRequestCount");
                            dnssecRequestCount = (dnssecRequestCount == null) ? new ArrayList<>() : dnssecRequestCount;
                            dnssecRequestCount.add(dnsDomainNameTdengineStatistcs.getDnssecRequestCount());
                            dnsDomainNameStatistics.getTimeData().put("dnssecRequestCount", dnssecRequestCount);
                            List<Long> noDnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("noDnssecRequestCount");
                            noDnssecRequestCount = (noDnssecRequestCount == null) ? new ArrayList<>() : noDnssecRequestCount;
                            noDnssecRequestCount.add(dnsDomainNameTdengineStatistcs.getNoDnssecRequestCount());
                            dnsDomainNameStatistics.getTimeData().put("noDnssecRequestCount", noDnssecRequestCount);
                            List<Long> dnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("dnssecResponseCount");
                            dnssecResponseCount = (dnssecResponseCount == null) ? new ArrayList<>() : dnssecResponseCount;
                            dnssecResponseCount.add(dnsDomainNameTdengineStatistcs.getDnssecResponseCount());
                            dnsDomainNameStatistics.getTimeData().put("dnssecResponseCount", dnssecResponseCount);
                            List<Long> noDnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("noDnssecResponseCount");
                            noDnssecResponseCount = (noDnssecResponseCount == null) ? new ArrayList<>() : noDnssecResponseCount;
                            noDnssecResponseCount.add(dnsDomainNameTdengineStatistcs.getNoDnssecResponseCount());
                            dnsDomainNameStatistics.getTimeData().put("noDnssecResponseCount", noDnssecResponseCount);
                            timeLineHasData = true;
                        }
                    }
                    if (!timeLineHasData) {
                        List<Long> queryCount = dnsDomainNameStatistics.getTimeData().get("queryCount");
                        queryCount = (queryCount == null) ? new ArrayList<>() : queryCount;
                        queryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("queryCount", queryCount);
                        List<Long> udpQueryCount = dnsDomainNameStatistics.getTimeData().get("udpQueryCount");
                        udpQueryCount = (udpQueryCount == null) ? new ArrayList<>() : udpQueryCount;
                        udpQueryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("udpQueryCount", udpQueryCount);
                        List<Long> tcpQueryCount = dnsDomainNameStatistics.getTimeData().get("tcpQueryCount");
                        tcpQueryCount = (tcpQueryCount == null) ? new ArrayList<>() : tcpQueryCount;
                        tcpQueryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("tcpQueryCount", tcpQueryCount);
                        List<Long> dnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("dnssecRequestCount");
                        dnssecRequestCount = (dnssecRequestCount == null) ? new ArrayList<>() : dnssecRequestCount;
                        dnssecRequestCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("dnssecRequestCount", dnssecRequestCount);
                        List<Long> noDnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("noDnssecRequestCount");
                        noDnssecRequestCount = (noDnssecRequestCount == null) ? new ArrayList<>() : noDnssecRequestCount;
                        noDnssecRequestCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("noDnssecRequestCount", noDnssecRequestCount);
                        List<Long> dnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("dnssecResponseCount");
                        dnssecResponseCount = (dnssecResponseCount == null) ? new ArrayList<>() : dnssecResponseCount;
                        dnssecResponseCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("dnssecResponseCount", dnssecResponseCount);
                        List<Long> noDnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("noDnssecResponseCount");
                        noDnssecResponseCount = (noDnssecResponseCount == null) ? new ArrayList<>() : noDnssecResponseCount;
                        noDnssecResponseCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("noDnssecResponseCount", noDnssecResponseCount);
                    }
                });
                result.put("code", 0);
                result.put("data", dnsDomainNameStatistics);
                result.put("message", "操作成功");
                return result;
            } else if (intervalType.contentEquals("HOUR")) {
                calendar.set(Calendar.SECOND, 0);
                int minuteRange = queryDateIsToday ? (nowCalendar.get(Calendar.HOUR_OF_DAY) > calendar.get(Calendar.HOUR_OF_DAY) ? 59 : nowCalendar.get(Calendar.MINUTE)) : 59;
                for (int index = 0; index <= minuteRange; index++) {
                    calendar.set(Calendar.MINUTE, index);
                    dnsDomainNameStatistics.getTimeLine().add(DateUtils.parseDateToStr(DateUtils.HH_MM_SS, calendar.getTime()));
                }
                dnsDomainNameStatistics.getTimeLine().forEach(timeLine -> {
                    boolean timeLineHasData = false;
                    for (DnsDomainNameTdengineStatistcs dnsDomainNameTdengineStatistcs : dnsDomainNameTdengineStatistcsList) {
                        if (DateUtils.parseDateToStr(DateUtils.MM, dnsDomainNameTdengineStatistcs.getQueryTime()).contentEquals(timeLine.split(":")[1])) {
                            List<Long> queryCount = dnsDomainNameStatistics.getTimeData().get("queryCount");
                            queryCount = (queryCount == null) ? new ArrayList<>() : queryCount;
                            queryCount.add(dnsDomainNameTdengineStatistcs.getQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("queryCount", queryCount);
                            List<Long> udpQueryCount = dnsDomainNameStatistics.getTimeData().get("udpQueryCount");
                            udpQueryCount = (udpQueryCount == null) ? new ArrayList<>() : udpQueryCount;
                            udpQueryCount.add(dnsDomainNameTdengineStatistcs.getUdpQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("udpQueryCount", udpQueryCount);
                            List<Long> tcpQueryCount = dnsDomainNameStatistics.getTimeData().get("tcpQueryCount");
                            tcpQueryCount = (tcpQueryCount == null) ? new ArrayList<>() : tcpQueryCount;
                            tcpQueryCount.add(dnsDomainNameTdengineStatistcs.getTcpQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("tcpQueryCount", tcpQueryCount);
                            List<Long> dnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("dnssecRequestCount");
                            dnssecRequestCount = (dnssecRequestCount == null) ? new ArrayList<>() : dnssecRequestCount;
                            dnssecRequestCount.add(dnsDomainNameTdengineStatistcs.getDnssecRequestCount());
                            dnsDomainNameStatistics.getTimeData().put("dnssecRequestCount", dnssecRequestCount);
                            List<Long> noDnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("noDnssecRequestCount");
                            noDnssecRequestCount = (noDnssecRequestCount == null) ? new ArrayList<>() : noDnssecRequestCount;
                            noDnssecRequestCount.add(dnsDomainNameTdengineStatistcs.getNoDnssecRequestCount());
                            dnsDomainNameStatistics.getTimeData().put("noDnssecRequestCount", noDnssecRequestCount);
                            List<Long> dnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("dnssecResponseCount");
                            dnssecResponseCount = (dnssecResponseCount == null) ? new ArrayList<>() : dnssecResponseCount;
                            dnssecResponseCount.add(dnsDomainNameTdengineStatistcs.getDnssecResponseCount());
                            dnsDomainNameStatistics.getTimeData().put("dnssecResponseCount", dnssecResponseCount);
                            List<Long> noDnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("noDnssecResponseCount");
                            noDnssecResponseCount = (noDnssecResponseCount == null) ? new ArrayList<>() : noDnssecResponseCount;
                            noDnssecResponseCount.add(dnsDomainNameTdengineStatistcs.getNoDnssecResponseCount());
                            dnsDomainNameStatistics.getTimeData().put("noDnssecResponseCount", noDnssecResponseCount);
                            timeLineHasData = true;
                        }
                    }
                    if (!timeLineHasData) {
                        List<Long> queryCount = dnsDomainNameStatistics.getTimeData().get("queryCount");
                        queryCount = (queryCount == null) ? new ArrayList<>() : queryCount;
                        queryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("queryCount", queryCount);
                        List<Long> udpQueryCount = dnsDomainNameStatistics.getTimeData().get("udpQueryCount");
                        udpQueryCount = (udpQueryCount == null) ? new ArrayList<>() : udpQueryCount;
                        udpQueryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("udpQueryCount", udpQueryCount);
                        List<Long> tcpQueryCount = dnsDomainNameStatistics.getTimeData().get("tcpQueryCount");
                        tcpQueryCount = (tcpQueryCount == null) ? new ArrayList<>() : tcpQueryCount;
                        tcpQueryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("tcpQueryCount", tcpQueryCount);
                        List<Long> dnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("dnssecRequestCount");
                        dnssecRequestCount = (dnssecRequestCount == null) ? new ArrayList<>() : dnssecRequestCount;
                        dnssecRequestCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("dnssecRequestCount", dnssecRequestCount);
                        List<Long> noDnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("noDnssecRequestCount");
                        noDnssecRequestCount = (noDnssecRequestCount == null) ? new ArrayList<>() : noDnssecRequestCount;
                        noDnssecRequestCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("noDnssecRequestCount", noDnssecRequestCount);
                        List<Long> dnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("dnssecResponseCount");
                        dnssecResponseCount = (dnssecResponseCount == null) ? new ArrayList<>() : dnssecResponseCount;
                        dnssecResponseCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("dnssecResponseCount", dnssecResponseCount);
                        List<Long> noDnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("noDnssecResponseCount");
                        noDnssecResponseCount = (noDnssecResponseCount == null) ? new ArrayList<>() : noDnssecResponseCount;
                        noDnssecResponseCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("noDnssecResponseCount", noDnssecResponseCount);
                    }
                });
                result.put("code", 0);
                result.put("data", dnsDomainNameStatistics);
                result.put("message", "操作成功");
                return result;
            } else if (intervalType.contentEquals("MINUTE")) {
                int secondRange = queryDateIsToday ? (nowCalendar.get(Calendar.HOUR_OF_DAY) > calendar.get(Calendar.HOUR_OF_DAY) ? 59 : (nowCalendar.get(Calendar.MINUTE) > calendar.get(Calendar.MINUTE) ? 59 : nowCalendar.get(Calendar.SECOND))) : 59;
                for (int index = 0; index <= secondRange; index++) {
                    calendar.set(Calendar.SECOND, index);
                    dnsDomainNameStatistics.getTimeLine().add(DateUtils.parseDateToStr(DateUtils.HH_MM_SS, calendar.getTime()));
                }
                dnsDomainNameStatistics.getTimeLine().forEach(timeLine -> {
                    boolean timeLineHasData = false;
                    for (DnsDomainNameTdengineStatistcs dnsDomainNameTdengineStatistcs : dnsDomainNameTdengineStatistcsList) {
                        if (DateUtils.parseDateToStr(DateUtils.SS, dnsDomainNameTdengineStatistcs.getQueryTime()).contentEquals(timeLine.split(":")[2])) {
                            List<Long> queryCount = dnsDomainNameStatistics.getTimeData().get("queryCount");
                            queryCount = (queryCount == null) ? new ArrayList<>() : queryCount;
                            queryCount.add(dnsDomainNameTdengineStatistcs.getQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("queryCount", queryCount);
                            List<Long> udpQueryCount = dnsDomainNameStatistics.getTimeData().get("udpQueryCount");
                            udpQueryCount = (udpQueryCount == null) ? new ArrayList<>() : udpQueryCount;
                            udpQueryCount.add(dnsDomainNameTdengineStatistcs.getUdpQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("udpQueryCount", udpQueryCount);
                            List<Long> tcpQueryCount = dnsDomainNameStatistics.getTimeData().get("tcpQueryCount");
                            tcpQueryCount = (tcpQueryCount == null) ? new ArrayList<>() : tcpQueryCount;
                            tcpQueryCount.add(dnsDomainNameTdengineStatistcs.getTcpQueryCount());
                            dnsDomainNameStatistics.getTimeData().put("tcpQueryCount", tcpQueryCount);
                            List<Long> dnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("dnssecRequestCount");
                            dnssecRequestCount = (dnssecRequestCount == null) ? new ArrayList<>() : dnssecRequestCount;
                            dnssecRequestCount.add(dnsDomainNameTdengineStatistcs.getDnssecRequestCount());
                            dnsDomainNameStatistics.getTimeData().put("dnssecRequestCount", dnssecRequestCount);
                            List<Long> noDnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("noDnssecRequestCount");
                            noDnssecRequestCount = (noDnssecRequestCount == null) ? new ArrayList<>() : noDnssecRequestCount;
                            noDnssecRequestCount.add(dnsDomainNameTdengineStatistcs.getNoDnssecRequestCount());
                            dnsDomainNameStatistics.getTimeData().put("noDnssecRequestCount", noDnssecRequestCount);
                            List<Long> dnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("dnssecResponseCount");
                            dnssecResponseCount = (dnssecResponseCount == null) ? new ArrayList<>() : dnssecResponseCount;
                            dnssecResponseCount.add(dnsDomainNameTdengineStatistcs.getDnssecResponseCount());
                            dnsDomainNameStatistics.getTimeData().put("dnssecResponseCount", dnssecResponseCount);
                            List<Long> noDnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("noDnssecResponseCount");
                            noDnssecResponseCount = (noDnssecResponseCount == null) ? new ArrayList<>() : noDnssecResponseCount;
                            noDnssecResponseCount.add(dnsDomainNameTdengineStatistcs.getNoDnssecResponseCount());
                            dnsDomainNameStatistics.getTimeData().put("noDnssecResponseCount", noDnssecResponseCount);
                            timeLineHasData = true;
                        }
                    }
                    if (!timeLineHasData) {
                        List<Long> queryCount = dnsDomainNameStatistics.getTimeData().get("queryCount");
                        queryCount = (queryCount == null) ? new ArrayList<>() : queryCount;
                        queryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("queryCount", queryCount);
                        List<Long> udpQueryCount = dnsDomainNameStatistics.getTimeData().get("udpQueryCount");
                        udpQueryCount = (udpQueryCount == null) ? new ArrayList<>() : udpQueryCount;
                        udpQueryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("udpQueryCount", udpQueryCount);
                        List<Long> tcpQueryCount = dnsDomainNameStatistics.getTimeData().get("tcpQueryCount");
                        tcpQueryCount = (tcpQueryCount == null) ? new ArrayList<>() : tcpQueryCount;
                        tcpQueryCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("tcpQueryCount", tcpQueryCount);
                        List<Long> dnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("dnssecRequestCount");
                        dnssecRequestCount = (dnssecRequestCount == null) ? new ArrayList<>() : dnssecRequestCount;
                        dnssecRequestCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("dnssecRequestCount", dnssecRequestCount);
                        List<Long> noDnssecRequestCount = dnsDomainNameStatistics.getTimeData().get("noDnssecRequestCount");
                        noDnssecRequestCount = (noDnssecRequestCount == null) ? new ArrayList<>() : noDnssecRequestCount;
                        noDnssecRequestCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("noDnssecRequestCount", noDnssecRequestCount);
                        List<Long> dnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("dnssecResponseCount");
                        dnssecResponseCount = (dnssecResponseCount == null) ? new ArrayList<>() : dnssecResponseCount;
                        dnssecResponseCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("dnssecResponseCount", dnssecResponseCount);
                        List<Long> noDnssecResponseCount = dnsDomainNameStatistics.getTimeData().get("noDnssecResponseCount");
                        noDnssecResponseCount = (noDnssecResponseCount == null) ? new ArrayList<>() : noDnssecResponseCount;
                        noDnssecResponseCount.add(0L);
                        dnsDomainNameStatistics.getTimeData().put("noDnssecResponseCount", noDnssecResponseCount);
                    }
                });
                result.put("code", 0);
                result.put("data", dnsDomainNameStatistics);
                result.put("message", "操作成功");
                return result;
            } else {
                result.put("code", -1);
                result.put("message", "操作失败");
                return result;
            }
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryGeo(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        Long timeStamp = Long.valueOf(String.valueOf(dnsDomainName.getParams().get("date")));
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());
        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {
            String queryTime = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, new Date(timeStamp));
            Query query = new Query();
            query.addCriteria(Criteria.where("queryTime").is(queryTime).and("queryDomain").is(dnsDomainName.getDomainName()));
            Map<String, Object> geoLogMap = mongoTemplate.findOne(query, Map.class, "dns_geo_log");
            if (geoLogMap == null) {
                geoLogMap = new HashMap<>();
                List<SysDictData> countryNameCodeDict = DictUtils.getDictCache("country_name_code");
                for (SysDictData countryNameCode : countryNameCodeDict) {
                    Object geoCount = redisCache.getCacheObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + queryTime + ":" + dnsDomainName.getDomainName() + ":" + countryNameCode.getDictValue());
                    if (geoCount != null) {
                        geoLogMap.put(countryNameCode.getDictValue(), String.valueOf(geoCount));
                    }
                }
                Object nullGeoCount = redisCache.getCacheObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + queryTime + ":" + dnsDomainName.getDomainName() + ":null");
                if (nullGeoCount != null) {
                    geoLogMap.put("null", String.valueOf(nullGeoCount));
                }
            }
            result.put("code", 0);
            result.put("message", "操作成功");
            result.put("data", geoLogMap);
            return result;
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryName(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        Long timeStamp = Long.valueOf(String.valueOf(dnsDomainName.getParams().get("date")));
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());

        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(timeStamp));
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long start = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            long end = calendar.getTimeInMillis();
            List<DnsDomainNameTdengineQueryNameStatistics> dnsDomainNameTdengineQueryNameStatisticsList = tdengineDataSource.queryNameStatistics(dnsDomainName.getDomainName(), start, end);
            result.put("code", 0);
            result.put("message", "操作成功");
            result.put("data", dnsDomainNameTdengineQueryNameStatisticsList);
            return result;
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryType(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        Long timeStamp = Long.valueOf(String.valueOf(dnsDomainName.getParams().get("date")));
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());

        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(timeStamp));
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long start = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            long end = calendar.getTimeInMillis();
            List<DnsDomainNameTdengineQueryTypeStatistics> dnsDomainNameTdengineQueryTypeStatisticsList = tdengineDataSource.queryTypeStatistics(dnsDomainName.getDomainName(), start, end);
            dnsDomainNameTdengineQueryTypeStatisticsList.forEach(dnsDomainNameTdengineQueryTypeStatistics -> {
                dnsDomainNameTdengineQueryTypeStatistics.setQueryType(Type.string(Integer.parseInt(dnsDomainNameTdengineQueryTypeStatistics.getQueryType())));
            });
            result.put("code", 0);
            result.put("message", "操作成功");
            result.put("data", dnsDomainNameTdengineQueryTypeStatisticsList);
            return result;
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryNameType(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        Long timeStamp = Long.valueOf(String.valueOf(dnsDomainName.getParams().get("date")));
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());

        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {

            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(timeStamp));
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long start = calendar.getTimeInMillis();
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            long end = calendar.getTimeInMillis();
            Map<String, DnsDomainNameTdengineQueryNameTypeStatistics> dnsDomainNameTdengineQueryNameTypeStatisticsMap = tdengineDataSource.queryNameTypeStatistics(dnsDomainName.getDomainName(), start, end);
            List<DnsDomainNameTdengineQueryNameTypeStatistics> dnsDomainNameTdengineQueryNameTypeStatisticsList = new ArrayList<>();
            dnsDomainNameTdengineQueryNameTypeStatisticsMap.keySet().forEach(queryName -> {
                DnsDomainNameTdengineQueryNameTypeStatistics dnsDomainNameTdengineQueryNameTypeStatistics = dnsDomainNameTdengineQueryNameTypeStatisticsMap.get(queryName);
                DnsDomainNameTdengineQueryNameTypeStatistics dnsDomainNameTdengineQueryNameTypeStatisticsItem = new DnsDomainNameTdengineQueryNameTypeStatistics();
                dnsDomainNameTdengineQueryNameTypeStatisticsItem.setQueryName(dnsDomainNameTdengineQueryNameTypeStatistics.getQueryName());
                dnsDomainNameTdengineQueryNameTypeStatistics.getQueryTypeCount().keySet().forEach(queryType -> {
                    dnsDomainNameTdengineQueryNameTypeStatisticsItem.getQueryTypeCount().put(Type.string(Integer.parseInt(queryType)), dnsDomainNameTdengineQueryNameTypeStatistics.getQueryTypeCount().get(queryType));
                });
                dnsDomainNameTdengineQueryNameTypeStatisticsList.add(dnsDomainNameTdengineQueryNameTypeStatisticsItem);
            });
            result.put("code", 0);
            result.put("message", "操作成功");
            result.put("data", dnsDomainNameTdengineQueryNameTypeStatisticsList);
            return result;
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    @Override
    public Map<String, Object> validateRefreshDnsDomainName(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        //添加的域名名称不为空
        if (StringUtils.isNotEmpty(dnsDomainName.getDomainName())) {
            //将域名中的中文。替换为.
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().replaceAll("。", "."));
            //将域名结尾替换为.
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().endsWith(".") ? dnsDomainName.getDomainName() : dnsDomainName.getDomainName() + ".");
            //将域名分割为不同段落
            String[] dnsDomainNameSection = dnsDomainName.getDomainName().split("\\.");
            //储存punycode后的域名
            StringBuilder domainNameBuilder = new StringBuilder();
            try {
                for (String nameSection : dnsDomainNameSection) {
                    domainNameBuilder.append(IDN.toASCII(nameSection)).append(".");
                }
                if (!domainNameBuilder.toString().matches("^[.a-zA-Z0-9_-]+$")) {
                    throw new RuntimeException();
                }
            } catch (Exception exception) {
                result.put("code", -1);
                result.put("message", "操作失败");
                return result;
            }

            //将域名设置为punycode后的域名
            dnsDomainName.setDomainName(domainNameBuilder.toString().toLowerCase());
            //获取系统中允许的域名后缀
            List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
            //默认添加的域名为不合法后缀
            boolean includeDomainExtension = false;
            //当前添加域名的后缀
            String thisDomainExtension = "";
            //循环对比系统中的后缀
            for (SysDictData sysDictData : domainExtension) {
                //如果当前域名后缀是系统中的合法后缀
                if (dnsDomainName.getDomainName().endsWith(sysDictData.getDictValue())) {
                    //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                    thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                    //设置为系统合法后缀
                    includeDomainExtension = true;
                }
            }

            if (includeDomainExtension) {
                //获取域名验证key
                String validateTxtContentKey = PlatformDomainNameConstants.USER_ADD_DOMAIN_NAME_VALIDATE + SecurityUtils.getUserId();
                //获取txt验证内容
                String validateTxtContent = redisCache.getCacheObject(validateTxtContentKey);
                //如果不存在验证内容
                if (StringUtils.isEmpty(validateTxtContent)) {
                    validateTxtContent = "auth." + dnsDomainName.getDomainName() + "|" +IdUtils.fastSimpleUUID();
                    redisCache.setCacheObject(validateTxtContentKey, validateTxtContent, 30, TimeUnit.MINUTES);
                }
                result.put("code", 0);
                result.put("message", "操作成功");
                result.put("content", validateTxtContent.split("\\|")[1]);
                return result;
            } else {
                result.put("code", -1);
                result.put("message", "操作失败");
                return result;
            }
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    /**
     * 验证域名
     *
     * @param dnsDomainName 域名
     * @return 结果 0(操作成功) -1(验证失败) -2(验证过期，请重新设置TXT解析记录值并进行验证) -3(重复添加域名) -4(未知错误)
     */
    @Transactional
    @Override
    public Map<String, Object> validateDnsDomainName(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        //添加的域名名称不为空
        if (StringUtils.isNotEmpty(dnsDomainName.getDomainName())) {
            //将域名中的中文。替换为.
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().replaceAll("。", "."));
            //将域名结尾替换为.
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().endsWith(".") ? dnsDomainName.getDomainName() : dnsDomainName.getDomainName() + ".");
            //将域名分割为不同段落
            String[] dnsDomainNameSection = dnsDomainName.getDomainName().split("\\.");
            //储存punycode后的域名
            StringBuilder domainNameBuilder = new StringBuilder();
            try {
                for (String nameSection : dnsDomainNameSection) {
                    domainNameBuilder.append(IDN.toASCII(nameSection)).append(".");
                }
                if (!domainNameBuilder.toString().matches("^[.a-zA-Z0-9_-]+$")) {
                    throw new RuntimeException();
                }
            } catch (Exception exception) {
                result.put("code", -1);
                result.put("message", "验证失败");
                return result;
            }

            //将域名设置为punycode后的域名
            dnsDomainName.setDomainName(domainNameBuilder.toString().toLowerCase());
            //获取系统中允许的域名后缀
            List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
            //默认添加的域名为不合法后缀
            boolean includeDomainExtension = false;
            //当前添加域名的后缀
            String thisDomainExtension = "";
            //循环对比系统中的后缀
            for (SysDictData sysDictData : domainExtension) {
                //如果当前域名后缀是系统中的合法后缀
                if (dnsDomainName.getDomainName().endsWith(sysDictData.getDictValue())) {
                    //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                    thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                    //设置为系统合法后缀
                    includeDomainExtension = true;
                }
            }

            if (includeDomainExtension) {
                //获取域名验证key
                String validateTxtContentKey = PlatformDomainNameConstants.USER_ADD_DOMAIN_NAME_VALIDATE + SecurityUtils.getUserId();
                //获取txt验证内容
                String validateTxtContent = redisCache.getCacheObject(validateTxtContentKey);
                //如果不存在验证内容
                if (StringUtils.isEmpty(validateTxtContent)) {
                    validateTxtContent = "auth." + dnsDomainName.getDomainName() + "|" +IdUtils.fastSimpleUUID();
                    redisCache.setCacheObject(validateTxtContentKey, validateTxtContent, 30, TimeUnit.MINUTES);
                    result.put("code", -2);
                    result.put("message", "验证过期，请重新设置TXT解析记录值并进行验证");
                    result.put("content", validateTxtContent.split("\\|")[1]);
                    return result;
                } else {//如果存在就对比本次验证域名是不是缓存中的域名
                    try {
                        Lookup lookup = new Lookup(new Name("auth." + dnsDomainName.getDomainName()), Type.TXT, DClass.IN);
                        Record[] records = lookup.run();
                        if (records != null) {
                            TXTRecord txtRecord = (TXTRecord) records[0];
                            String[] validateSection = validateTxtContent.split("\\|");
                            if (validateSection[0].contentEquals("auth." + dnsDomainName.getDomainName()) && validateSection[1].contentEquals(txtRecord.getStrings().get(0))) {
                                //设置域名所属用户
                                dnsDomainName.setUserId(SecurityUtils.getUserId());
                                //查询该域名是否已经被本账号添加
                                DnsDomainName dnsDomainNameExist = dnsDomainNameMapper.selectDnsDomainNameByNameAndUserId(dnsDomainName);
                                //如果域名被此账号添加
                                if (dnsDomainNameExist != null) {
                                    if ("0".contentEquals(dnsDomainNameExist.getDomainNameStatus())) {
                                        //是我的域名
                                        result.put("code", -3);
                                        result.put("message", "重复添加域名");
                                    } else {
                                        try {
                                            dnsDomainName.setDomainNameStatus("-1");
                                            dnsDomainNameMapper.updateDnsDomainNameStatusByName(dnsDomainName);
                                            //更新区域
                                            dnsDomainNameExist.setDomainNameStatus("0");
                                            updateDnsDomainName(dnsDomainNameExist);
                                            dnsDomainNameUtils.transformZone(dnsDomainNameExist, "");
                                            result.put("code", 0);
                                            result.put("message", "操作成功");
                                        } catch (Exception e) {
                                            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                            result.put("code", -4);
                                            result.put("message", "未知错误");
                                            return result;
                                        }

                                    }
                                    return result;
                                } else {
                                    //查询该域名是否被别人添加
                                    List<DnsDomainName> dnsDomainNameExistList = dnsDomainNameMapper.selectDnsDomainNameByName(dnsDomainName);
                                    //没有被别人添加
                                    if (dnsDomainNameExistList.isEmpty()) {
                                        //可以添加
                                        Date now = DateUtils.getNowDate();
                                        dnsDomainName.setDomainNameDnssec(false);
                                        dnsDomainName.setDomainNameStatus("0");
                                        dnsDomainName.setCreateTime(now);
                                        dnsDomainName.setUpdateTime(now);
                                        dnsDomainNameMapper.insertDnsDomainName(dnsDomainName);
                                        //创建域名默认记录List
                                        List<Record> defaultRecordList = new ArrayList<>();
                                        try {
                                            //添加默认SOA记录
                                            defaultRecordList.add(new SOARecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domains@zhangmenglong.cn."), 600, 600, 600, 600, 600));
                                            //添加默认NS记录
                                            defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn.")));
                                            //添加默认NS记录
                                            defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns2.zhangmenglong.cn.")));
                                            //循环将域名默认记录添加到数据库
                                            for (Record record : defaultRecordList) {
                                                //创建域名记录
                                                DnsDomainNameRecord dnsDomainNameRecord = new DnsDomainNameRecord();
                                                //设置记录的域名id
                                                dnsDomainNameRecord.setDomainNameId(dnsDomainName.getId());
                                                //设置记录的记录名称
                                                dnsDomainNameRecord.setRecordName(record.getName().toString());
                                                //设置记录的记录类型
                                                dnsDomainNameRecord.setRecordType(String.valueOf(record.getType()));
                                                //设置记录的地理位置
                                                dnsDomainNameRecord.setRecordGeo("*");
                                                //设置记录的TTL
                                                dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                                //设置记录值
                                                dnsDomainNameRecord.setRecordValue(record.rdataToString());
                                                //设置记录的内容
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(record.toWire(Section.ANSWER)));
                                                //添加域名记录到数据库
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            }
                                            //更新区域
                                            dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                            result.put("code", 0);
                                            result.put("message", "操作成功");
                                            return result;
                                        } catch (Exception e) {
                                            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                            result.put("code", -4);
                                            result.put("message", "未知错误");
                                            return result;
                                        }
                                    } else {
                                        boolean domainNameStatusIsNormal = false;
                                        for (DnsDomainName domainName : dnsDomainNameExistList) {
                                            if (domainName.getDomainNameStatus().contentEquals("0")) {
                                                domainNameStatusIsNormal = true;
                                            }
                                        }
                                        if (domainNameStatusIsNormal) {
                                            dnsDomainName.setDomainNameStatus("-1");
                                            dnsDomainNameMapper.updateDnsDomainNameStatusByName(dnsDomainName);
                                        }
                                        //可以添加
                                        Date now = DateUtils.getNowDate();
                                        dnsDomainName.setDomainNameDnssec(false);
                                        dnsDomainName.setDomainNameStatus("0");
                                        dnsDomainName.setCreateTime(now);
                                        dnsDomainName.setUpdateTime(now);
                                        dnsDomainNameMapper.insertDnsDomainName(dnsDomainName);
                                        //创建域名默认记录List
                                        List<Record> defaultRecordList = new ArrayList<>();
                                        try {
                                            //添加默认SOA记录
                                            defaultRecordList.add(new SOARecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domains@zhangmenglong.cn."), 600, 600, 600, 600, 600));
                                            //添加默认NS记录
                                            defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn.")));
                                            //添加默认NS记录
                                            defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns2.zhangmenglong.cn.")));
                                            //循环将域名默认记录添加到数据库
                                            for (Record record : defaultRecordList) {
                                                //创建域名记录
                                                DnsDomainNameRecord dnsDomainNameRecord = new DnsDomainNameRecord();
                                                //设置记录的域名id
                                                dnsDomainNameRecord.setDomainNameId(dnsDomainName.getId());
                                                //设置记录的记录名称
                                                dnsDomainNameRecord.setRecordName(record.getName().toString());
                                                //设置记录的记录类型
                                                dnsDomainNameRecord.setRecordType(String.valueOf(record.getType()));
                                                //设置记录的地理位置
                                                dnsDomainNameRecord.setRecordGeo("*");
                                                //设置记录的TTL
                                                dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                                //设置记录值
                                                dnsDomainNameRecord.setRecordValue(record.rdataToString());
                                                //设置记录的内容
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(record.toWire(Section.ANSWER)));
                                                //添加域名记录到数据库
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            }
                                            dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                            result.put("code", 0);
                                            result.put("message", "操作成功");
                                            return result;
                                        } catch (Exception e) {
                                            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                            result.put("code", -4);
                                            result.put("message", "未知错误");
                                            return result;
                                        }
                                    }

                                }
                            } else {
                                result.put("code", -1);
                                result.put("message", "验证失败");
                                return result;
                            }
                        } else {
                            result.put("code", -1);
                            result.put("message", "验证失败");
                            return result;
                        }
                    } catch (TextParseException e) {
                        result.put("code", -1);
                        result.put("message", "验证失败");
                        return result;
                    }
                }
            } else {
                result.put("code", -1);
                result.put("message", "验证失败");
                return result;
            }
        } else {
            result.put("code", -1);
            result.put("message", "验证失败");
            return result;
        }
    }

    /**
     * 查询域名
     * 
     * @param id 域名主键
     * @return 域名
     */
    @Override
    public DnsDomainName selectDnsDomainNameById(Long id)
    {
        return dnsDomainNameMapper.selectDnsDomainNameById(id);
    }

    /**
     * 查询域名列表
     * 
     * @param dnsDomainName 域名
     * @return 域名
     */
    @Override
    public List<DnsDomainName> selectDnsDomainNameList(DnsDomainName dnsDomainName)
    {
        dnsDomainName.setUserId(SecurityUtils.getUserId());
        return dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);
    }

    /**
     * 新增域名
     * 
     * @param dnsDomainName 域名
     * @return 结果 0(操作成功) -1(域名为空) -2(域名格式错误) -3(不支持的后缀) -4(重复添加域名) -5(子域名需要验证) -6(域名被他人添加，需要验证) -7(未知错误)
     */

    @Transactional
    @Override
    public Map<String, Object> insertDnsDomainName(DnsDomainName dnsDomainName)
    {
        Map<String, Object> result = new HashMap<>();
        if (StringUtils.isNotEmpty(dnsDomainName.getDomainName())) {
            //将域名中的中文。替换为.
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().replaceAll("。", "."));
            //将域名结尾替换为.
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().endsWith(".") ? dnsDomainName.getDomainName() : dnsDomainName.getDomainName() + ".");
            //将域名分割为不同段落
            String[] dnsDomainNameSection = dnsDomainName.getDomainName().split("\\.");
            //储存punycode后的域名
            StringBuilder domainNameBuilder = new StringBuilder();
            try {
                //循环拼接转换后的域名
                for (String nameSection : dnsDomainNameSection) {
                    domainNameBuilder.append(IDN.toASCII(nameSection)).append(".");
                }
                //如果转换后的域名不符合格式就抛出异常
                if (!domainNameBuilder.toString().matches("^[.a-zA-Z0-9_-]+$")) {
                    throw new RuntimeException();
                }
            } catch (Exception exception) {
                //捕获异常
                result.put("code", -2);
                result.put("message", "域名格式错误");
                return result;
            }

            //将域名设置为punycode后的域名
            dnsDomainName.setDomainName(domainNameBuilder.toString().toLowerCase());
            //获取系统中允许的域名后缀
            List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
            //默认添加的域名为不合法后缀
            boolean includeDomainExtension = false;
            //当前添加域名的后缀
            String thisDomainExtension = "";
            //循环对比系统中的后缀
            for (SysDictData sysDictData : domainExtension) {
                //如果当前域名后缀是系统中的合法后缀
                if (dnsDomainName.getDomainName().endsWith(sysDictData.getDictValue())) {
                    //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                    thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                    //设置为系统合法后缀
                    includeDomainExtension = true;
                }
            }
            //如果是系统合法后缀
            if (includeDomainExtension) {
                //设置域名所属用户
                dnsDomainName.setUserId(SecurityUtils.getUserId());
                //查询该域名是否已经被本账号添加
                DnsDomainName dnsDomainNameExist = dnsDomainNameMapper.selectDnsDomainNameByNameAndUserId(dnsDomainName);
                //如果域名被此账号添加
                if (dnsDomainNameExist != null) {
                    //是我的域名
                    result.put("code", -4);
                    result.put("message", "重复添加域名");
                    return result;
                } else {
                    //查询该域名是否被别人添加
                    List<DnsDomainName> dnsDomainNameExistList = dnsDomainNameMapper.selectDnsDomainNameByName(dnsDomainName);
                    //没有被别人添加
                    if (dnsDomainNameExistList.isEmpty()) {
                        //判断该域名是否为子域名
                        boolean isSubName = dnsDomainName.getDomainName().substring(0, dnsDomainName.getDomainName().length() - thisDomainExtension.length()).contains(".");
                        if (isSubName) {
                            //获取域名验证key
                            String validateTxtContentKey = PlatformDomainNameConstants.USER_ADD_DOMAIN_NAME_VALIDATE + SecurityUtils.getUserId();
                            //获取txt验证内容
                            String validateTxtContent = redisCache.getCacheObject(validateTxtContentKey);
                            //如果不存在验证内容
                            if (StringUtils.isEmpty(validateTxtContent)) {
                                validateTxtContent = "auth." + dnsDomainName.getDomainName() + "|" +IdUtils.fastSimpleUUID();
                                redisCache.setCacheObject(validateTxtContentKey, validateTxtContent, 30, TimeUnit.MINUTES);
                            } else {//如果存在就对比本次验证域名是不是缓存中的域名
                                //如果本次验证域名不是缓存中的域名则重新生成验证
                                if (!validateTxtContent.startsWith("auth." + dnsDomainName.getDomainName() + "|")) {
                                    validateTxtContent = "auth." + dnsDomainName.getDomainName() + "|" +IdUtils.fastSimpleUUID();
                                    redisCache.setCacheObject(validateTxtContentKey, validateTxtContent, 30, TimeUnit.MINUTES);
                                }
                            }
                            result.put("code", -5);
                            result.put("message", "子域名需要验证");
                            result.put("content", validateTxtContent.split("\\|")[1]);
                            return result;
                        } else {
                            //可以添加
                            Date now = DateUtils.getNowDate();
                            dnsDomainName.setDomainNameDnssec(false);
                            dnsDomainName.setDomainNameStatus("0");
                            dnsDomainName.setCreateTime(now);
                            dnsDomainName.setUpdateTime(now);
                            dnsDomainNameMapper.insertDnsDomainName(dnsDomainName);
                            //创建域名默认记录List
                            List<Record> defaultRecordList = new ArrayList<>();
                            try {
                                //添加默认SOA记录
                                defaultRecordList.add(new SOARecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domains@zhangmenglong.cn."), 600, 600, 600, 600, 600));
                                //添加默认NS记录
                                defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn.")));
                                //添加默认NS记录
                                defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns2.zhangmenglong.cn.")));
                                //循环将域名默认记录添加到数据库
                                for (Record record : defaultRecordList) {
                                    //创建域名记录
                                    DnsDomainNameRecord dnsDomainNameRecord = new DnsDomainNameRecord();
                                    //设置记录的域名id
                                    dnsDomainNameRecord.setDomainNameId(dnsDomainName.getId());
                                    //设置记录的记录名称
                                    dnsDomainNameRecord.setRecordName(record.getName().toString());
                                    //设置记录的记录类型
                                    dnsDomainNameRecord.setRecordType(String.valueOf(record.getType()));
                                    //设置记录的地理位置
                                    dnsDomainNameRecord.setRecordGeo("*");
                                    //设置记录的TTL
                                    dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                    //设置记录值
                                    dnsDomainNameRecord.setRecordValue(record.rdataToString());
                                    //设置记录的内容
                                    dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(record.toWire(Section.ANSWER)));
                                    //添加域名记录到数据库
                                    dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                }
                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                result.put("code", 0);
                                result.put("message", "操作成功");
                                return result;
                            } catch (Exception e) {
                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                result.put("code", -7);
                                result.put("message", "未知错误");
                                return result;
                            }
                        }
                    } else {
                        //域名已经被别人添加
                        //获取域名验证key
                        String validateTxtContentKey = PlatformDomainNameConstants.USER_ADD_DOMAIN_NAME_VALIDATE + SecurityUtils.getUserId();
                        //获取txt验证内容
                        String validateTxtContent = redisCache.getCacheObject(validateTxtContentKey);
                        //如果不存在验证内容
                        if (StringUtils.isEmpty(validateTxtContent)) {
                            validateTxtContent = "auth." + dnsDomainName.getDomainName() + "|" +IdUtils.fastSimpleUUID();
                            redisCache.setCacheObject(validateTxtContentKey, validateTxtContent, 30, TimeUnit.MINUTES);
                        } else {//如果存在就对比本次验证域名是不是缓存中的域名
                            //如果本次验证域名不是缓存中的域名则重新生成验证
                            if (!validateTxtContent.startsWith("auth." + dnsDomainName.getDomainName() + "|")) {
                                validateTxtContent = "auth." + dnsDomainName.getDomainName() + "|" +IdUtils.fastSimpleUUID();
                                redisCache.setCacheObject(validateTxtContentKey, validateTxtContent, 30, TimeUnit.MINUTES);
                            }
                        }
                        result.put("code", -6);
                        result.put("message", "域名已被他人添加，需要验证");
                        result.put("content", validateTxtContent.split("\\|")[1]);
                        return result;
                    }

                }
            } else {
                result.put("code", -3);
                result.put("message", "不支持的后缀");
                return result;
            }
        } else {
            result.put("code", -1);
            result.put("message", "域名为空");
            return result;
        }
    }

    /**
     * 修改域名DNSSEC
     * 
     * @param dnsDomainName 域名
     * @return 结果
     */
    @Transactional
    @Override
    public Map<String, Object> updateDnsDomainNameDnssec(DnsDomainName dnsDomainName)
    {
        Map<String, Object> result = new HashMap<>();
        Boolean dnssecEnable = dnsDomainName.getDomainNameDnssec();
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());
        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {
            if (dnssecEnable) {
                try {
                    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
                    keyPairGenerator.initialize(new ECGenParameterSpec("secp256r1"), new SecureRandom());
                    KeyPair kskKeyPair = keyPairGenerator.generateKeyPair();
                    ECPublicKey kskEcPublicKey = (ECPublicKey) kskKeyPair.getPublic();
                    ECPrivateKey kskEcPrivateKey = (ECPrivateKey) kskKeyPair.getPrivate();
                    KeyPair zskKeyPair = keyPairGenerator.generateKeyPair();
                    ECPublicKey zskEcPublicKey = (ECPublicKey) zskKeyPair.getPublic();
                    ECPrivateKey zskEcPrivateKey = (ECPrivateKey) zskKeyPair.getPrivate();
                    DNSKEYRecord kskRecord = new DNSKEYRecord(Name.fromString(dnsDomainName.getDomainName()), DClass.IN, 3600, 0x101, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, kskEcPublicKey);
                    DSRecord dsRecord = new DSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 3600, DNSSEC.Digest.SHA256, kskRecord);
                    dnsDomainName.setDomainNameDnssec(true);
                    dnsDomainName.setDomainNameDnssecKskPrivateKey(Base64.getEncoder().encodeToString(kskEcPrivateKey.getEncoded()));
                    dnsDomainName.setDomainNameDnssecKskPublicKey(Base64.getEncoder().encodeToString(kskEcPublicKey.getEncoded()));
                    dnsDomainName.setDomainNameDnssecZskPrivateKey(Base64.getEncoder().encodeToString(zskEcPrivateKey.getEncoded()));
                    dnsDomainName.setDomainNameDnssecZskPublicKey(Base64.getEncoder().encodeToString(zskEcPublicKey.getEncoded()));
                    dnsDomainName.setDomainNameDnssecDsKeyTag(String.valueOf(dsRecord.getFootprint()));
                    dnsDomainName.setDomainNameDnssecDsDigestValue(base16.toString(dsRecord.getDigest()));
                    updateDnsDomainName(dnsDomainName);
                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                    result.put("code", 0);
                    result.put("message", "操作成功");
                    return result;
                } catch (Exception ignored) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    result.put("code", -1);
                    result.put("message", "操作失败");
                    return result;
                }
            } else {
                try {
                    dnsDomainName.setDomainNameDnssec(false);
                    updateDnsDomainName(dnsDomainName);
                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                    result.put("code", 0);
                    result.put("message", "操作成功");
                    return result;
                } catch (Exception ignored) {
                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                    result.put("code", -1);
                    result.put("message", "操作失败");
                    return result;
                }
            }
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    @Override
    public void updateDnsDomainName(DnsDomainName dnsDomainName) {
        dnsDomainName.setUpdateTime(DateUtils.getNowDate());
        dnsDomainNameMapper.updateDnsDomainName(dnsDomainName);
    }

    /**
     * 批量删除域名
     * 
     * @param ids 需要删除的域名主键
     * @return 结果
     */
    @Transactional
    @Override
    public Map<String, Object> deleteDnsDomainNameByIds(Long[] ids)
    {
        long userId = SecurityUtils.getUserId();
        Map<String, Object> result = new HashMap<>();
        List<DnsDomainName> dnsDomainNameList = new LinkedList<>();
        for (Long id : ids) {
            DnsDomainName dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(id);
            if ((dnsDomainName != null) && (dnsDomainName.getUserId() == userId)) {
                dnsDomainNameList.add(dnsDomainName);
            } else {
                result.put("code", -1);
                result.put("message", "操作失败");
                return result;
            }
        }
        dnsDomainNameMapper.deleteDnsDomainNameByIds(ids);
        dnsDomainNameRecordMapper.deleteDnsDomainNameRecordByDomainNameIds(ids);
        for (DnsDomainName dnsDomainName : dnsDomainNameList) {
            if ("0".contentEquals(dnsDomainName.getDomainNameStatus())) {
                try {
                    dnsDomainNameUtils.deleteZone(dnsDomainName);
                } catch (Exception ignored) {}
            }
        }
        result.put("code", 0);
        result.put("message", "操作成功");
        return result;
    }

    /**
     * 删除域名信息
     * 
     * @param id 域名主键
     * @return 结果
     */
    @Override
    public int deleteDnsDomainNameById(Long id)
    {
        return dnsDomainNameMapper.deleteDnsDomainNameById(id);
    }
}

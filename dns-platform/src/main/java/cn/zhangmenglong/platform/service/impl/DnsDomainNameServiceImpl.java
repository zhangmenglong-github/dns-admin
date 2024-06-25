package cn.zhangmenglong.platform.service.impl;

import java.net.IDN;
import java.net.InetAddress;
import java.security.*;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import cn.zhangmenglong.common.core.domain.entity.SysDictData;
import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.*;
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
import cn.zhangmenglong.platform.utils.SnowFlakeUtils;
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

    @Autowired
    private SnowFlakeUtils snowFlakeUtils;


    @Transactional
    @Override
    public Map<String, Object> deleteDnsDomainNameRecordByIds(Long[] ids) {
        long userId = SecurityUtils.getUserId();
        Map<String, Object> result = new HashMap<>();
        DnsDomainName dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(ids[0]);
        if ((dnsDomainName != null) && (dnsDomainName.getUserId() == userId) && (dnsDomainName.getDomainNameStatus().contentEquals("0"))) {
            DnsDomainNameRecord dnsDomainNameRecordSoa = dnsDomainNameRecordMapper.selectDnsDomainNameRecordSoaRecordByDomainNameId(dnsDomainName.getId());
            boolean isContainsSoaRecord = false;
            for (int index = 1; index < ids.length; index++) {
                if (ids[index] == dnsDomainNameRecordSoa.getId().longValue()) {
                    isContainsSoaRecord = true;
                    break;
                }
            }
            if (isContainsSoaRecord) {
                result.put("code", -1);
                result.put("message", "SOA记录不可删除");
            } else {
                List<DnsDomainNameRecord> dnsDomainNameRecordNsRecordList = dnsDomainNameRecordMapper.selectDnsDomainNameRecordNsRecordByDomainNameId(dnsDomainName.getId());
                List<Long> nsRecord = dnsDomainNameRecordNsRecordList.stream().map(DnsDomainNameRecord::getId).collect(Collectors.toList());
                for (Long id : ids) {
                    nsRecord.remove(id);
                }
                if (nsRecord.size() < 2) {
                    result.put("code", -2);
                    result.put("message", "至少存在两条NS记录");
                } else {
                    for (int index = 1; index < ids.length; index++) {
                        DnsDomainNameRecord dnsDomainNameRecord = dnsDomainNameRecordMapper.selectDnsDomainNameRecordById(ids[index]);
                        if (dnsDomainNameRecord.getDomainNameId().longValue() == ids[0]) {
                            dnsDomainNameRecordMapper.deleteDnsDomainNameRecordById(dnsDomainNameRecord.getId());
                        }
                    }
                    try {
                        dnsDomainNameUtils.transformZone(dnsDomainName, "");
                    } catch (Exception ignored) {}
                    result.put("code", 0);
                    result.put("message", "操作成功");
                }
            }
            
        } else {
            result.put("code", -3);
            result.put("message", "操作失败");
        }
        return result;
    }

    @Override
    public Map<String, Object> updateDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord) {
        Map<String, Object> result = new HashMap<>();
        if (dnsDomainNameRecord.getDomainNameId() != null) {//对应域名不为空
            DnsDomainName dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainNameRecord.getDomainNameId());
            if ((dnsDomainName != null) && (dnsDomainName.getUserId().longValue() == SecurityUtils.getUserId().longValue()) && (dnsDomainName.getDomainNameStatus().contentEquals("0"))) {
                dnsDomainNameRecord.setRecordName(((StringUtils.isEmpty(dnsDomainNameRecord.getRecordName()) || dnsDomainNameRecord.getRecordName().contentEquals("@")) ? dnsDomainName.getDomainName() : (dnsDomainNameRecord.getRecordName() + "." + dnsDomainName.getDomainName())));
                dnsDomainNameRecord.setRecordName(dnsDomainNameUtils.nameToPunycode(dnsDomainNameRecord.getRecordName()));
                if (StringUtils.isEmpty(dnsDomainNameRecord.getRecordName())) {
                    result.put("code", -1);
                    result.put("message", "主机记录错误");
                } else if (StringUtils.isEmpty(dnsDomainNameRecord.getRecordGeo()) || (DictUtils.getDictLabel("geo_code", dnsDomainNameRecord.getRecordGeo()) == null)) {
                    result.put("code", -2);
                    result.put("message", "地理位置错误");
                } else if ((dnsDomainNameRecord.getRecordTtl() == null) || (dnsDomainNameRecord.getRecordTtl() < 0) || (dnsDomainNameRecord.getRecordTtl() > 86400)) {
                    result.put("code", -3);
                    result.put("message", "TTL错误");
                } else {
                    try {
                        Name recordName = new Name(dnsDomainNameRecord.getRecordName());
                        Name domainName = new Name(dnsDomainName.getDomainName());
                        List<DnsDomainNameRecord> existDnsDomainNameRecordList = dnsDomainNameRecordMapper.selectDnsDomainNameRecordByRecordName(dnsDomainNameRecord.getRecordName());
                        switch (dnsDomainNameRecord.getRecordType()) {
                            case Type.A: {
                                InetAddress address = dnsDomainNameUtils.getIpv4Address(String.valueOf(dnsDomainNameRecord.getParams().get("ipv4")));
                                if (address != null) {
                                    //Name name, int dclass, long ttl, InetAddress address
                                    ARecord aRecord = new ARecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), address);
                                    boolean existCnameRecord = false;
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.A) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(aRecord.toWire(Section.ANSWER))))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existCnameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existNsRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -4);
                                            result.put("message", "该条A记录已存在");
                                        } else if (existCnameRecord) {
                                            result.put("code", -5);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(aRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -6);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -7);
                                            result.put("message", "该条A记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -8);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -9);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(aRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -10);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                } else {
                                    result.put("code", -11);
                                    result.put("message", "ipv4地址格式错误");
                                }
                                break;
                            }
                            case Type.NS: {
                                String nsName = String.valueOf(dnsDomainNameRecord.getParams().get("nsName"));
                                //储存punycode后的域名
                                nsName = dnsDomainNameUtils.nameToPunycode(nsName);
                                if (StringUtils.isEmpty(nsName)) {
                                    result.put("code", -12);
                                    result.put("message", "NS记录格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (nsName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        //Name name, int dclass, long ttl, Name target
                                        Name targetName = new Name(nsName);
                                        NSRecord nsRecord = new NSRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), targetName);
                                        boolean existSameRecord = false;
                                        boolean existOtherRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(nsRecord.toWire(Section.ANSWER))))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() != Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existOtherRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -13);
                                                result.put("message", "该条NS记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(nsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(nsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -14);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -15);
                                                result.put("message", "该条NS记录已存在");
                                            } else if (existOtherRecord) {
                                                result.put("code", -16);
                                                result.put("message", "与已存在的其他记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(nsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(nsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -17);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -18);
                                        result.put("message", "NS记录域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.CNAME: {

                                String cnameName = String.valueOf(dnsDomainNameRecord.getParams().get("cnameName"));
                                //储存punycode后的域名
                                cnameName = dnsDomainNameUtils.nameToPunycode(cnameName);
                                if (StringUtils.isEmpty(cnameName)) {
                                    result.put("code", -19);
                                    result.put("message", "CNAME记录格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (cnameName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        //name, Type.CNAME, dclass, ttl, alias
                                        Name targetName = new Name(cnameName);
                                        CNAMERecord cnameRecord = new CNAMERecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), targetName);
                                        boolean existSameRecord = false;
                                        boolean existNotNsOrSoaOrMxOrTxtOtherRecord = false;
                                        boolean existOtherRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(cnameRecord.toWire(Section.ANSWER))))) {
                                                existSameRecord = true;
                                            }
                                            if (((domainNameRecord.getRecordType() != Type.NS) && (domainNameRecord.getRecordType() != Type.SOA) && (domainNameRecord.getRecordType() != Type.MX) && (domainNameRecord.getRecordType() != Type.TXT)) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))  && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existNotNsOrSoaOrMxOrTxtOtherRecord = true;
                                            }
                                            if((domainNameRecord.getRecordType() != Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existOtherRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -20);
                                                result.put("message", "该条CNAME记录已存在");
                                            } else if (existNotNsOrSoaOrMxOrTxtOtherRecord) {
                                                result.put("code", -21);
                                                result.put("message", "与已存在的其他记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(cnameRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(cnameRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -22);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -23);
                                                result.put("message", "该条CNAME记录已存在");
                                            } else if (existOtherRecord) {
                                                result.put("code", -24);
                                                result.put("message", "与已存在的其他记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(cnameRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(cnameRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -25);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -26);
                                        result.put("message", "CNAME记录域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.SOA: {
                                DnsDomainNameRecord domainNameRecordSoa = dnsDomainNameRecordMapper.selectDnsDomainNameRecordById(dnsDomainNameRecord.getId());
                                if ((domainNameRecordSoa != null) && (domainNameRecordSoa.getRecordType() == Type.SOA)) {
                                    String host = String.valueOf(dnsDomainNameRecord.getParams().get("host"));
                                    String admin = String.valueOf(dnsDomainNameRecord.getParams().get("admin"));
                                    long serial;
                                    long refresh;
                                    long retry;
                                    long expire;
                                    long minimum;
                                    try {
                                        serial = Long.parseLong(String.valueOf(dnsDomainNameRecord.getParams().get("serial")));
                                    } catch (Exception exception) {
                                        serial = -1L;
                                    }
                                    try {
                                        refresh = Long.parseLong(String.valueOf(dnsDomainNameRecord.getParams().get("refresh")));
                                    } catch (Exception exception) {
                                        refresh = -1L;
                                    }
                                    try {
                                        retry = Long.parseLong(String.valueOf(dnsDomainNameRecord.getParams().get("retry")));
                                    } catch (Exception exception) {
                                        retry = -1L;
                                    }
                                    try {
                                        expire = Long.parseLong(String.valueOf(dnsDomainNameRecord.getParams().get("expire")));
                                    } catch (Exception exception) {
                                        expire = -1L;
                                    }
                                    try {
                                        minimum = Long.parseLong(String.valueOf(dnsDomainNameRecord.getParams().get("minimum")));
                                    } catch (Exception exception) {
                                        minimum = -1L;
                                    }
                                    if (StringUtils.isEmpty(host) || (dnsDomainNameUtils.nameToPunycode(host) == null)) {
                                        result.put("code", -27);
                                        result.put("message", "主名称服务器错误");
                                    } else if (StringUtils.isEmpty(admin) || (dnsDomainNameUtils.emailToPunycode(admin) == null)) {
                                        result.put("code", -28);
                                        result.put("message", "管理员邮箱错误");
                                    } else if ((serial < 0)) {
                                        result.put("code", -29);
                                        result.put("message", "区域序列号错误");
                                    } else if ((refresh < 0)) {
                                        result.put("code", -30);
                                        result.put("message", "序列号刷新时间错误");
                                    } else if ((retry < 0)) {
                                        result.put("code", -31);
                                        result.put("message", "序列号重试时间错误");
                                    } else if ((expire < 0)) {
                                        result.put("code", -32);
                                        result.put("message", "序列号过期时间错误");
                                    } else if ((minimum < 0)) {
                                        result.put("code", -33);
                                        result.put("message", "区域中最小TTL错误");
                                    } else {
                                        host = dnsDomainNameUtils.nameToPunycode(host);
                                        admin = dnsDomainNameUtils.emailToPunycode(admin) + ".";
                                        SOARecord soaRecord = new SOARecord(domainName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), Name.fromString(host), Name.fromString(admin), serial, refresh, retry, expire, minimum);
                                        if (domainNameRecordSoa.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(soaRecord.toWire(Section.ANSWER)))) {
                                            result.put("code", -34);
                                            result.put("message", "该条SOA记录已存在");
                                        } else {
                                            dnsDomainNameRecord.setRecordName(dnsDomainName.getDomainName());
                                            dnsDomainNameRecord.setRecordGeo("*");
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(soaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(soaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -35);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                } else {
                                    result.put("code", -36);
                                    result.put("message", "操作失败");
                                }

                                break;
                            }
                            case Type.MX: {
                                String mxName = String.valueOf(dnsDomainNameRecord.getParams().get("mxName"));
                                //储存punycode后的域名
                                mxName = dnsDomainNameUtils.nameToPunycode(mxName);
                                int priority;
                                try {
                                    priority = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("priority")));
                                } catch (Exception exception) {
                                    priority = -1;
                                }

                                if ((priority < 0) || (priority > 65535)) {
                                    result.put("code", -37);
                                    result.put("message", "优先级错误");
                                } else if (StringUtils.isEmpty(mxName)) {
                                    result.put("code", -38);
                                    result.put("message", "MX记录格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (mxName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        Name targetName = new Name(mxName);
                                        MXRecord mxRecord = new MXRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), priority, targetName);
                                        boolean existSameRecord = false;
                                        boolean existNsRecord = false;
                                        boolean existCnameRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.MX) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(mxRecord.toWire(Section.ANSWER))))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existNsRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existCnameRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -39);
                                                result.put("message", "该条MX记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(mxRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(mxRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -40);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -41);
                                                result.put("message", "该条MX记录已存在");
                                            } else if (existNsRecord) {
                                                result.put("code", -42);
                                                result.put("message", "与已存在的NS记录冲突");
                                            } else if (existCnameRecord) {
                                                result.put("code", -43);
                                                result.put("message", "与已存在的CNAME记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(mxRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(mxRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -44);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -45);
                                        result.put("message", "MX记录邮件服务器域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.TXT: {
                                String txtContent = String.valueOf(dnsDomainNameRecord.getParams().get("txtContent"));
                                if (StringUtils.isEmpty(txtContent)) {
                                    result.put("code", -46);
                                    result.put("message", "TXT记录格式错误");
                                } else {
                                    //Name name, int dclass, long ttl, String string
                                    TXTRecord txtRecord = new TXTRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), txtContent);
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    boolean existCnameRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.TXT) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(txtRecord.toWire(Section.ANSWER))))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existNsRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existCnameRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -47);
                                            result.put("message", "该条TXT记录已存在");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(txtRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(txtRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -48);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -49);
                                            result.put("message", "该条TXT记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -50);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -51);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(txtRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(txtRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -52);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                            case Type.AAAA: {
                                InetAddress address = dnsDomainNameUtils.getIpv6Address(String.valueOf(dnsDomainNameRecord.getParams().get("ipv6")));
                                if (address != null) {
                                    //Name name, int dclass, long ttl, InetAddress address
                                    AAAARecord aaaaRecord = new AAAARecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), address);
                                    boolean existCnameRecord = false;
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.AAAA) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(aaaaRecord.toWire(Section.ANSWER)))) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existCnameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existNsRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -53);
                                            result.put("message", "该条AAAA记录已存在");
                                        } else if (existCnameRecord) {
                                            result.put("code", -54);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(aaaaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aaaaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -55);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -56);
                                            result.put("message", "该条AAAA记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -57);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -58);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(aaaaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aaaaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -59);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                } else {
                                    result.put("code", -60);
                                    result.put("message", "ipv6地址错误");
                                }
                                break;
                            }
                            case Type.SRV: {
                                String srvName = String.valueOf(dnsDomainNameRecord.getParams().get("srvName"));
                                //储存punycode后的域名
                                srvName = dnsDomainNameUtils.nameToPunycode(srvName);
                                int priority;
                                int weight;
                                int port;
                                try {
                                    priority = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("priority")));
                                } catch (Exception exception) {
                                    priority = -1;
                                }
                                try {
                                    weight = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("weight")));
                                } catch (Exception exception) {
                                    weight = -1;
                                }
                                try {
                                    port = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("port")));
                                } catch (Exception exception) {
                                    port = -1;
                                }

                                if ((priority < 0) || (priority > 65535)) {
                                    result.put("code", -61);
                                    result.put("message", "优先级错误");
                                } else if ((weight < 0) || (weight > 65535)) {
                                    result.put("code", -62);
                                    result.put("message", "权重错误");
                                } else if ((port < 0) || (port > 65535)) {
                                    result.put("code", -63);
                                    result.put("message", "端口错误");
                                } else if (StringUtils.isEmpty(srvName)) {
                                    result.put("code", -64);
                                    result.put("message", "目标地址格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (srvName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        Name targetName = new Name(srvName);
                                        //Name name, int dclass, long ttl, int priority, int weight, int port, Name target
                                        SRVRecord srvRecord = new SRVRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), priority, weight, port, targetName);
                                        boolean existSameRecord = false;
                                        boolean existNsRecord = false;
                                        boolean existCnameRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.SRV) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(srvRecord.toWire(Section.ANSWER))))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existNsRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existCnameRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -65);
                                                result.put("message", "该条SRV记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(srvRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(srvRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -66);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -67);
                                                result.put("message", "该条SRV记录已存在");
                                            } else if (existNsRecord) {
                                                result.put("code", -68);
                                                result.put("message", "与已存在的NS记录冲突");
                                            } else if (existCnameRecord) {
                                                result.put("code", -69);
                                                result.put("message", "与已存在的CNAME记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(srvRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(srvRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -70);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -71);
                                        result.put("message", "SRV目标服务器后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.HTTPS: {
                                String httpsName = String.valueOf(dnsDomainNameRecord.getParams().get("httpsName"));
                                //储存punycode后的域名
                                httpsName = dnsDomainNameUtils.nameToPunycode(httpsName);
                                List<HTTPSRecord.ParameterBase> params = new ArrayList<>();
                                int priority;
                                try {
                                    priority = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("priority")));
                                } catch (Exception exception) {
                                    priority = -1;
                                }
                                try {
                                    String alpnString = String.valueOf(dnsDomainNameRecord.getParams().get("alpn"));
                                    if (StringUtils.isNotEmpty(alpnString)) {
                                        HTTPSRecord.ParameterMandatory mandatory = new HTTPSRecord.ParameterMandatory();
                                        mandatory.fromString("alpn");
                                        HTTPSRecord.ParameterAlpn alpn = new HTTPSRecord.ParameterAlpn();
                                        alpn.fromString(alpnString);
                                        params.add(mandatory);
                                        params.add(alpn);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -72);
                                    result.put("message", "alpn格式错误");
                                }
                                try {
                                    String ipv4hintString = String.valueOf(dnsDomainNameRecord.getParams().get("ipv4hint"));
                                    if (StringUtils.isNotEmpty(ipv4hintString)) {
                                        HTTPSRecord.ParameterIpv4Hint ipv4hint = new HTTPSRecord.ParameterIpv4Hint();
                                        ipv4hint.fromString(ipv4hintString);
                                        params.add(ipv4hint);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -73);
                                    result.put("message", "ipv4hint格式错误");
                                }
                                try {
                                    String ipv6hintString = String.valueOf(dnsDomainNameRecord.getParams().get("ipv6hint"));
                                    if (StringUtils.isNotEmpty(ipv6hintString)) {
                                        HTTPSRecord.ParameterIpv6Hint ipv6hint = new HTTPSRecord.ParameterIpv6Hint();
                                        ipv6hint.fromString(ipv6hintString);
                                        params.add(ipv6hint);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -74);
                                    result.put("message", "ipv6hint格式错误");
                                }
                                try {
                                    String portString = String.valueOf(dnsDomainNameRecord.getParams().get("port"));
                                    if (StringUtils.isNotEmpty(portString)) {
                                        HTTPSRecord.ParameterPort port = new SVCBBase.ParameterPort();
                                        port.fromString(portString);
                                        params.add(port);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -75);
                                    result.put("message", "port格式错误");
                                }

                                if ((priority < 0) || (priority > 65535)) {
                                    result.put("code", -76);
                                    result.put("message", "优先级错误");
                                } else if (result.isEmpty()) {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (httpsName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        Name targetName = new Name(httpsName);
                                        HTTPSRecord httpsRecord = new HTTPSRecord(recordName, DClass.IN, 300, priority, targetName, params);
                                        boolean existSameRecord = false;
                                        boolean existNsRecord = false;
                                        boolean existCnameRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.HTTPS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(httpsRecord.toWire(Section.ANSWER))))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existNsRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                                existCnameRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -77);
                                                result.put("message", "该条HTTPS记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(httpsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(httpsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -78);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -79);
                                                result.put("message", "该条HTTPS记录已存在");
                                            } else if (existNsRecord) {
                                                result.put("code", -80);
                                                result.put("message", "与已存在的NS记录冲突");
                                            } else if (existCnameRecord) {
                                                result.put("code", -81);
                                                result.put("message", "与已存在的CNAME记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setRecordValue(httpsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(httpsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -82);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -83);
                                        result.put("message", "HTTPS目标域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.CAA: {
                                int flag;
                                try {
                                    flag = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("flag")));
                                } catch (Exception exception) {
                                    flag = -1;
                                }
                                String tag = String.valueOf(dnsDomainNameRecord.getParams().get("tag"));
                                String value = String.valueOf(dnsDomainNameRecord.getParams().get("value"));
                                if ((flag < 0) || (flag > 255)) {
                                    result.put("code", -84);
                                    result.put("message", "flag格式错误");
                                } else if (StringUtils.isEmpty(tag) || (!tag.contentEquals("issue") && !tag.contentEquals("issuewild") && !tag.contentEquals("iodef"))) {
                                    result.put("code", -85);
                                    result.put("message", "tag格式错误");
                                } else if (StringUtils.isEmpty(value)) {
                                    result.put("code", -86);
                                    result.put("message", "value不能为空");
                                } else if ((tag.contentEquals("iodef") && (dnsDomainNameUtils.emailToPunycode(value) == null))) {
                                    result.put("code", -87);
                                    result.put("message", "value邮箱格式错误");
                                } else if ((!tag.contentEquals("iodef") && (dnsDomainNameUtils.nameToPunycode(value) == null))) {
                                    result.put("code", -88);
                                    result.put("message", "value域名格式错误");
                                } else {
                                    //Name name, int dclass, long ttl, int flags, String tag, String value
                                    CAARecord caaRecord = new CAARecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), flag, tag, value);
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    boolean existCnameRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.CAA) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordContent().contentEquals(Base64.getEncoder().encodeToString(caaRecord.toWire(Section.ANSWER))))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existNsRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getId().longValue() != dnsDomainNameRecord.getId().longValue())) {
                                            existCnameRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -89);
                                            result.put("message", "该条CAA记录已存在");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(caaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(caaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -90);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -91);
                                            result.put("message", "该条CAA记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -92);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -93);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setRecordValue(caaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(caaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -94);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                            default: {
                                result.put("code", -95);
                                result.put("message", "记录类型错误");
                            }
                        }
                    } catch (TextParseException e) {
                        result.put("code", -96);
                        result.put("message", "未知错误");
                    }

                }
            } else {
                result.put("code", -97);
                result.put("message", "操作失败");
            }
        } else {
            result.put("code", -98);
            result.put("message", "操作失败");
        }
        return result;
    }

    @Override
    public Map<String, Object> getDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord) {
        Map<String, Object> result = new HashMap<>();
        if ((dnsDomainNameRecord.getId() != null) && (dnsDomainNameRecord.getDomainNameId() != null)) {
            DnsDomainName dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainNameRecord.getDomainNameId());
            if ((dnsDomainName != null) && (dnsDomainName.getUserId().longValue() == SecurityUtils.getUserId().longValue()) && (dnsDomainName.getDomainNameStatus().contentEquals("0"))) {
                result.put("code", 0);
                result.put("message", "操作成功");
                result.put("data", dnsDomainNameRecordMapper.selectDnsDomainNameRecordById(dnsDomainNameRecord.getId()));
            } else {
                result.put("code", -1);
                result.put("message", "操作失败");
            }
        } else {
            result.put("code", -1);
            result.put("message", "操作失败");
        }
        return result;
    }

    @Transactional
    @Override
    public Map<String, Object> insertDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord) {
        Map<String, Object> result = new HashMap<>();
        if (dnsDomainNameRecord.getDomainNameId() != null) {//对应域名不为空
            DnsDomainName dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainNameRecord.getDomainNameId());
            if ((dnsDomainName != null) && (dnsDomainName.getUserId().longValue() == SecurityUtils.getUserId().longValue()) && (dnsDomainName.getDomainNameStatus().contentEquals("0"))) {
                dnsDomainNameRecord.setRecordName(((StringUtils.isEmpty(dnsDomainNameRecord.getRecordName()) || dnsDomainNameRecord.getRecordName().contentEquals("@")) ? dnsDomainName.getDomainName() : (dnsDomainNameRecord.getRecordName() + "." + dnsDomainName.getDomainName())));
                dnsDomainNameRecord.setRecordName(dnsDomainNameUtils.nameToPunycode(dnsDomainNameRecord.getRecordName()));
                if (StringUtils.isEmpty(dnsDomainNameRecord.getRecordName())) {
                    result.put("code", -1);
                    result.put("message", "主机记录错误");
                } else if (StringUtils.isEmpty(dnsDomainNameRecord.getRecordGeo()) || (DictUtils.getDictLabel("geo_code", dnsDomainNameRecord.getRecordGeo()) == null)) {
                    result.put("code", -2);
                    result.put("message", "地理位置错误");
                } else if ((dnsDomainNameRecord.getRecordTtl() == null) || (dnsDomainNameRecord.getRecordTtl() < 0) || (dnsDomainNameRecord.getRecordTtl() > 86400)) {
                    result.put("code", -3);
                    result.put("message", "TTL错误");
                } else {
                    try {
                        Name recordName = new Name(dnsDomainNameRecord.getRecordName());
                        Name domainName = new Name(dnsDomainName.getDomainName());
                        List<DnsDomainNameRecord> existDnsDomainNameRecordList = dnsDomainNameRecordMapper.selectDnsDomainNameRecordByRecordName(dnsDomainNameRecord.getRecordName());
                        switch (dnsDomainNameRecord.getRecordType()) {
                            case Type.A: {
                                InetAddress address = dnsDomainNameUtils.getIpv4Address(String.valueOf(dnsDomainNameRecord.getParams().get("ipv4")));
                                if (address != null) {
                                    //Name name, int dclass, long ttl, InetAddress address
                                    ARecord aRecord = new ARecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), address);
                                    boolean existCnameRecord = false;
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existCnameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.A) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(aRecord.rdataToString()))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existNsRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -4);
                                            result.put("message", "该条A记录已存在");
                                        } else if (existCnameRecord) {
                                            result.put("code", -5);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(aRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -6);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -7);
                                            result.put("message", "该条A记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -8);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -9);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(aRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -10);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                } else {
                                    result.put("code", -11);
                                    result.put("message", "ipv4地址格式错误");
                                }
                                break;
                            }
                            case Type.NS: {
                                String nsName = String.valueOf(dnsDomainNameRecord.getParams().get("nsName"));
                                //储存punycode后的域名
                                nsName = dnsDomainNameUtils.nameToPunycode(nsName);
                                if (StringUtils.isEmpty(nsName)) {
                                    result.put("code", -12);
                                    result.put("message", "NS记录格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (nsName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        //Name name, int dclass, long ttl, Name target
                                        Name targetName = new Name(nsName);
                                        NSRecord nsRecord = new NSRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), targetName);
                                        boolean existSameRecord = false;
                                        boolean existOtherRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(nsRecord.rdataToString()))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() != Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existOtherRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -13);
                                                result.put("message", "该条NS记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(nsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(nsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -14);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -15);
                                                result.put("message", "该条NS记录已存在");
                                            } else if (existOtherRecord) {
                                                result.put("code", -16);
                                                result.put("message", "与已存在的其他记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(nsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(nsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -17);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -18);
                                        result.put("message", "NS记录域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.CNAME: {

                                String cnameName = String.valueOf(dnsDomainNameRecord.getParams().get("cnameName"));
                                //储存punycode后的域名
                                cnameName = dnsDomainNameUtils.nameToPunycode(cnameName);
                                if (StringUtils.isEmpty(cnameName)) {
                                    result.put("code", -19);
                                    result.put("message", "CNAME记录格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (cnameName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        //name, Type.CNAME, dclass, ttl, alias
                                        Name targetName = new Name(cnameName);
                                        CNAMERecord cnameRecord = new CNAMERecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), targetName);
                                        boolean existSameRecord = false;
                                        boolean existNotNsOrSoaOrMxOrTxtOtherRecord = false;
                                        boolean existOtherRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(cnameRecord.rdataToString()))) {
                                                existSameRecord = true;
                                            }
                                            if (((domainNameRecord.getRecordType() != Type.NS) && (domainNameRecord.getRecordType() != Type.SOA) && (domainNameRecord.getRecordType() != Type.MX) && (domainNameRecord.getRecordType() != Type.TXT)) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existNotNsOrSoaOrMxOrTxtOtherRecord = true;
                                            }
                                            if((domainNameRecord.getRecordType() != Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existOtherRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -20);
                                                result.put("message", "该条CNAME记录已存在");
                                            } else if (existNotNsOrSoaOrMxOrTxtOtherRecord) {
                                                result.put("code", -21);
                                                result.put("message", "与已存在的其他记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(cnameRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(cnameRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -22);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -23);
                                                result.put("message", "该条CNAME记录已存在");
                                            } else if (existOtherRecord) {
                                                result.put("code", -24);
                                                result.put("message", "与已存在的其他记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(cnameRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(cnameRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -25);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -26);
                                        result.put("message", "CNAME记录域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.MX: {
                                String mxName = String.valueOf(dnsDomainNameRecord.getParams().get("mxName"));
                                //储存punycode后的域名
                                mxName = dnsDomainNameUtils.nameToPunycode(mxName);
                                int priority;
                                try {
                                    priority = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("priority")));
                                } catch (Exception exception) {
                                    priority = -1;
                                }

                                if ((priority < 0) || (priority > 65535)) {
                                    result.put("code", -27);
                                    result.put("message", "优先级错误");
                                } else if (StringUtils.isEmpty(mxName)) {
                                    result.put("code", -28);
                                    result.put("message", "MX记录格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (mxName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        Name targetName = new Name(mxName);
                                        MXRecord mxRecord = new MXRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), priority, targetName);
                                        boolean existSameRecord = false;
                                        boolean existNsRecord = false;
                                        boolean existCnameRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.MX) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(mxRecord.rdataToString()))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existNsRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existCnameRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -29);
                                                result.put("message", "该条MX记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(mxRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(mxRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -30);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -31);
                                                result.put("message", "该条MX记录已存在");
                                            } else if (existNsRecord) {
                                                result.put("code", -32);
                                                result.put("message", "与已存在的NS记录冲突");
                                            } else if (existCnameRecord) {
                                                result.put("code", -33);
                                                result.put("message", "与已存在的CNAME记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(mxRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(mxRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -34);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -35);
                                        result.put("message", "MX记录邮件服务器域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.TXT: {
                                String txtContent = String.valueOf(dnsDomainNameRecord.getParams().get("txtContent"));
                                if (StringUtils.isEmpty(txtContent)) {
                                    result.put("code", -36);
                                    result.put("message", "TXT记录格式错误");
                                } else {
                                    //Name name, int dclass, long ttl, String string
                                    TXTRecord txtRecord = new TXTRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), txtContent);
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    boolean existCnameRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.TXT) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(txtRecord.rdataToString()))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existNsRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existCnameRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -37);
                                            result.put("message", "该条TXT记录已存在");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(txtRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(txtRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -38);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -39);
                                            result.put("message", "该条TXT记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -40);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -41);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(txtRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(txtRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -42);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                            case Type.AAAA: {
                                InetAddress address = dnsDomainNameUtils.getIpv6Address(String.valueOf(dnsDomainNameRecord.getParams().get("ipv6")));
                                if (address != null) {
                                    //Name name, int dclass, long ttl, InetAddress address
                                    AAAARecord aaaaRecord = new AAAARecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), address);
                                    boolean existCnameRecord = false;
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existCnameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.AAAA) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(aaaaRecord.rdataToString()))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existNsRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -43);
                                            result.put("message", "该条AAAA记录已存在");
                                        } else if (existCnameRecord) {
                                            result.put("code", -44);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(aaaaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aaaaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -45);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -46);
                                            result.put("message", "该条AAAA记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -47);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -48);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(aaaaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(aaaaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -49);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                } else {
                                    result.put("code", -50);
                                    result.put("message", "ipv6地址错误");
                                }
                                break;
                            }
                            case Type.SRV: {
                                String srvName = String.valueOf(dnsDomainNameRecord.getParams().get("srvName"));
                                //储存punycode后的域名
                                srvName = dnsDomainNameUtils.nameToPunycode(srvName);
                                int priority;
                                int weight;
                                int port;
                                try {
                                    priority = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("priority")));
                                } catch (Exception exception) {
                                    priority = -1;
                                }
                                try {
                                    weight = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("weight")));
                                } catch (Exception exception) {
                                    weight = -1;
                                }
                                try {
                                    port = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("port")));
                                } catch (Exception exception) {
                                    port = -1;
                                }

                                if ((priority < 0) || (priority > 65535)) {
                                    result.put("code", -51);
                                    result.put("message", "优先级错误");
                                } else if ((weight < 0) || (weight > 65535)) {
                                    result.put("code", -52);
                                    result.put("message", "权重错误");
                                } else if ((port < 0) || (port > 65535)) {
                                    result.put("code", -53);
                                    result.put("message", "端口错误");
                                } else if (StringUtils.isEmpty(srvName)) {
                                    result.put("code", -54);
                                    result.put("message", "目标地址格式错误");
                                } else {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (srvName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        Name targetName = new Name(srvName);
                                        //Name name, int dclass, long ttl, int priority, int weight, int port, Name target
                                        SRVRecord srvRecord = new SRVRecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), priority, weight, port, targetName);
                                        boolean existSameRecord = false;
                                        boolean existNsRecord = false;
                                        boolean existCnameRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.SRV) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(srvRecord.rdataToString()))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existNsRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existCnameRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -55);
                                                result.put("message", "该条SRV记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(srvRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(srvRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -56);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -57);
                                                result.put("message", "该条SRV记录已存在");
                                            } else if (existNsRecord) {
                                                result.put("code", -58);
                                                result.put("message", "与已存在的NS记录冲突");
                                            } else if (existCnameRecord) {
                                                result.put("code", -59);
                                                result.put("message", "与已存在的CNAME记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(srvRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(srvRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -60);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -61);
                                        result.put("message", "SRV目标服务器后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.HTTPS: {
                                String httpsName = String.valueOf(dnsDomainNameRecord.getParams().get("httpsName"));
                                //储存punycode后的域名
                                httpsName = dnsDomainNameUtils.nameToPunycode(httpsName);
                                List<HTTPSRecord.ParameterBase> params = new ArrayList<>();
                                int priority;
                                try {
                                    priority = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("priority")));
                                } catch (Exception exception) {
                                    priority = -1;
                                }
                                try {
                                    String alpnString = String.valueOf(dnsDomainNameRecord.getParams().get("alpn"));
                                    if (StringUtils.isNotEmpty(alpnString)) {
                                        HTTPSRecord.ParameterMandatory mandatory = new HTTPSRecord.ParameterMandatory();
                                        mandatory.fromString("alpn");
                                        HTTPSRecord.ParameterAlpn alpn = new HTTPSRecord.ParameterAlpn();
                                        alpn.fromString(alpnString);
                                        params.add(mandatory);
                                        params.add(alpn);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -62);
                                    result.put("message", "alpn格式错误");
                                }
                                try {
                                    String ipv4hintString = String.valueOf(dnsDomainNameRecord.getParams().get("ipv4hint"));
                                    if (StringUtils.isNotEmpty(ipv4hintString)) {
                                        HTTPSRecord.ParameterIpv4Hint ipv4hint = new HTTPSRecord.ParameterIpv4Hint();
                                        ipv4hint.fromString(ipv4hintString);
                                        params.add(ipv4hint);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -63);
                                    result.put("message", "ipv4hint格式错误");
                                }
                                try {
                                    String ipv6hintString = String.valueOf(dnsDomainNameRecord.getParams().get("ipv6hint"));
                                    if (StringUtils.isNotEmpty(ipv6hintString)) {
                                        HTTPSRecord.ParameterIpv6Hint ipv6hint = new HTTPSRecord.ParameterIpv6Hint();
                                        ipv6hint.fromString(ipv6hintString);
                                        params.add(ipv6hint);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -64);
                                    result.put("message", "ipv6hint格式错误");
                                }
                                try {
                                    String portString = String.valueOf(dnsDomainNameRecord.getParams().get("port"));
                                    if (StringUtils.isNotEmpty(portString)) {
                                        HTTPSRecord.ParameterPort port = new SVCBBase.ParameterPort();
                                        port.fromString(portString);
                                        params.add(port);
                                    }
                                } catch (Exception exception) {
                                    result.put("code", -65);
                                    result.put("message", "port格式错误");
                                }

                                if ((priority < 0) || (priority > 65535)) {
                                    result.put("code", -66);
                                    result.put("message", "优先级错误");
                                } else if (result.isEmpty()) {
                                    //获取系统中允许的域名后缀
                                    List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
                                    //默认添加的域名为不合法后缀
                                    boolean includeDomainExtension = false;
                                    //当前添加域名的后缀
                                    String thisDomainExtension = "";
                                    //循环对比系统中的后缀
                                    for (SysDictData sysDictData : domainExtension) {
                                        //如果当前域名后缀是系统中的合法后缀
                                        if (httpsName.endsWith(sysDictData.getDictValue())) {
                                            //设置当前域名后缀，以最长后缀为准，比如.cn.和.net.cn.，认为后缀是.net.cn.
                                            thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                                            //设置为系统合法后缀
                                            includeDomainExtension = true;
                                        }
                                    }
                                    if (includeDomainExtension) {
                                        Name targetName = new Name(httpsName);
                                        HTTPSRecord httpsRecord = new HTTPSRecord(recordName, DClass.IN, 300, priority, targetName, params);
                                        boolean existSameRecord = false;
                                        boolean existNsRecord = false;
                                        boolean existCnameRecord = false;
                                        for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                            if ((domainNameRecord.getRecordType() == Type.HTTPS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(httpsRecord.rdataToString()))) {
                                                existSameRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existNsRecord = true;
                                            }
                                            if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                                existCnameRecord = true;
                                            }
                                        }
                                        if (recordName.equals(domainName)) {//如果是根域记录
                                            if (existSameRecord) {
                                                result.put("code", -67);
                                                result.put("message", "该条HTTPS记录已存在");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(httpsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(httpsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -68);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        } else {
                                            if (existSameRecord) {
                                                result.put("code", -69);
                                                result.put("message", "该条HTTPS记录已存在");
                                            } else if (existNsRecord) {
                                                result.put("code", -70);
                                                result.put("message", "与已存在的NS记录冲突");
                                            } else if (existCnameRecord) {
                                                result.put("code", -71);
                                                result.put("message", "与已存在的CNAME记录冲突");
                                            } else {
                                                Date now = DateUtils.getNowDate();
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setRecordValue(httpsRecord.rdataToString());
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(httpsRecord.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
                                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                                //更新区域
                                                try {
                                                    dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                    result.put("code", 0);
                                                    result.put("message", "操作成功");
                                                } catch (Exception exception) {
                                                    TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                    result.put("code", -72);
                                                    result.put("message", "未知异常");
                                                }
                                            }
                                        }
                                    } else {
                                        result.put("code", -73);
                                        result.put("message", "HTTPS目标域名后缀不支持");
                                    }
                                }
                                break;
                            }
                            case Type.CAA: {
                                int flag;
                                try {
                                    flag = Integer.parseInt(String.valueOf(dnsDomainNameRecord.getParams().get("flag")));
                                } catch (Exception exception) {
                                    flag = -1;
                                }
                                String tag = String.valueOf(dnsDomainNameRecord.getParams().get("tag"));
                                String value = String.valueOf(dnsDomainNameRecord.getParams().get("value"));
                                if ((flag < 0) || (flag > 255)) {
                                    result.put("code", -74);
                                    result.put("message", "flag格式错误");
                                } else if (StringUtils.isEmpty(tag) || (!tag.contentEquals("issue") && !tag.contentEquals("issuewild") && !tag.contentEquals("iodef"))) {
                                    result.put("code", -75);
                                    result.put("message", "tag格式错误");
                                } else if (StringUtils.isEmpty(value)) {
                                    result.put("code", -76);
                                    result.put("message", "value不能为空");
                                } else if ((tag.contentEquals("iodef") && (dnsDomainNameUtils.emailToPunycode(value) == null))) {
                                    result.put("code", -77);
                                    result.put("message", "value邮箱格式错误");
                                } else if ((!tag.contentEquals("iodef") && (dnsDomainNameUtils.nameToPunycode(value) == null))) {
                                    result.put("code", -78);
                                    result.put("message", "value域名格式错误");
                                } else {
                                    //Name name, int dclass, long ttl, int flags, String tag, String value
                                    CAARecord caaRecord = new CAARecord(recordName, DClass.IN, dnsDomainNameRecord.getRecordTtl(), flag, tag, value);
                                    boolean existSameRecord = false;
                                    boolean existNsRecord = false;
                                    boolean existCnameRecord = false;
                                    for (DnsDomainNameRecord domainNameRecord : existDnsDomainNameRecordList) {
                                        if ((domainNameRecord.getRecordType() == Type.CAA) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo())) && (domainNameRecord.getRecordValue().contentEquals(caaRecord.rdataToString()))) {
                                            existSameRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.NS) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existNsRecord = true;
                                        }
                                        if ((domainNameRecord.getRecordType() == Type.CNAME) && (dnsDomainNameRecord.getRecordGeo().contentEquals(dnsDomainNameRecord.getRecordGeo()))) {
                                            existCnameRecord = true;
                                        }
                                    }
                                    if (recordName.equals(domainName)) {//如果是根域记录
                                        if (existSameRecord) {
                                            result.put("code", -79);
                                            result.put("message", "该条CAA记录已存在");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(caaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(caaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -80);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    } else {
                                        if (existSameRecord) {
                                            result.put("code", -81);
                                            result.put("message", "该条CAA记录已存在");
                                        } else if (existNsRecord) {
                                            result.put("code", -82);
                                            result.put("message", "与已存在的NS记录冲突");
                                        } else if (existCnameRecord) {
                                            result.put("code", -83);
                                            result.put("message", "与已存在的CNAME记录冲突");
                                        } else {
                                            Date now = DateUtils.getNowDate();
                                            dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                            dnsDomainNameRecord.setRecordValue(caaRecord.rdataToString());
                                            dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(caaRecord.toWire(Section.ANSWER)));
                                            dnsDomainNameRecord.setCreateTime(now);
                                            dnsDomainNameRecord.setUpdateTime(now);
                                            dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                                            //更新区域
                                            try {
                                                dnsDomainNameUtils.transformZone(dnsDomainName, "");
                                                result.put("code", 0);
                                                result.put("message", "操作成功");
                                            } catch (Exception exception) {
                                                TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
                                                result.put("code", -84);
                                                result.put("message", "未知异常");
                                            }
                                        }
                                    }
                                }
                                break;
                            }
                            default: {
                                result.put("code", -85);
                                result.put("message", "记录类型错误");
                            }
                        }
                    } catch (TextParseException e) {
                        result.put("code", -86);
                        result.put("message", "未知错误");
                    }

                }
            } else {
                result.put("code", -87);
                result.put("message", "操作失败");
            }
        } else {
            result.put("code", -88);
            result.put("message", "操作失败");
        }
        return result;
    }

    /**
     * 获取域名记录
     *
     * @param dnsDomainNameRecord 域名
     * @return 域名记录
     */
    @Override
    public List<DnsDomainNameRecord> selectDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord) {
        if (dnsDomainNameRecord.getDomainNameId() == null) {
            return new ArrayList<>();
        } else {
            DnsDomainName dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainNameRecord.getDomainNameId());
            if ((dnsDomainName != null) && (dnsDomainName.getUserId().longValue() == SecurityUtils.getUserId().longValue())) {
                PageUtils.startPage();
                return dnsDomainNameRecordMapper.selectDnsDomainNameRecordList(dnsDomainNameRecord);
            } else {
                return new ArrayList<>();
            }
        }
    }

    /**
     *获取域名首页统计数量信息
     **/
    @Override
    public Map<String, Integer> selectDnsDomainNameStatisticsCountByUserId(Long userId) {
        return dnsDomainNameMapper.selectDnsDomainNameStatisticsCountByUserId(userId);
    }


    /**
     *查询域名解析量统计
     **/
    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatistics(DnsDomainName dnsDomainName) {
        //创建结果集合
        Map<String, Object> result = new HashMap<>();
        //获取参数中的查询时间窗口
        String intervalType = String.valueOf(dnsDomainName.getParams().get("intervalType"));
        //获取参数中的查询时间
        long timeStamp = Long.parseLong(String.valueOf(dnsDomainName.getParams().get("date")));
        //查询该域名是否存在
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());
        //如果该域名存在，且域名状态正常，且是该用户的域名
        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue()) && StringUtils.isNotEmpty(intervalType)) {
            //查询时间范围的开始时间
            long start;
            //查询时间范围的结束时间
            long end;
            //获取日历对象
            Calendar calendar = Calendar.getInstance();
            //将日历时间设置为参数的查询时间
            calendar.setTime(new Date(timeStamp));
            if (intervalType.contentEquals("DAY")) {//如果时间窗口是DAY（天），就将时间范围的开始和结束时间设置为查询时间的一天所有小时的时间范围，如 yyyy-MM-dd 00:00:00.000 ~ yyyy-MM-dd 23:59:59.999
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
            } else if (intervalType.contentEquals("HOUR")) {//如果时间窗口是HOUR（小时），就将时间范围的开始和结束时间设置为查询时间的某一个小时的所有分钟范围内，如 yyyy-MM-dd HH:00:00.000 ~ yyyy-MM-dd HH:59:59.999
                calendar.set(Calendar.MINUTE, 0);
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                start = calendar.getTimeInMillis();
                calendar.set(Calendar.MINUTE, 59);
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                end = calendar.getTimeInMillis();
            } else if (intervalType.contentEquals("MINUTE")) {//如果时间窗口是MINUTE（分钟），就将时间范围的开始和结束时间设置为查询时间的某一个分钟的所有秒范围内，如 yyyy-MM-dd HH:MM:00.000 ~ yyyy-MM-dd HH:MM:59.999
                calendar.set(Calendar.SECOND, 0);
                calendar.set(Calendar.MILLISECOND, 0);
                start = calendar.getTimeInMillis();
                calendar.set(Calendar.SECOND, 59);
                calendar.set(Calendar.MILLISECOND, 999);
                end = calendar.getTimeInMillis();
            } else {//否则参数非法
                result.put("code", -1);
                result.put("message", "操作失败");
                return result;
            }
            //根据生成的参数查询出该时间窗口范围内的数据
            List<DnsDomainNameTdengineStatistcs> dnsDomainNameTdengineStatistcsList = tdengineDataSource.queryStatistics(dnsDomainName.getDomainName(), start, end, intervalType);
            //创建统计对象
            DnsDomainNameStatistics dnsDomainNameStatistics = new DnsDomainNameStatistics();
            //初始化统计对象的时间线
            dnsDomainNameStatistics.setTimeLine(new ArrayList<>());
            //初始化统计对象的时间的对应的数据
            dnsDomainNameStatistics.setTimeData(new HashMap<>());
            //创建一个当前时间的日历对象
            Calendar nowCalendar = Calendar.getInstance();
            //判断查询的时间是不是今天
            boolean queryDateIsToday = (nowCalendar.get(Calendar.YEAR) == calendar.get(Calendar.YEAR)) && (nowCalendar.get(Calendar.MONTH) == calendar.get(Calendar.MONTH)) && (nowCalendar.get(Calendar.DATE) == calendar.get(Calendar.DATE));
            if (intervalType.contentEquals("DAY")) {//如果查询的时间窗口是DAY（天），说明需要将时间线设置为小时
                calendar.set(Calendar.MINUTE, 0);//将分设为0
                calendar.set(Calendar.SECOND, 0);//将秒设为0
                int hourRange = queryDateIsToday ? nowCalendar.get(Calendar.HOUR_OF_DAY) : 23; //如果查询日期是今天要限制时间线范围结束时间为当前小时，否则就是查询历史数据可以将时间线设置为24小时
                for (int index = 0; index <= hourRange; index++) {//循环设置时间线并添加进统计对象
                    calendar.set(Calendar.HOUR_OF_DAY, index);
                    dnsDomainNameStatistics.getTimeLine().add(DateUtils.parseDateToStr(DateUtils.HH_MM_SS, calendar.getTime()));
                }
                //根据添加的时间线循环填充对应时间线的统计数据
                dnsDomainNameStatistics.getTimeLine().forEach(timeLine -> {
                    boolean timeLineHasData = false;//该时间点是否有数据
                    for (DnsDomainNameTdengineStatistcs dnsDomainNameTdengineStatistcs : dnsDomainNameTdengineStatistcsList) {//循环获取时间点数据
                        //如果改时间点存在统计数据
                        if (DateUtils.parseDateToStr(DateUtils.HH, dnsDomainNameTdengineStatistcs.getQueryTime()).contentEquals(timeLine.split(":")[0])) {
                            //设置该时间点的统计数据对象
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
                    if (!timeLineHasData) {//如果改时间点不存在数据就填充0
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
            } else if (intervalType.contentEquals("HOUR")) {//如上类推
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
            } else if (intervalType.contentEquals("MINUTE")) {//如上类推
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
        } else {//不是该用户的域名
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    /**
     * 查询解析地理位置统计
     * **/
    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryGeo(DnsDomainName dnsDomainName) {
        //创建结果集合
        Map<String, Object> result = new HashMap<>();
        //获取参数中的查询日期
        long timeStamp = Long.parseLong(String.valueOf(dnsDomainName.getParams().get("date")));
        //获取该域名
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());
        //如果该域名存在且状态正常且为该用户的域名
        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {
            //将查询日期转换为天 yyyy-MM-dd
            String queryTime = DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, new Date(timeStamp));
            //创建mongodb查询对象
            Query query = new Query();
            //设置查询时间和查询的域名
            query.addCriteria(Criteria.where("queryTime").is(queryTime).and("queryDomain").is(dnsDomainName.getDomainName()));
            //从mongodb中获取统计信息
            Map<String, Object> geoLogMap = mongoTemplate.findOne(query, Map.class, "dns_geo_log");
            if (geoLogMap == null) {//如果mongodb中没有查到就去redis缓存中查找
                geoLogMap = new HashMap<>();//创建
                List<SysDictData> countryNameCodeDict = DictUtils.getDictCache("country_name_code");//获取国家代码集合
                for (SysDictData countryNameCode : countryNameCodeDict) {//循环国家代码从和缓存中查询统计信息
                    Object geoCount = redisCache.getCacheObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + queryTime + ":" + dnsDomainName.getDomainName() + ":" + countryNameCode.getDictValue());
                    if (geoCount != null) {//如果存在统计信息就储存
                        geoLogMap.put(countryNameCode.getDictValue(), String.valueOf(geoCount));
                    }
                }
                //获取未知区域的查询数量
                Object nullGeoCount = redisCache.getCacheObject(PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + queryTime + ":" + dnsDomainName.getDomainName() + ":null");
                if (nullGeoCount != null) {//如果存在未知区域的查询数量就储存
                    geoLogMap.put("null", String.valueOf(nullGeoCount));
                }
            }
            result.put("code", 0);
            result.put("message", "操作成功");
            result.put("data", geoLogMap);
            return result;
        } else {//否则就操作失败
            result.put("code", -1);
            result.put("message", "操作失败");
            return result;
        }
    }

    /**
     *查询域名名称的查询量统计
     * **/
    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryName(DnsDomainName dnsDomainName) {
        //创建结果集合
        Map<String, Object> result = new HashMap<>();
        //获取查询日期
        long timeStamp = Long.parseLong(String.valueOf(dnsDomainName.getParams().get("date")));
        //获取查询的域名
        dnsDomainName = dnsDomainNameMapper.selectDnsDomainNameById(dnsDomainName.getId());
        //如果查询的域名存在且状态正常且为当前用户的
        if ((dnsDomainName != null) && (dnsDomainName.getDomainNameStatus().contentEquals("0")) && (SecurityUtils.getUserId().longValue() == dnsDomainName.getUserId().longValue())) {
            //设置开始时间
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(new Date(timeStamp));
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long start = calendar.getTimeInMillis();
            //设置结束时间
            calendar.set(Calendar.HOUR_OF_DAY, 23);
            calendar.set(Calendar.MINUTE, 59);
            calendar.set(Calendar.SECOND, 59);
            calendar.set(Calendar.MILLISECOND, 999);
            long end = calendar.getTimeInMillis();
            //查询指定时间范围的域名查询量
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

    /**
     * 查询域名解析类型的解析量
     * **/
    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryType(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        long timeStamp = Long.parseLong(String.valueOf(dnsDomainName.getParams().get("date")));
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


    /**
     * 查询域名对应类型分组的解析量
     * **/
    @Override
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryNameType(DnsDomainName dnsDomainName) {
        Map<String, Object> result = new HashMap<>();
        long timeStamp = Long.parseLong(String.valueOf(dnsDomainName.getParams().get("date")));
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

    /**
     * 验证域名添加
     * **/
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
                                        dnsDomainName.setId(snowFlakeUtils.nextId());
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
                                                dnsDomainNameRecord.setRecordType(record.getType());
                                                //设置记录的地理位置
                                                dnsDomainNameRecord.setRecordGeo("*");
                                                //设置记录的TTL
                                                dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                                //设置记录值
                                                dnsDomainNameRecord.setRecordValue(record.rdataToString());
                                                //设置记录的内容
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(record.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
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
                                        dnsDomainName.setId(snowFlakeUtils.nextId());
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
                                                dnsDomainNameRecord.setRecordType(record.getType());
                                                //设置记录的地理位置
                                                dnsDomainNameRecord.setRecordGeo("*");
                                                //设置记录的TTL
                                                dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                                //设置记录值
                                                dnsDomainNameRecord.setRecordValue(record.rdataToString());
                                                //设置记录的内容
                                                dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(record.toWire(Section.ANSWER)));
                                                dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                                dnsDomainNameRecord.setCreateTime(now);
                                                dnsDomainNameRecord.setUpdateTime(now);
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
                            dnsDomainName.setId(snowFlakeUtils.nextId());
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
                                    dnsDomainNameRecord.setRecordType(record.getType());
                                    //设置记录的地理位置
                                    dnsDomainNameRecord.setRecordGeo("*");
                                    //设置记录的TTL
                                    dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                    //设置记录值
                                    dnsDomainNameRecord.setRecordValue(record.rdataToString());
                                    //设置记录的内容
                                    dnsDomainNameRecord.setRecordContent(Base64.getEncoder().encodeToString(record.toWire(Section.ANSWER)));
                                    dnsDomainNameRecord.setId(snowFlakeUtils.nextId());
                                    dnsDomainNameRecord.setCreateTime(now);
                                    dnsDomainNameRecord.setUpdateTime(now);
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

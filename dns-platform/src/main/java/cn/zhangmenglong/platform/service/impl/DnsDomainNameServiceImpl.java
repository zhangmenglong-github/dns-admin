package cn.zhangmenglong.platform.service.impl;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.*;

import cn.zhangmenglong.common.core.domain.entity.SysDictData;
import cn.zhangmenglong.common.utils.DateUtils;
import cn.zhangmenglong.common.utils.DictUtils;
import cn.zhangmenglong.common.utils.SecurityUtils;
import cn.zhangmenglong.common.utils.StringUtils;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameWithUser;
import cn.zhangmenglong.platform.mapper.DnsDomainNameRecordMapper;
import cn.zhangmenglong.platform.mapper.DnsDomainNameWithUserMapper;
import cn.zhangmenglong.platform.rabbitmq.RabbitMQ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.service.IDnsDomainNameService;
import org.springframework.transaction.annotation.Transactional;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

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
    private DnsDomainNameWithUserMapper dnsDomainNameWithUserMapper;

    @Autowired
    private RabbitMQ rabbitMQ;

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
        return dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);
    }

    /**
     * 新增域名
     * 
     * @param dnsDomainName 域名
     * @return 结果
     */

    @Transactional
    @Override
    public int insertDnsDomainName(DnsDomainName dnsDomainName)
    {
        if (StringUtils.isNotEmpty(dnsDomainName.getDomainName())) {
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().replaceAll("。", "."));
            dnsDomainName.setDomainName(dnsDomainName.getDomainName().endsWith(".") ? dnsDomainName.getDomainName() : dnsDomainName.getDomainName() + ".");
            String[] dnsDomainNameSection = dnsDomainName.getDomainName().split("\\.");
            StringBuilder domainNameBuilder = new StringBuilder();
            try {
                for (String nameSection : dnsDomainNameSection) {
                    domainNameBuilder.append(IDN.toASCII(nameSection)).append(".");
                }
            } catch (Exception exception) {
                return -2;
            }

            dnsDomainName.setDomainName(domainNameBuilder.toString());

            List<SysDictData> domainExtension = DictUtils.getDictCache("domain_extension");
            boolean includeDomainExtension = false;
            String thisDomainExtension = "";
            for (SysDictData sysDictData : domainExtension) {
                if (dnsDomainName.getDomainName().endsWith(sysDictData.getDictValue())) {
                    thisDomainExtension = thisDomainExtension.length() < sysDictData.getDictValue().length() ? sysDictData.getDictValue() : thisDomainExtension;
                    includeDomainExtension = true;
                }
            }
            if (includeDomainExtension) {
                boolean isSubName = dnsDomainName.getDomainName().substring(0, dnsDomainName.getDomainName().length() - thisDomainExtension.length()).contains(".");
                if (isSubName) {

                } else {
                    Date now = DateUtils.getNowDate();
                    DnsDomainName dnsDomainNameExist = dnsDomainNameMapper.selectDnsDomainNameByName(dnsDomainName.getDomainName());
                    if (dnsDomainNameExist == null) {
                        dnsDomainName.setDomainNameDnssec(false);
                        dnsDomainName.setCreateTime(now);
                        dnsDomainName.setUpdateTime(now);
                        dnsDomainNameMapper.insertDnsDomainName(dnsDomainName);
                        List<Record> defaultRecordList = new ArrayList<>();
                        try {
                            defaultRecordList.add(new SOARecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domains@zhangmenglong.cn."), 600, 600, 600, 600, 600));
                            defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns1.zhangmenglong.cn.")));
                            defaultRecordList.add(new NSRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 600, new Name("ns2.zhangmenglong.cn.")));
                            for (Record record : defaultRecordList) {
                                DnsDomainNameRecord dnsDomainNameRecord = new DnsDomainNameRecord();
                                dnsDomainNameRecord.setDomainNameId(dnsDomainName.getId());
                                dnsDomainNameRecord.setRecordName(record.getName().toString());
                                dnsDomainNameRecord.setRecordType(String.valueOf(record.getType()));
                                dnsDomainNameRecord.setRecordTtl(record.getTTL());
                                dnsDomainNameRecord.setRecordContent(record.rdataToString());
                                dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
                            }
                            Map<String, Object> zoneMap = new HashMap<>();
                            Map<String, List<Record>> geoZone = new HashMap<>();
                            zoneMap.put("domain", dnsDomainName.getDomainName());
                            geoZone.put("*", defaultRecordList);
                            zoneMap.put("geoZone", geoZone);
                            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                            ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                            objectOutputStream.writeObject(zoneMap);
                            DnsDomainNameWithUser dnsDomainNameWithUser = new DnsDomainNameWithUser();
                            dnsDomainNameWithUser.setUserId(SecurityUtils.getUserId());
                            dnsDomainNameWithUser.setDomainNameId(dnsDomainName.getId());
                            dnsDomainNameWithUser.setStatus("1");
                            dnsDomainNameWithUserMapper.insertDnsDomainNameWithUser(dnsDomainNameWithUser);
                            rabbitMQ.send(byteArrayOutputStream.toByteArray());
                            return 1;
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }

                    } else {
                        DnsDomainNameWithUser dnsDomainNameWithUser = new DnsDomainNameWithUser();
                        dnsDomainNameWithUser.setUserId(SecurityUtils.getUserId());
                        dnsDomainNameWithUser.setDomainNameId(dnsDomainNameExist.getId());
                        List<DnsDomainNameWithUser> dnsDomainNameWithUserList = dnsDomainNameWithUserMapper.selectDnsDomainNameWithUserList(dnsDomainNameWithUser);
                        if (!dnsDomainNameWithUserList.isEmpty()) {
                            //是我的域名
                            return -2;
                        } else {
                            //不是我的域名就要进行验证

                        }
                    }
                }
            } else {
                return -3;
            }


        } else {
            return -1;
        }
        return -1;
    }

    /**
     * 修改域名
     * 
     * @param dnsDomainName 域名
     * @return 结果
     */
    @Override
    public int updateDnsDomainName(DnsDomainName dnsDomainName)
    {
        dnsDomainName.setUpdateTime(DateUtils.getNowDate());
        return dnsDomainNameMapper.updateDnsDomainName(dnsDomainName);
    }

    /**
     * 批量删除域名
     * 
     * @param ids 需要删除的域名主键
     * @return 结果
     */
    @Override
    public int deleteDnsDomainNameByIds(Long[] ids)
    {
        return dnsDomainNameMapper.deleteDnsDomainNameByIds(ids);
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

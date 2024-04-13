package cn.zhangmenglong.platform.service.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import cn.zhangmenglong.platform.mapper.DnsDomainNameRecordMapper;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
import cn.zhangmenglong.platform.service.IDnsDomainNameRecordService;

/**
 * 域名记录Service业务层处理
 * 
 * @author dns
 * @date 2024-04-13
 */
@Service
public class DnsDomainNameRecordServiceImpl implements IDnsDomainNameRecordService 
{
    @Autowired
    private DnsDomainNameRecordMapper dnsDomainNameRecordMapper;

    /**
     * 查询域名记录
     * 
     * @param id 域名记录主键
     * @return 域名记录
     */
    @Override
    public DnsDomainNameRecord selectDnsDomainNameRecordById(Long id)
    {
        return dnsDomainNameRecordMapper.selectDnsDomainNameRecordById(id);
    }

    /**
     * 查询域名记录列表
     * 
     * @param dnsDomainNameRecord 域名记录
     * @return 域名记录
     */
    @Override
    public List<DnsDomainNameRecord> selectDnsDomainNameRecordList(DnsDomainNameRecord dnsDomainNameRecord)
    {
        return dnsDomainNameRecordMapper.selectDnsDomainNameRecordList(dnsDomainNameRecord);
    }

    /**
     * 新增域名记录
     * 
     * @param dnsDomainNameRecord 域名记录
     * @return 结果
     */
    @Override
    public int insertDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord)
    {
        return dnsDomainNameRecordMapper.insertDnsDomainNameRecord(dnsDomainNameRecord);
    }

    /**
     * 修改域名记录
     * 
     * @param dnsDomainNameRecord 域名记录
     * @return 结果
     */
    @Override
    public int updateDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord)
    {
        return dnsDomainNameRecordMapper.updateDnsDomainNameRecord(dnsDomainNameRecord);
    }

    /**
     * 批量删除域名记录
     * 
     * @param ids 需要删除的域名记录主键
     * @return 结果
     */
    @Override
    public int deleteDnsDomainNameRecordByIds(Long[] ids)
    {
        return dnsDomainNameRecordMapper.deleteDnsDomainNameRecordByIds(ids);
    }

    /**
     * 删除域名记录信息
     * 
     * @param id 域名记录主键
     * @return 结果
     */
    @Override
    public int deleteDnsDomainNameRecordById(Long id)
    {
        return dnsDomainNameRecordMapper.deleteDnsDomainNameRecordById(id);
    }
}

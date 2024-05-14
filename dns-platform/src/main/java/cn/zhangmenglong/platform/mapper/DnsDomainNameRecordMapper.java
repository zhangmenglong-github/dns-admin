package cn.zhangmenglong.platform.mapper;

import java.util.List;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;

/**
 * 域名记录Mapper接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface DnsDomainNameRecordMapper 
{
    /**
     * 查询域名记录
     * 
     * @param id 域名记录主键
     * @return 域名记录
     */
    public DnsDomainNameRecord selectDnsDomainNameRecordById(Long id);

    /**
     * 查询域名记录列表
     *
     * @param dnsDomainNameRecord 域名记录
     * @return 域名记录集合
     */
    public List<DnsDomainNameRecord> selectDnsDomainNameRecordByDomainNameId(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 查询域名记录列表
     * 
     * @param dnsDomainNameRecord 域名记录
     * @return 域名记录集合
     */
    public List<DnsDomainNameRecord> selectDnsDomainNameRecordList(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 新增域名记录
     * 
     * @param dnsDomainNameRecord 域名记录
     * @return 结果
     */
    public int insertDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 修改域名记录
     * 
     * @param dnsDomainNameRecord 域名记录
     * @return 结果
     */
    public int updateDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 删除域名记录
     * 
     * @param id 域名记录主键
     * @return 结果
     */
    public int deleteDnsDomainNameRecordById(Long id);

    /**
     * 删除域名记录
     *
     * @param id 域名主键
     * @return 结果
     */
    public int deleteDnsDomainNameRecordByDomainNameId(Long id);

    /**
     * 批量删除域名记录
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDnsDomainNameRecordByIds(Long[] ids);

    /**
     * 批量删除域名记录
     *
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDnsDomainNameRecordByDomainNameIds(Long[] ids);
}

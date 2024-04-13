package cn.zhangmenglong.platform.service;

import java.util.List;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;

/**
 * 域名记录Service接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface IDnsDomainNameRecordService 
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
     * 批量删除域名记录
     * 
     * @param ids 需要删除的域名记录主键集合
     * @return 结果
     */
    public int deleteDnsDomainNameRecordByIds(Long[] ids);

    /**
     * 删除域名记录信息
     * 
     * @param id 域名记录主键
     * @return 结果
     */
    public int deleteDnsDomainNameRecordById(Long id);
}

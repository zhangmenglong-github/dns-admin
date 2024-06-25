package cn.zhangmenglong.platform.service;

import java.util.List;
import java.util.Map;

import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;

/**
 * 域名Service接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface IDnsDomainNameService
{
    /**
     * 批量删除域名记录
     *
     * @param ids 需要删除的域名主键集合
     * @return 结果
     */
    public Map<String, Object> deleteDnsDomainNameRecordByIds(Long[] ids);

    /**
     * 修改域名记录
     *
     * @param dnsDomainNameRecord 域名
     * @return 结果
     */

    public Map<String, Object> updateDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 获取域名记录
     *
     * @param dnsDomainNameRecord 域名
     * @return 结果
     */

    public Map<String, Object> getDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 添加域名记录
     *
     * @param dnsDomainNameRecord 域名
     * @return 结果
     */

    public Map<String, Object> insertDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 获取域名记录
     *
     * @param dnsDomainNameRecord 域名
     * @return 域名记录列表
     */

    public List<DnsDomainNameRecord> selectDnsDomainNameRecord(DnsDomainNameRecord dnsDomainNameRecord);

    /**
     * 获取域名统计数量
     *
     * @param userId 域名
     * @return 统计信息
     */
    public Map<String, Integer> selectDnsDomainNameStatisticsCountByUserId(Long userId);

    /**
     * 查询域名解析统计
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> queryDnsDomainNameResolutionStatistics(DnsDomainName dnsDomainName);

    /**
     * 查询域名解析统计
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryGeo(DnsDomainName dnsDomainName);

    /**
     * 查询域名解析子域统计
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryName(DnsDomainName dnsDomainName);

    /**
     * 查询域名解析类型统计
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryType(DnsDomainName dnsDomainName);

    /**
     * 查询域名子域解析类型统计
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> queryDnsDomainNameResolutionStatisticsQueryNameType(DnsDomainName dnsDomainName);

    /**
     * 刷新域名
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> validateRefreshDnsDomainName(DnsDomainName dnsDomainName);

    /**
     * 验证域名
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public Map<String, Object> validateDnsDomainName(DnsDomainName dnsDomainName);

    /**
     * 查询域名
     * 
     * @param id 域名主键
     * @return 域名
     */
    public DnsDomainName selectDnsDomainNameById(Long id);

    /**
     * 查询域名列表
     * 
     * @param dnsDomainName 域名
     * @return 域名集合
     */
    public List<DnsDomainName> selectDnsDomainNameList(DnsDomainName dnsDomainName);

    /**
     * 新增域名
     * 
     * @param dnsDomainName 域名
     * @return 结果
     */
    public Map<String, Object> insertDnsDomainName(DnsDomainName dnsDomainName);

    /**
     * 修改域名DNSSEC
     * 
     * @param dnsDomainName 域名
     * @return 结果
     */
    public Map<String, Object> updateDnsDomainNameDnssec(DnsDomainName dnsDomainName);

    /**
     * 修改域名
     *
     * @param dnsDomainName 域名
     */
    public void updateDnsDomainName(DnsDomainName dnsDomainName);

    /**
     * 批量删除域名
     * 
     * @param ids 需要删除的域名主键集合
     * @return 结果
     */
    public Map<String, Object> deleteDnsDomainNameByIds(Long[] ids);

    /**
     * 删除域名信息
     * 
     * @param id 域名主键
     * @return 结果
     */
    public int deleteDnsDomainNameById(Long id);
}

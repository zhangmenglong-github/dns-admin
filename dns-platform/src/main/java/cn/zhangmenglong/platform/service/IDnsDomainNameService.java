package cn.zhangmenglong.platform.service;

import java.util.List;
import java.util.Map;

import cn.zhangmenglong.platform.domain.po.DnsDomainName;

/**
 * 域名Service接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface IDnsDomainNameService 
{

    /**
     * 查询域名
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
    public int deleteDnsDomainNameByIds(Long[] ids);

    /**
     * 删除域名信息
     * 
     * @param id 域名主键
     * @return 结果
     */
    public int deleteDnsDomainNameById(Long id);
}

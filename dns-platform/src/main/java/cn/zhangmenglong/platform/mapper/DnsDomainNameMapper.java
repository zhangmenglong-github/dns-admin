package cn.zhangmenglong.platform.mapper;

import java.util.List;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;

/**
 * 域名Mapper接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface DnsDomainNameMapper 
{
    /**
     * 更新域名状态
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public int updateDnsDomainNameStatusByName(DnsDomainName dnsDomainName);

    /**
     * 查询域名
     * 
     * @param id 域名主键
     * @return 域名
     */
    public DnsDomainName selectDnsDomainNameById(Long id);

    /**
     * 查询域名
     *
     * @param dnsDomainName 域名
     * @return 域名
     */
    public DnsDomainName selectDnsDomainNameByNameAndUserId(DnsDomainName dnsDomainName);

    /**
     * 查询域名列表
     *
     * @param dnsDomainName 域名
     * @return 域名集合
     */
    public List<DnsDomainName> selectDnsDomainNameByName(DnsDomainName dnsDomainName);

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
    public int insertDnsDomainName(DnsDomainName dnsDomainName);

    /**
     * 修改域名
     * 
     * @param dnsDomainName 域名
     * @return 结果
     */
    public int updateDnsDomainName(DnsDomainName dnsDomainName);

    /**
     * 删除域名
     * 
     * @param id 域名主键
     * @return 结果
     */
    public int deleteDnsDomainNameById(Long id);

    /**
     * 批量删除域名
     * 
     * @param ids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDnsDomainNameByIds(Long[] ids);
}

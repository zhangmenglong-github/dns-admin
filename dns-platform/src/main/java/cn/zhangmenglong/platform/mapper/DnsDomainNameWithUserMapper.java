package cn.zhangmenglong.platform.mapper;

import cn.zhangmenglong.platform.domain.po.DnsDomainNameWithUser;

import java.util.List;

/**
 * 域名用户关联Mapper接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface DnsDomainNameWithUserMapper 
{
    /**
     * 查询域名用户关联
     * 
     * @param userId 域名用户关联主键
     * @return 域名用户关联
     */
    public DnsDomainNameWithUser selectDnsDomainNameWithUserByUserId(Long userId);

    /**
     * 查询域名用户关联列表
     * 
     * @param dnsDomainNameWithUser 域名用户关联
     * @return 域名用户关联集合
     */
    public List<DnsDomainNameWithUser> selectDnsDomainNameWithUserList(DnsDomainNameWithUser dnsDomainNameWithUser);

    /**
     * 新增域名用户关联
     * 
     * @param dnsDomainNameWithUser 域名用户关联
     * @return 结果
     */
    public int insertDnsDomainNameWithUser(DnsDomainNameWithUser dnsDomainNameWithUser);

    /**
     * 修改域名用户关联
     * 
     * @param dnsDomainNameWithUser 域名用户关联
     * @return 结果
     */
    public int updateDnsDomainNameWithUser(DnsDomainNameWithUser dnsDomainNameWithUser);

    /**
     * 删除域名用户关联
     * 
     * @param userId 域名用户关联主键
     * @return 结果
     */
    public int deleteDnsDomainNameWithUserByUserId(Long userId);

    /**
     * 批量删除域名用户关联
     * 
     * @param userIds 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDnsDomainNameWithUserByUserIds(Long[] userIds);
}

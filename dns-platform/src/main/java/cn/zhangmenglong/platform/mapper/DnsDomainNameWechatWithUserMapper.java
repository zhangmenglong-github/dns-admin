package cn.zhangmenglong.platform.mapper;

import java.util.List;
import cn.zhangmenglong.platform.domain.DnsDomainNameWechatWithUser;

/**
 * 微信关联用户Mapper接口
 * 
 * @author dns
 * @date 2024-04-13
 */
public interface DnsDomainNameWechatWithUserMapper 
{
    /**
     * 查询微信关联用户
     * 
     * @param wechatMpOpenid 微信关联用户主键
     * @return 微信关联用户
     */
    public DnsDomainNameWechatWithUser selectDnsDomainNameWechatWithUserByWechatMpOpenid(String wechatMpOpenid);

    /**
     * 查询微信关联用户
     *
     * @param wechatMpOpenid 微信关联用户主键
     * @return 微信关联用户
     */
    public DnsDomainNameWechatWithUser selectDnsDomainNameWechatWithUserByUserId(String wechatMpOpenid);


    /**
     * 查询微信关联用户列表
     * 
     * @param dnsDomainNameWechatWithUser 微信关联用户
     * @return 微信关联用户集合
     */
    public DnsDomainNameWechatWithUser selectDnsDomainNameWechatWithUser(DnsDomainNameWechatWithUser dnsDomainNameWechatWithUser);

    /**
     * 新增微信关联用户
     * 
     * @param dnsDomainNameWechatWithUser 微信关联用户
     * @return 结果
     */
    public int insertDnsDomainNameWechatWithUser(DnsDomainNameWechatWithUser dnsDomainNameWechatWithUser);

    /**
     * 修改微信关联用户
     * 
     * @param dnsDomainNameWechatWithUser 微信关联用户
     * @return 结果
     */
    public int updateDnsDomainNameWechatWithUser(DnsDomainNameWechatWithUser dnsDomainNameWechatWithUser);

    /**
     * 删除微信关联用户
     * 
     * @param wechatMpOpenid 微信关联用户主键
     * @return 结果
     */
    public int deleteDnsDomainNameWechatWithUserByWechatMpOpenid(String wechatMpOpenid);

    /**
     * 批量删除微信关联用户
     * 
     * @param wechatMpOpenids 需要删除的数据主键集合
     * @return 结果
     */
    public int deleteDnsDomainNameWechatWithUserByWechatMpOpenids(String[] wechatMpOpenids);
}

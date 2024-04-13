package cn.zhangmenglong.platform.domain;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import cn.zhangmenglong.common.annotation.Excel;
import cn.zhangmenglong.common.core.domain.BaseEntity;

/**
 * 微信关联用户对象 dns_domain_name_wechat_with_user
 * 
 * @author dns
 * @date 2024-04-13
 */
public class DnsDomainNameWechatWithUser extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 微信公众号openid */
    @Excel(name = "微信公众号openid")
    private String wechatMpOpenid;

    /** 用户id */
    @Excel(name = "用户id")
    private Long userId;

    public void setWechatMpOpenid(String wechatMpOpenid) 
    {
        this.wechatMpOpenid = wechatMpOpenid;
    }

    public String getWechatMpOpenid() 
    {
        return wechatMpOpenid;
    }
    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("wechatMpOpenid", getWechatMpOpenid())
            .append("userId", getUserId())
            .toString();
    }
}

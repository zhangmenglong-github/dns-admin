package cn.zhangmenglong.platform.domain.po;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import cn.zhangmenglong.common.annotation.Excel;
import cn.zhangmenglong.common.core.domain.BaseEntity;

/**
 * 域名用户关联对象 dns_domain_name_with_user
 * 
 * @author dns
 * @date 2024-04-13
 */
public class DnsDomainNameWithUser extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** 用户id */
    @Excel(name = "用户id")
    private Long userId;

    /** 域名id */
    @Excel(name = "域名id")
    private Long domainNameId;

    /** 状态 */
    @Excel(name = "状态")
    private String status;

    public void setUserId(Long userId) 
    {
        this.userId = userId;
    }

    public Long getUserId() 
    {
        return userId;
    }
    public void setDomainNameId(Long domainNameId) 
    {
        this.domainNameId = domainNameId;
    }

    public Long getDomainNameId() 
    {
        return domainNameId;
    }
    public void setStatus(String status) 
    {
        this.status = status;
    }

    public String getStatus() 
    {
        return status;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("userId", getUserId())
            .append("domainNameId", getDomainNameId())
            .append("status", getStatus())
            .toString();
    }
}

package cn.zhangmenglong.platform.domain.po;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import cn.zhangmenglong.common.annotation.Excel;
import cn.zhangmenglong.common.core.domain.BaseEntity;

/**
 * 域名对象 dns_domain_name
 * 
 * @author dns
 * @date 2024-04-13
 */
public class DnsDomainName extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** id */
    private Long id;

    /** 域名 */
    @Excel(name = "域名")
    private String domainName;

    /** 是否开启dnssec */
    @Excel(name = "是否开启dnssec")
    private Boolean domainNameDnssec;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setDomainName(String domainName) 
    {
        this.domainName = domainName;
    }

    public String getDomainName() 
    {
        return domainName;
    }
    public void setDomainNameDnssec(Boolean domainNameDnssec)
    {
        this.domainNameDnssec = domainNameDnssec;
    }

    public Boolean getDomainNameDnssec()
    {
        return domainNameDnssec;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("domainName", getDomainName())
            .append("domainNameDnssec", getDomainNameDnssec())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .toString();
    }
}

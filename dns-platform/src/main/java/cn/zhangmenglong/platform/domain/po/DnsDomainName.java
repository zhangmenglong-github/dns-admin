package cn.zhangmenglong.platform.domain.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
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

    /** 用户id */
    private Long userId;

    /** 域名 */
    @Excel(name = "域名")
    private String domainName;

    /** 是否开启dnssec */
    @Excel(name = "是否开启dnssec")
    private Boolean domainNameDnssec;

    /** dnssec密钥签名私钥 */
    @JsonIgnore
    @Excel(name = "dnssec密钥签名私钥")
    private String domainNameDnssecKskPrivateKey;

    /** dnssec密钥签名公钥 */
    @JsonIgnore
    @Excel(name = "dnssec密钥签名公钥")
    private String domainNameDnssecKskPublicKey;

    /** dnssec区域签名私钥 */
    @JsonIgnore
    @Excel(name = "dnssec区域签名私钥")
    private String domainNameDnssecZskPrivateKey;

    /** dnssec区域签名公钥 */
    @JsonIgnore
    @Excel(name = "dnssec区域签名公钥")
    private String domainNameDnssecZskPublicKey;

    @Excel(name = "dnssec关键标签")
    private String domainNameDnssecDsKeyTag;

    @Excel(name = "dnssec摘要")
    private String domainNameDnssecDsDigestValue;

    @Excel(name = "域名状态")
    private String domainNameStatus;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getUserId() {
        return userId;
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

    public void setDomainNameDnssecKskPrivateKey(String domainNameDnssecKskPrivateKey) {
        this.domainNameDnssecKskPrivateKey = domainNameDnssecKskPrivateKey;
    }

    public String getDomainNameDnssecKskPrivateKey() {
        return domainNameDnssecKskPrivateKey;
    }

    public void setDomainNameDnssecKskPublicKey(String domainNameDnssecKskPublicKey) {
        this.domainNameDnssecKskPublicKey = domainNameDnssecKskPublicKey;
    }

    public String getDomainNameDnssecKskPublicKey() {
        return domainNameDnssecKskPublicKey;
    }

    public void setDomainNameDnssecZskPrivateKey(String domainNameDnssecZskPrivateKey) {
        this.domainNameDnssecZskPrivateKey = domainNameDnssecZskPrivateKey;
    }

    public String getDomainNameDnssecZskPrivateKey() {
        return domainNameDnssecZskPrivateKey;
    }

    public void setDomainNameDnssecZskPublicKey(String domainNameDnssecZskPublicKey) {
        this.domainNameDnssecZskPublicKey = domainNameDnssecZskPublicKey;
    }

    public String getDomainNameDnssecZskPublicKey() {
        return domainNameDnssecZskPublicKey;
    }

    public void setDomainNameDnssecDsKeyTag(String domainNameDnssecDsKeyTag) {
        this.domainNameDnssecDsKeyTag = domainNameDnssecDsKeyTag;
    }

    public String getDomainNameDnssecDsKeyTag() {
        return domainNameDnssecDsKeyTag;
    }

    public void setDomainNameDnssecDsDigestValue(String domainNameDnssecDsDigestValue) {
        this.domainNameDnssecDsDigestValue = domainNameDnssecDsDigestValue;
    }

    public String getDomainNameDnssecDsDigestValue() {
        return domainNameDnssecDsDigestValue;
    }

    public void setDomainNameStatus(String domainNameStatus) {
        this.domainNameStatus = domainNameStatus;
    }

    public String getDomainNameStatus() {
        return domainNameStatus;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("domainName", getDomainName())
            .append("domainNameDnssec", getDomainNameDnssec())
            .append("createTime", getCreateTime())
            .append("updateTime", getUpdateTime())
            .append("domainNameStatus", getDomainNameStatus())
            .append("domainNameDnssecKskPrivateKey", getDomainNameDnssecKskPrivateKey())
            .append("domainNameDnssecKskPublicKey", getDomainNameDnssecKskPublicKey())
            .append("domainNameDnssecZskPrivateKey", getDomainNameDnssecZskPrivateKey())
            .append("domainNameDnssecZskPublicKey", getDomainNameDnssecZskPublicKey())
            .append("domainNameDnssecDsKeyTag", getDomainNameDnssecDsKeyTag())
            .append("domainNameDnssecDsDigestValue", getDomainNameDnssecDsDigestValue())
            .toString();
    }
}

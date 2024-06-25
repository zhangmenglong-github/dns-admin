package cn.zhangmenglong.platform.domain.po;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;
import cn.zhangmenglong.common.annotation.Excel;
import cn.zhangmenglong.common.core.domain.BaseEntity;

/**
 * 域名记录对象 dns_domain_name_record
 * 
 * @author dns
 * @date 2024-04-13
 */
public class DnsDomainNameRecord extends BaseEntity
{
    private static final long serialVersionUID = 1L;

    /** id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 域名id */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long domainNameId;

    /** 记录名 */
    @Excel(name = "记录名")
    private String recordName;

    /** 记录类型 */
    @Excel(name = "记录类型", dictType = "record_type")
    private Integer recordType;

    /** 记录类型 */
    @Excel(name = "记录地理位置", dictType = "geo_code")
    private String recordGeo;

    /** 记录TTL */
    @Excel(name = "记录TTL")
    private Long recordTtl;

    /** 记录TTL */
    @Excel(name = "记录值")
    private String recordValue;

    /** 记录内容 */
    @JsonIgnore
    private String recordContent;

    public void setId(Long id) 
    {
        this.id = id;
    }

    public Long getId() 
    {
        return id;
    }
    public void setDomainNameId(Long domainNameId) 
    {
        this.domainNameId = domainNameId;
    }

    public Long getDomainNameId() 
    {
        return domainNameId;
    }
    public void setRecordName(String recordName) 
    {
        this.recordName = recordName;
    }

    public String getRecordName() 
    {
        return recordName;
    }
    public void setRecordType(Integer recordType)
    {
        this.recordType = recordType;
    }

    public Integer getRecordType()
    {
        return recordType;
    }

    public void setRecordGeo(String recordGeo) {
        this.recordGeo = recordGeo;
    }

    public String getRecordGeo() {
        return recordGeo;
    }

    public void setRecordTtl(Long recordTtl)
    {
        this.recordTtl = recordTtl;
    }

    public Long getRecordTtl() 
    {
        return recordTtl;
    }

    public void setRecordValue(String recordValue) {
        this.recordValue = recordValue;
    }

    public String getRecordValue() {
        return recordValue;
    }

    public void setRecordContent(String recordContent)
    {
        this.recordContent = recordContent;
    }

    public String getRecordContent() 
    {
        return recordContent;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this,ToStringStyle.MULTI_LINE_STYLE)
            .append("id", getId())
            .append("domainNameId", getDomainNameId())
            .append("recordName", getRecordName())
            .append("recordType", getRecordType())
            .append("recordGeo", getRecordGeo())
            .append("recordTtl", getRecordTtl())
            .append("recordValue", getRecordValue())
            .append("recordContent", getRecordContent())
            .toString();
    }
}

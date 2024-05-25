package cn.zhangmenglong.platform.domain.vo;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class DnsDomainNameTdengineQueryTypeStatistics {

    private String queryType;

    private Long queryCount;

    public void setQueryType(String queryType) {
        this.queryType = queryType;
    }

    public String getQueryType() {
        return queryType;
    }

    public void setQueryCount(Long queryCount) {
        this.queryCount = queryCount;
    }

    public Long getQueryCount() {
        return queryCount;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("queryType", getQueryType())
                .append("queryCount", getQueryCount())
                .toString();
    }

}

package cn.zhangmenglong.platform.domain.vo;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

public class DnsDomainNameTdengineQueryNameStatistics {

    private String queryName;

    private Long queryCount;

    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }

    public String getQueryName() {
        return queryName;
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
                .append("queryName", getQueryName())
                .append("queryCount", getQueryCount())
                .toString();
    }

}

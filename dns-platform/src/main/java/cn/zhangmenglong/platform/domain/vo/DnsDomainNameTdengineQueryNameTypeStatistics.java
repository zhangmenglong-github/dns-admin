package cn.zhangmenglong.platform.domain.vo;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.HashMap;
import java.util.Map;

public class DnsDomainNameTdengineQueryNameTypeStatistics {

    private String queryName;

    private final Map<String, Long> queryTypeCount = new HashMap<>();

    public void setQueryName(String queryName) {
        this.queryName = queryName;
    }

    public String getQueryName() {
        return queryName;
    }

    public void setQueryTypeCount(String type, Long count) {
        queryTypeCount.put(type, count);
    }

    public Map<String, Long> getQueryTypeCount() {
        return queryTypeCount;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("queryName", getQueryName())
                .append("queryTypeCount", getQueryTypeCount())
                .toString();
    }
}

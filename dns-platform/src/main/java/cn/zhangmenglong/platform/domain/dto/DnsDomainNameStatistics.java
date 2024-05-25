package cn.zhangmenglong.platform.domain.dto;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.List;
import java.util.Map;

public class DnsDomainNameStatistics {
    private List<String> timeLine;

    private Map<String, List<Long>> timeData;

    public void setTimeLine(List<String> timeLine) {
        this.timeLine = timeLine;
    }

    public List<String> getTimeLine() {
        return timeLine;
    }

    public void setTimeData(Map<String, List<Long>> timeData) {
        this.timeData = timeData;
    }

    public Map<String, List<Long>> getTimeData() {
        return timeData;
    }

    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("timeLine", getTimeLine())
                .append("timeData", getTimeData())
                .toString();
    }

}

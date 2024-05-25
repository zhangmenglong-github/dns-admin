package cn.zhangmenglong.platform.domain.vo;

import org.apache.commons.lang3.builder.ToStringBuilder;
import org.apache.commons.lang3.builder.ToStringStyle;

import java.util.Date;

public class DnsDomainNameTdengineStatistcs {

    private Date queryTime;

    private Long queryCount;

    private Long udpQueryCount;

    private Long tcpQueryCount;

    private Long dnssecRequestCount;

    private Long noDnssecRequestCount;

    private Long dnssecResponseCount;

    private Long noDnssecResponseCount;

    public void setQueryTime(Date queryTime) {
        this.queryTime = queryTime;
    }

    public Date getQueryTime() {
        return queryTime;
    }

    public void setQueryCount(Long queryCount) {
        this.queryCount = queryCount;
    }

    public Long getQueryCount() {
        return queryCount;
    }

    public void setUdpQueryCount(Long udpQueryCount) {
        this.udpQueryCount = udpQueryCount;
    }

    public Long getUdpQueryCount() {
        return udpQueryCount;
    }

    public void setTcpQueryCount(Long tcpQueryCount) {
        this.tcpQueryCount = tcpQueryCount;
    }

    public Long getTcpQueryCount() {
        return tcpQueryCount;
    }

    public void setDnssecRequestCount(Long dnssecRequestCount) {
        this.dnssecRequestCount = dnssecRequestCount;
    }

    public Long getDnssecRequestCount() {
        return dnssecRequestCount;
    }

    public void setNoDnssecRequestCount(Long noDnssecRequestCount) {
        this.noDnssecRequestCount = noDnssecRequestCount;
    }

    public Long getNoDnssecRequestCount() {
        return noDnssecRequestCount;
    }

    public void setDnssecResponseCount(Long dnssecResponseCount) {
        this.dnssecResponseCount = dnssecResponseCount;
    }

    public Long getDnssecResponseCount() {
        return dnssecResponseCount;
    }

    public void setNoDnssecResponseCount(Long noDnssecResponseCount) {
        this.noDnssecResponseCount = noDnssecResponseCount;
    }

    public Long getNoDnssecResponseCount() {
        return noDnssecResponseCount;
    }


    @Override
    public String toString() {
        return new ToStringBuilder(this, ToStringStyle.MULTI_LINE_STYLE)
                .append("queryTime", getQueryTime())
                .append("queryCount", getQueryCount())
                .append("udpQueryCount", getUdpQueryCount())
                .append("tcpQueryCount", getTcpQueryCount())
                .append("dnssecRequestCount", getDnssecRequestCount())
                .append("noDnssecRequestCount", getNoDnssecRequestCount())
                .append("dnssecResponseCount", getDnssecResponseCount())
                .append("noDnssecResponseCount", getNoDnssecResponseCount())
                .toString();
    }


}

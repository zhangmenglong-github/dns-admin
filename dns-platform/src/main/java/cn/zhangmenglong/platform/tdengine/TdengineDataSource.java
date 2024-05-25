package cn.zhangmenglong.platform.tdengine;

import cn.zhangmenglong.common.utils.StringUtils;
import cn.zhangmenglong.common.utils.sign.Md5Utils;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineQueryNameStatistics;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineQueryNameTypeStatistics;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineQueryTypeStatistics;
import cn.zhangmenglong.platform.domain.vo.DnsDomainNameTdengineStatistcs;
import cn.zhangmenglong.platform.utils.SnowFlakeUtils;
import com.taosdata.jdbc.TSDBPreparedStatement;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

@Component
public class TdengineDataSource {

    @Autowired
    private SnowFlakeUtils snowFlakeUtils;

    private HikariDataSource hikariDataSource;


    public TdengineDataSource(@Value("${tdengine.url}") String url, @Value("${tdengine.username}") String username, @Value("${tdengine.password}") String password, @Value("${tdengine.datasource.minimumIdle}") Integer minimumIdle, @Value("${tdengine.datasource.maximumPoolSize}") Integer maximumPoolSize, @Value("${tdengine.datasource.connectionTimeout}") Integer connectionTimeout, @Value("${tdengine.datasource.maxLifetime}") Integer maxLifetime, @Value("${tdengine.datasource.idleTimeout}") Integer idleTimeout, @Value("${tdengine.datasource.connectionTestQuery}") String connectionTestQuery) throws SQLException {
        HikariConfig hikariConfig = new HikariConfig();
        // jdbc properties
        hikariConfig.setJdbcUrl(url);
        hikariConfig.setUsername(username);
        hikariConfig.setPassword(password);
        // connection pool configurations
        hikariConfig.setMinimumIdle(minimumIdle);           //minimum number of idle connection
        hikariConfig.setMaximumPoolSize(maximumPoolSize);      //maximum number of connection in the pool
        hikariConfig.setConnectionTimeout(connectionTimeout); //maximum wait milliseconds for get connection from pool
        hikariConfig.setMaxLifetime(maxLifetime);       // maximum life time for each connection
        hikariConfig.setIdleTimeout(idleTimeout);       // max idle time for recycle idle connection
        hikariConfig.setConnectionTestQuery(connectionTestQuery); //validation query
        hikariDataSource = new HikariDataSource(hikariConfig); //create datasource
        Connection connection = hikariDataSource.getConnection(); // get connection
        Statement statement = connection.createStatement(); // get statement
        // create table
        statement.executeUpdate("CREATE STABLE IF NOT EXISTS domain_name_resolution_log (queryTime TIMESTAMP, queryId BIGINT PRIMARY KEY, queryName BINARY(256), queryType TINYINT, ednsIp BINARY(64), clientIp BINARY(64), isUdp BOOL, requestDnssec BOOL, responseDnssec BOOL, dnsMessage VARBINARY(2048)) TAGS (queryDomain BINARY(256))");
        statement.close();
        connection.close(); // put back to connection pool
    }

    public void insert(Map<String, Object> dnsQueryLog) throws SQLException {
        String sql = "INSERT INTO ? USING domain_name_resolution_log TAGS(?) VALUES(?,?,?,?,?,?,?,?,?,?)";
        try (Connection connection = hikariDataSource.getConnection(); TSDBPreparedStatement tsdbPreparedStatement = connection.prepareStatement(sql).unwrap(TSDBPreparedStatement.class)) {
            tsdbPreparedStatement.setTableName("domain_" + Md5Utils.hash((String) dnsQueryLog.get("queryDomain")));
            tsdbPreparedStatement.setTagString(0, (String) dnsQueryLog.get("queryDomain"));
            tsdbPreparedStatement.setTimestamp(0, new ArrayList<>(Collections.singletonList((Long) dnsQueryLog.get("queryTime"))));
            tsdbPreparedStatement.setLong(1, new ArrayList<>(Collections.singletonList(snowFlakeUtils.nextId())));
            tsdbPreparedStatement.setString(2, new ArrayList<>(Collections.singletonList((String) dnsQueryLog.get("queryName"))), 256);
            tsdbPreparedStatement.setByte(3, new ArrayList<>(Collections.singletonList(Byte.parseByte(((Integer) dnsQueryLog.get("queryType")).toString()))));
            tsdbPreparedStatement.setString(4, new ArrayList<>(Collections.singletonList((String) dnsQueryLog.get("ednsIp"))), 64);
            tsdbPreparedStatement.setString(5, new ArrayList<>(Collections.singletonList((String) dnsQueryLog.get("clientIp"))), 64);
            tsdbPreparedStatement.setBoolean(6, new ArrayList<>(Collections.singletonList(((Boolean) dnsQueryLog.get("isUdp")) ? true : null)));
            tsdbPreparedStatement.setBoolean(7, new ArrayList<>(Collections.singletonList(((Boolean) dnsQueryLog.get("requestDnssec")) ? true : null)));
            tsdbPreparedStatement.setBoolean(8, new ArrayList<>(Collections.singletonList(((Boolean) dnsQueryLog.get("responseDnssec")) ? true : null)));
            tsdbPreparedStatement.setVarbinary(9, new ArrayList<>(Collections.singletonList((byte[]) dnsQueryLog.get("dnsMessage"))), 2048);
            tsdbPreparedStatement.columnDataAddBatch();
            tsdbPreparedStatement.columnDataExecuteBatch();
        }
    }

    public List<DnsDomainNameTdengineStatistcs> queryStatistics(String domainName, Long start, Long end, String intervalType) {
        String sql = "";
        if (intervalType.contentEquals("DAY")) {
            sql = "SELECT COUNT(1) AS queryCount, COUNT(isUdp) AS udpQueryCount, COUNT(requestDnssec) AS dnssecRequestCount, COUNT(responseDnssec) AS dnssecResponseCount, FIRST(queryTime) AS queryTime FROM ? WHERE queryTime >= ? and queryTime <= ? INTERVAL(1H)";
        } else if (intervalType.contentEquals("HOUR")) {
            sql = "SELECT COUNT(1) AS queryCount, COUNT(isUdp) AS udpQueryCount, COUNT(requestDnssec) AS dnssecRequestCount, COUNT(responseDnssec) AS dnssecResponseCount, FIRST(queryTime) AS queryTime FROM ? WHERE queryTime >= ? and queryTime <= ? INTERVAL(1M)";
        } else if (intervalType.contentEquals("MINUTE")) {
            sql = "SELECT COUNT(1) AS queryCount, COUNT(isUdp) AS udpQueryCount, COUNT(requestDnssec) AS dnssecRequestCount, COUNT(responseDnssec) AS dnssecResponseCount, FIRST(queryTime) AS queryTime FROM ? WHERE queryTime >= ? and queryTime <= ? INTERVAL(1S)";
        }
        List<DnsDomainNameTdengineStatistcs> result = new ArrayList<>();
        try (Connection connection = hikariDataSource.getConnection(); TSDBPreparedStatement tsdbPreparedStatement = connection.prepareStatement(sql).unwrap(TSDBPreparedStatement.class)) {
            tsdbPreparedStatement.setString(1, "domain_" + Md5Utils.hash(domainName));
            tsdbPreparedStatement.setLong(2, start);
            tsdbPreparedStatement.setLong(3, end);
            ResultSet resultSet = tsdbPreparedStatement.executeQuery();
            while (resultSet.next()) {
                long queryCount = resultSet.getLong("queryCount");
                long udpQueryCount = resultSet.getLong("udpQueryCount");
                long tcpQueryCount = queryCount - udpQueryCount;
                long dnssecRequestCount = resultSet.getLong("dnssecRequestCount");
                long noDnssecRequestCount = queryCount - dnssecRequestCount;
                long dnssecResponseCount = resultSet.getLong("dnssecResponseCount");
                long noDnssecResponseCount = queryCount - dnssecResponseCount;
                Date queryTime = resultSet.getDate("queryTime");
                DnsDomainNameTdengineStatistcs dnsDomainNameTdengineStatistcs = new DnsDomainNameTdengineStatistcs();
                dnsDomainNameTdengineStatistcs.setQueryTime(queryTime);
                dnsDomainNameTdengineStatistcs.setQueryCount(queryCount);
                dnsDomainNameTdengineStatistcs.setUdpQueryCount(udpQueryCount);
                dnsDomainNameTdengineStatistcs.setTcpQueryCount(tcpQueryCount);
                dnsDomainNameTdengineStatistcs.setDnssecRequestCount(dnssecRequestCount);
                dnsDomainNameTdengineStatistcs.setNoDnssecRequestCount(noDnssecRequestCount);
                dnsDomainNameTdengineStatistcs.setDnssecResponseCount(dnssecResponseCount);
                dnsDomainNameTdengineStatistcs.setNoDnssecResponseCount(noDnssecResponseCount);
                result.add(dnsDomainNameTdengineStatistcs);
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    public List<DnsDomainNameTdengineQueryNameStatistics> queryNameStatistics(String domainName, Long start, Long end) {
        String sql = "SELECT COUNT(queryName) AS queryNameCount,  FIRST(queryName) AS queryName FROM ? WHERE queryTime >= ? and queryTime <= ? GROUP BY queryName";
        List<DnsDomainNameTdengineQueryNameStatistics> result = new ArrayList<>();
        try (Connection connection = hikariDataSource.getConnection(); TSDBPreparedStatement tsdbPreparedStatement = connection.prepareStatement(sql).unwrap(TSDBPreparedStatement.class)) {
            tsdbPreparedStatement.setString(1, "domain_" + Md5Utils.hash(domainName));
            tsdbPreparedStatement.setLong(2, start);
            tsdbPreparedStatement.setLong(3, end);
            ResultSet resultSet = tsdbPreparedStatement.executeQuery();
            while (resultSet.next()) {

                String queryName = resultSet.getString("queryName");
                if (StringUtils.isNotEmpty(queryName)) {
                    Long queryCount = resultSet.getLong("queryNameCount");
                    DnsDomainNameTdengineQueryNameStatistics dnsDomainNameTdengineStatistcs = new DnsDomainNameTdengineQueryNameStatistics();
                    dnsDomainNameTdengineStatistcs.setQueryName(queryName);
                    dnsDomainNameTdengineStatistcs.setQueryCount(queryCount);
                    result.add(dnsDomainNameTdengineStatistcs);
                }
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    public List<DnsDomainNameTdengineQueryTypeStatistics> queryTypeStatistics(String domainName, Long start, Long end) {
        String sql = "SELECT COUNT(queryType) AS queryTypeCount,  FIRST(queryType) AS queryType FROM ? WHERE queryTime >= ? and queryTime <= ? GROUP BY queryType";
        List<DnsDomainNameTdengineQueryTypeStatistics> result = new ArrayList<>();
        try (Connection connection = hikariDataSource.getConnection(); TSDBPreparedStatement tsdbPreparedStatement = connection.prepareStatement(sql).unwrap(TSDBPreparedStatement.class)) {
            tsdbPreparedStatement.setString(1, "domain_" + Md5Utils.hash(domainName));
            tsdbPreparedStatement.setLong(2, start);
            tsdbPreparedStatement.setLong(3, end);
            ResultSet resultSet = tsdbPreparedStatement.executeQuery();
            while (resultSet.next()) {
                String queryType = resultSet.getString("queryType");
                if (StringUtils.isNotEmpty(queryType)) {
                    Long queryCount = resultSet.getLong("queryTypeCount");
                    DnsDomainNameTdengineQueryTypeStatistics dnsDomainNameTdengineQueryTypeStatistics = new DnsDomainNameTdengineQueryTypeStatistics();
                    dnsDomainNameTdengineQueryTypeStatistics.setQueryType(queryType);
                    dnsDomainNameTdengineQueryTypeStatistics.setQueryCount(queryCount);
                    result.add(dnsDomainNameTdengineQueryTypeStatistics);
                }
            }
        } catch (Exception ignored) {
            return result;
        }
        return result;
    }

    public Map<String, DnsDomainNameTdengineQueryNameTypeStatistics> queryNameTypeStatistics(String domainName, Long start, Long end) {
        String sql = "SELECT FIRST(queryName) AS queryName, COUNT(queryType) AS queryTypeCount, FIRST(queryType) AS queryType FROM ? WHERE queryTime >= ? and queryTime <= ? GROUP BY queryName, queryType";
        Map<String, DnsDomainNameTdengineQueryNameTypeStatistics> result = new HashMap<>();
        try (Connection connection = hikariDataSource.getConnection(); TSDBPreparedStatement tsdbPreparedStatement = connection.prepareStatement(sql).unwrap(TSDBPreparedStatement.class)) {
            tsdbPreparedStatement.setString(1, "domain_" + Md5Utils.hash(domainName));
            tsdbPreparedStatement.setLong(2, start);
            tsdbPreparedStatement.setLong(3, end);
            ResultSet resultSet = tsdbPreparedStatement.executeQuery();
            while (resultSet.next()) {
                String queryName = resultSet.getString("queryName");
                if (StringUtils.isNotEmpty(queryName)) {
                    String queryType = resultSet.getString("queryType");
                    Long queryTypeCount = resultSet.getLong("queryTypeCount");
                    DnsDomainNameTdengineQueryNameTypeStatistics dnsDomainNameTdengineQueryNameTypeStatistics = result.get(queryName);
                    dnsDomainNameTdengineQueryNameTypeStatistics = (dnsDomainNameTdengineQueryNameTypeStatistics == null) ? new DnsDomainNameTdengineQueryNameTypeStatistics() : dnsDomainNameTdengineQueryNameTypeStatistics;
                    dnsDomainNameTdengineQueryNameTypeStatistics.setQueryName(queryName);
                    dnsDomainNameTdengineQueryNameTypeStatistics.getQueryTypeCount().put(queryType, queryTypeCount);
                    result.put(queryName, dnsDomainNameTdengineQueryNameTypeStatistics);
                }
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
            return result;
        }
        return result;
    }
}

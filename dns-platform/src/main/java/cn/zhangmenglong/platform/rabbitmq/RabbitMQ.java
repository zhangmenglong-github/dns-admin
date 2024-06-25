package cn.zhangmenglong.platform.rabbitmq;

import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.DateUtils;
import cn.zhangmenglong.platform.constant.PlatformDomainNameConstants;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.tdengine.TdengineDataSource;
import cn.zhangmenglong.platform.utils.DnsDomainNameUtils;
import cn.zhangmenglong.platform.utils.IpGeoUtils;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.*;
import java.util.*;
import java.util.concurrent.TimeUnit;


@Component
public class RabbitMQ {

    @Autowired
    private DnsDomainNameUtils dnsDomainNameUtils;

    @Autowired
    private DnsDomainNameMapper dnsDomainNameMapper;

    @Autowired
    private TdengineDataSource tdengineDataSource;

    // 更新队列交换机
    @Value("${rabbitmq.mq-server-authoritative-update-exchange}")
    private String updateExchangeName;
    private Channel channel;

    @Autowired
    private IpGeoUtils ipGeoUtils;

    @Autowired
    private RedisCache redisCache;

    public RabbitMQ(@Value("${rabbitmq.host}") String host, @Value("${rabbitmq.port}") Integer port, @Value("${rabbitmq.username}") String username, @Value("${rabbitmq.password}") String password, @Value("${rabbitmq.mq-server-admin-init-queue}") String initQueueName, @Value("${rabbitmq.mq-server-authoritative-update-exchange}") String updateExchangeName, @Value("${rabbitmq.mq-server-authoritative-statistics-queue}") String statisticsQueue) {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(host);
        connectionFactory.setPort(port);
        connectionFactory.setUsername(username);
        connectionFactory.setPassword(password);
        final ExceptionHandler exceptionHandler = new DefaultExceptionHandler() {
            @Override
            public void handleConsumerException(Channel channel, Throwable exception, Consumer consumer, String consumerTag, String methodName) {}
        };
        connectionFactory.setExceptionHandler(exceptionHandler);
        try {
            Connection connection = connectionFactory.newConnection();
            channel = connection.createChannel();
            channel.queueDeclare(initQueueName, true, false, false, null);
            channel.queueDeclare(statisticsQueue, true, false, false, null);
            channel.exchangeDeclare(updateExchangeName, BuiltinExchangeType.FANOUT, true, false, null);
            Consumer initConsumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    try {
                        DnsDomainName dnsDomainName = new DnsDomainName();
                        dnsDomainName.setDomainNameStatus("0");

                        List<DnsDomainName> dnsDomainNameList = dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);

                        dnsDomainNameList.forEach(dnsDomainNameTemp -> {
                            try {
                                dnsDomainNameUtils.transformZone(dnsDomainNameTemp, new String(body));
                            } catch (Exception ignored) {}
                        });
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    } catch (Exception ignored){
                        channel.basicNack(envelope.getDeliveryTag(), false, true);
                    }

                }
            };

            Consumer statisticsConsumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    Map<String, Object> dnsQueryLog;
                    try {
                        dnsQueryLog = (Map<String, Object>) objectInputStream.readObject();
                        tdengineDataSource.insert(dnsQueryLog);
                        String ipAddress = (String) dnsQueryLog.get("ednsIp");
                        ipAddress = (ipAddress == null) ? (String) dnsQueryLog.get("clientIp") : ipAddress;
                        String countryCode = ipGeoUtils.getCountry(ipAddress);
                        String key = PlatformDomainNameConstants.RESOLUTION_GEO_STATISTICS_COUNT + DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, DateUtils.getNowDate()) + ":" + dnsQueryLog.get("queryDomain") + ":" + countryCode;
                        redisCache.redisTemplate.opsForValue().increment(key);
                        redisCache.expire(key, 2, TimeUnit.DAYS);
                        channel.basicAck(envelope.getDeliveryTag(), false);
                    } catch (Exception ignored) {
                        channel.basicNack(envelope.getDeliveryTag(), false, true);
                    }
                }
            };
            channel.basicConsume(initQueueName, false, initConsumer);
            channel.basicConsume(statisticsQueue, false, statisticsConsumer);
        } catch (Exception ignored) {}
    }

    public void send(byte[] body, String queue) {
        try {
            channel.basicPublish(updateExchangeName, queue, null, body);
        } catch (Exception exception) {
            throw new RuntimeException(exception);
        }
    }


}

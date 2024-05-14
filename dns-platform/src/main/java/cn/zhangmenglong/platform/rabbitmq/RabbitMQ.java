package cn.zhangmenglong.platform.rabbitmq;

import cn.zhangmenglong.common.utils.DateUtils;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.utils.DnsDomainNameUtils;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.*;
import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.TimeoutException;


@Component
public class RabbitMQ {

    @Autowired
    private DnsDomainNameUtils dnsDomainNameUtils;

    @Autowired
    private DnsDomainNameMapper dnsDomainNameMapper;

    @Autowired
    private MongoTemplate mongoTemplate;

    // 更新队列交换机
    @Value("${rabbitmq.mq-server-authoritative-update-exchange}")
    private String updateExchangeName;
    private Channel channel;

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

                    DnsDomainName dnsDomainName = new DnsDomainName();
                    dnsDomainName.setDomainNameStatus("0");

                    List<DnsDomainName> dnsDomainNameList = dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);

                    dnsDomainNameList.forEach(dnsDomainNameTemp -> {
                        try {
                            dnsDomainNameUtils.transformZone(dnsDomainNameTemp, new String(body));
                        } catch (Exception ignored) {}
                    });
                }
            };

            Consumer statisticsConsumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    ByteArrayInputStream inputStream = new ByteArrayInputStream(body);
                    ObjectInputStream objectInputStream = new ObjectInputStream(inputStream);
                    try {
                        Map<String, Object> queryStatistics = (Map<String, Object>) objectInputStream.readObject();
                        mongoTemplate.insert(queryStatistics, DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, new Date(Long.parseLong(String.valueOf(queryStatistics.get("queryTime"))))));
                    } catch (ClassNotFoundException e) {}
                }
            };
            channel.basicConsume(initQueueName, true, initConsumer);
            channel.basicConsume(statisticsQueue, true, statisticsConsumer);
        } catch (IOException | TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(byte[] body, String queue) {
        try {
            channel.basicPublish(updateExchangeName, queue, null, body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}

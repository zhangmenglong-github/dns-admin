package cn.zhangmenglong.platform.rabbitmq;

import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.mapper.DnsDomainNameMapper;
import cn.zhangmenglong.platform.utils.DnsDomainNameUtils;
import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.*;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;


@Component
public class RabbitMQ {

    @Autowired
    private DnsDomainNameUtils dnsDomainNameUtils;

    @Autowired
    private DnsDomainNameMapper dnsDomainNameMapper;

    // 更新队列交换机
    @Value("${rabbitmq.mq-server-authoritative-update-exchange}")
    private String updateExchangeName;
    private Channel channel;

    public RabbitMQ(@Value("${rabbitmq.host}") String host, @Value("${rabbitmq.port}") Integer port, @Value("${rabbitmq.username}") String username, @Value("${rabbitmq.password}") String password, @Value("${rabbitmq.mq-server-authoritative-init-queue}") String initQueueName, @Value("${rabbitmq.mq-server-authoritative-init-exchange}") String initExchangeName, @Value("${rabbitmq.mq-server-authoritative-update-exchange}") String updateExchangeName) {
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
            channel.queueDeclare(initQueueName, true, false, true, null);
            channel.exchangeDeclare(initExchangeName, BuiltinExchangeType.FANOUT, true, false, null);
            channel.exchangeDeclare(updateExchangeName, BuiltinExchangeType.FANOUT, true, false, null);
            channel.queueBind(initQueueName, initExchangeName, "");
            Consumer consumer = new DefaultConsumer(channel) {
                @Override
                public void handleDelivery(String consumerTag, Envelope envelope, AMQP.BasicProperties properties, byte[] body) throws IOException {
                    DnsDomainName dnsDomainName = new DnsDomainName();
                    dnsDomainName.setDomainNameStatus("0");

                    List<DnsDomainName> dnsDomainNameList = dnsDomainNameMapper.selectDnsDomainNameList(dnsDomainName);

                    dnsDomainNameList.forEach(dnsDomainNameTemp -> {
                        try {
                            dnsDomainNameUtils.transformZone(dnsDomainNameTemp);
                        } catch (Exception ignored) {}
                    });
                }
            };
            channel.basicConsume(initQueueName, true, consumer);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } catch (TimeoutException e) {
            throw new RuntimeException(e);
        }
    }

    public void send(byte[] body) {
        try {
            channel.basicPublish(updateExchangeName, "", null, body);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }


}

package cn.zhangmenglong.platform.rabbitmq;

import com.rabbitmq.client.*;
import com.rabbitmq.client.impl.DefaultExceptionHandler;
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
                    List<Record> recordList = new LinkedList<>();

                    try {
                        //Name name, int dclass, long ttl, Name host, Name admin, long serial, long refresh, long retry, long expire, long minimum
                        recordList.add(new SOARecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domain@zhangmenglong.cn."), 600, 600, 600, 600, 600));
                        //Name name, int dclass, long ttl, Name next, int[] types
                        recordList.add(new NSECRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("zhangmenglong.cn."), new int[]{Type.SOA}));

                        //Name name, int dclass, long ttl, Name target
                        recordList.add(new NSRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.zhangmenglong.cn.")));
                        recordList.add(new NSRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns2.zhangmenglong.cn.")));

                        //Name name, int dclass, long ttl, InetAddress address
                        recordList.add(new ARecord(new Name("*.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.70")));
                        recordList.add(new ARecord(new Name("ss.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.71")));

                        //Name name, int dclass, long ttl, Name target
                        recordList.add(new NSRecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.dns.zhangmenglong.cn.")));
                        recordList.add(new NSRecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns2.dns.zhangmenglong.cn.")));

                        //Name name, int dclass, long ttl, InetAddress address
                        recordList.add(new ARecord(new Name("ns1.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("1.1.1.1")));
                        recordList.add(new ARecord(new Name("ns2.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("2.2.2.2")));

                        //Name name, int dclass, long ttl, InetAddress address
                        recordList.add(new AAAARecord(new Name("ns1.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("::1")));
                        recordList.add(new AAAARecord(new Name("ns2.dns.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("::2")));

                        //Name name, int dclass, long ttl, Name alias
                        recordList.add(new CNAMERecord(new Name("cname.zhangmenglong.cn."), DClass.IN, 600, new Name("1.cname.cn.")));
                        recordList.add(new CNAMERecord(new Name("cname.zhangmenglong.cn."), DClass.IN, 600, new Name("2.cname.cn.")));





//
//            recordList.add(new CNAMERecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns3.dns.cn.")));
//            recordList.add(new CNAMERecord(new Name("dns.zhangmenglong.cn."), DClass.IN, 600, new Name("ns4.dns.cn.")));

                        Map<String, Object> zoneMap = new HashMap<>();
                        zoneMap.put("domain", recordList.get(0).getName().toString());
                        Map<String, List<Record>> geoZone = new HashMap<>();
                        geoZone.put("*", recordList);
                        recordList = new LinkedList<>();
                        recordList.add(new ARecord(new Name("ss.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.72")));
                        geoZone.put("CN", recordList);
                        zoneMap.put("geoZone", geoZone);

                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                        objectOutputStream.writeObject(zoneMap);

                        channel.basicPublish("", new String(body), null, byteArrayOutputStream.toByteArray());

                    } catch (TextParseException e) {
                        throw new RuntimeException(e);
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
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

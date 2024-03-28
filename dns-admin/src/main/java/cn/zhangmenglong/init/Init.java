package cn.zhangmenglong.init;


import cn.zhangmenglong.rabbitmq.RabbitMQ;
import com.alibaba.fastjson2.JSON;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import javax.annotation.PostConstruct;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.*;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

import static java.security.SecureRandom.getInstanceStrong;

@Component
public class Init {

    @Autowired
    private RabbitMQ rabbitMQ;

    private void dnssecSign(List<Record> recordList, PrivateKey kskPrivateKey, DNSKEYRecord kskDnskeyRecord, PrivateKey zskPrivateKey, DNSKEYRecord zskDnskeyRecord) throws DNSSEC.DNSSECException {

        Map<String, List<Record>> RRsetMap = new HashMap<>();

        recordList.forEach(record -> {
            List<Record> records = RRsetMap.get(record.getName().toString() + "&" + record.getType());
            records = (records == null) ? new LinkedList<>() : records;
            records.add(record);
            RRsetMap.put(record.getName().toString() + "&" + record.getType(), records);
        });

        List<RRset> rRsets = new LinkedList<>();

        RRsetMap.keySet().forEach(key -> {
            List<Record> records = RRsetMap.get(key);
            RRset rRset = new RRset();
            records.forEach(record -> {
                rRset.addRR(record);
            });
            rRsets.add(rRset);
        });

        Instant now = Instant.now();

        for (RRset rRset : rRsets) {
            if (rRset.getType() == Type.DNSKEY) {
                recordList.add(DNSSEC.sign(rRset, kskDnskeyRecord, kskPrivateKey, now, now.plusSeconds(2592000)));
            } else {
                recordList.add(DNSSEC.sign(rRset, zskDnskeyRecord, zskPrivateKey, now, now.plusSeconds(2592000)));
            }

        }
    }

    @PostConstruct
    public void init() {
        List<org.xbill.DNS.Record> recordList = new LinkedList<>();

        try {
            //Name name, int dclass, long ttl, Name host, Name admin, long serial, long refresh, long retry, long expire, long minimum
            recordList.add(new SOARecord(new Name("zhangmenglong.cn."), DClass.IN, 600, new Name("ns1.zhangmenglong.cn."), new Name("domain@zhangmenglong.cn."), 600, 600, 600, 600, 600));

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

            try {
                // 初始化KeyPairGenerator
                KeyPairGenerator keyGen = KeyPairGenerator.getInstance("EC");

                // 设置ECDSA的P-256曲线
                keyGen.initialize(256, new SecureRandom());

                // 生成密钥对
                KeyPair kskPair = keyGen.generateKeyPair();

                // 私钥
                byte[] kskPrivateKeyOrigin = kskPair.getPrivate().getEncoded();

                // 公钥
                byte[] kskPublicKeyOrigin = kskPair.getPublic().getEncoded();


                String kskPrivateKeyString = Base64.getEncoder().encodeToString(kskPrivateKeyOrigin);

                String kskPublicKeyString = Base64.getEncoder().encodeToString(kskPublicKeyOrigin);

                // 生成密钥对
                KeyPair zskPair = keyGen.generateKeyPair();

                // 私钥
                byte[] zskPrivateKey = zskPair.getPrivate().getEncoded();

                // 公钥
                byte[] zskPublicKey = zskPair.getPublic().getEncoded();


                String zskPrivateKeyString = Base64.getEncoder().encodeToString(zskPrivateKey);

                String zskPublicKeyString = Base64.getEncoder().encodeToString(zskPublicKey);




                KeyFactory keyFactory = KeyFactory.getInstance("EC");
                PrivateKey kskPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(kskPrivateKeyString)));
                PublicKey kskPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(kskPublicKeyString)));

                DNSKEYRecord kskDnskeyRecord = new DNSKEYRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, DNSKEYRecord.Flags.SEP_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, kskPublicKey);
                DNSKEYRecord zskDnskeyRecord = new DNSKEYRecord(new Name("zhangmenglong.cn."), DClass.IN, 600, DNSKEYRecord.Flags.ZONE_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, zskPair.getPublic());

                recordList.add(kskDnskeyRecord);
                recordList.add(zskDnskeyRecord);

                dnssecSign(recordList, kskPrivateKey, kskDnskeyRecord, zskPair.getPrivate(), zskDnskeyRecord);

                geoZone.put("*", recordList);








                recordList = new LinkedList<>();
                recordList.add(new ARecord(new Name("ss.zhangmenglong.cn."), DClass.IN, 600, InetAddress.getByName("120.78.160.72")));
                geoZone.put("CN", recordList);
                zoneMap.put("geoZone", geoZone);



                dnssecSign(recordList, kskPrivateKey, kskDnskeyRecord, zskPair.getPrivate(), zskDnskeyRecord);


                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
                objectOutputStream.writeObject(zoneMap);

                rabbitMQ.send(byteArrayOutputStream.toByteArray());
                System.out.println(geoZone);

            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            } catch (InvalidKeySpecException e) {
                throw new RuntimeException(e);
            } catch (DNSSEC.DNSSECException e) {
                throw new RuntimeException(e);
            }

//            String[] signTime = ZoneCache.query(Constants.DNSSEC_SIGN_TIME).split("&");
//            String[] keyPair = dnssec.split("&");
//            byte[] zskPublicKeyBytes = Base64.getDecoder().decode(keyPair[2]);
//            byte[] zskPrivateKeyBytes = Base64.getDecoder().decode(keyPair[3]);
//            try {
//                KeyFactory keyFactory = KeyFactory.getInstance("EC");
//                PublicKey zskPublicKey = keyFactory.generatePublic(new X509EncodedKeySpec(zskPublicKeyBytes));
//                PrivateKey zskPrivateKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(zskPrivateKeyBytes));
//                DNSKEYRecord zskRecord = new DNSKEYRecord(Name.fromString(domainName + "."), DClass.IN, Constants.DNSSEC_TTL, DNSKEYRecord.Flags.ZONE_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, zskPublicKey);
//                RRset rRset = new RRset();
//                switch (queryResultType) {
//                    case Type.A: {
//                        for (RecordRoundRobin recordRoundRobin : recordRoundRobinList) {
//                            ARecord aRecord = new ARecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), InetAddress.getByName(recordRoundRobin.getContent()));
//                            message.addRecord(aRecord, Section.ANSWER);
//                            rRset.addRR(aRecord);
//                        }
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ANSWER);
//                        break;
//                    }
//                    case Type.CNAME: {
//                        for (RecordRoundRobin recordRoundRobin : recordRoundRobinList) {
//                            CNAMERecord cnameRecord = new CNAMERecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), Name.fromString(recordRoundRobin.getContent() + "."));
//                            message.addRecord(cnameRecord, Section.ANSWER);
//                            rRset.addRR(cnameRecord);
//                        }
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ANSWER);
//                        break;
//                    }
//                    case Type.AAAA: {
//                        for (RecordRoundRobin recordRoundRobin : recordRoundRobinList) {
//                            AAAARecord aaaaRecord = new AAAARecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), InetAddress.getByName(recordRoundRobin.getContent()));
//                            message.addRecord(aaaaRecord, Section.ANSWER);
//                            rRset.addRR(aaaaRecord);
//                        }
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ANSWER);
//                        break;
//                    }
//                    case Type.NS: {
//                        int resultSection = queryType == Type.NS ? Section.ANSWER : Section.AUTHORITY;
//                        queryRecord.clear();
//                        for (RecordRoundRobin recordRoundRobin : recordRoundRobinList) {
//                            for (String geo : geoList) {
//                                queryRecord.add(recordPrefix + recordRoundRobin.getContent() + ":" + Type.A + ":" + geo);
//                                queryRecord.add(recordPrefix + recordRoundRobin.getContent() + ":" + Type.AAAA + ":" + geo);
//                            }
//                            NSRecord nsRecord = new NSRecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), Name.fromString(recordRoundRobin.getContent() + "."));
//                            message.addRecord(nsRecord, resultSection);
//                            rRset.addRR(nsRecord);
//                        }
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), resultSection);
//                        for (String record : queryRecord) {
//                            queryResult = ZoneCache.query(record);
//                            if (queryResult != null) {
//                                queryResultSection = StringUtils.split(queryResult, "#");
//                                String queryResultRecordName = queryResultSection[0] + ".";
//                                queryResultType = Integer.parseInt(queryResultSection[1]);
//                                queryResultSection = StringUtils.split(queryResultSection[2], "&");
//                                recordRoundRobinList.clear();
//                                for (String recordContent : queryResultSection) {
//                                    queryResultSection = StringUtils.split(recordContent, "_");
//                                    recordRoundRobinList.add(new RecordRoundRobin(Long.parseLong(queryResultSection[0]), Integer.parseInt(queryResultSection[1]), queryResultSection[2]));
//                                }
//                                recordRoundRobinList = recordRoundRobinList.stream().sorted(Comparator.comparing(RecordRoundRobin::getRandomPriority)).collect(Collectors.toList());
//                                if (queryResultType == Type.A) {
//                                    rRset.clear();
//                                    for (RecordRoundRobin recordRoundRobin : recordRoundRobinList) {
//                                        ARecord aRecord = new ARecord(Name.fromString(queryResultRecordName), DClass.IN, recordRoundRobin.getTtl(), InetAddress.getByName(recordRoundRobin.getContent()));
//                                        message.addRecord(aRecord, Section.ADDITIONAL);
//                                        rRset.addRR(aRecord);
//                                    }
//                                    message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ADDITIONAL);
//                                } else {
//                                    rRset.clear();
//                                    for (RecordRoundRobin recordRoundRobin : recordRoundRobinList) {
//                                        AAAARecord aaaaRecord = new AAAARecord(Name.fromString(queryResultRecordName), DClass.IN, recordRoundRobin.getTtl(), InetAddress.getByName(recordRoundRobin.getContent()));
//                                        message.addRecord(aaaaRecord, Section.ADDITIONAL);
//                                        rRset.addRR(aaaaRecord);
//                                    }
//                                    message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ADDITIONAL);
//                                }
//                            }
//                        }
//                        break;
//                    }
//                    case Type.MX: {
//                        recordRoundRobinList.forEach(recordRoundRobin -> {
//                            try {
//                                MXRecord mxRecord = new MXRecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), recordRoundRobin.getPriority(), Name.fromString(recordRoundRobin.getContent() + "."));
//                                message.addRecord(mxRecord, Section.ANSWER);
//                                rRset.addRR(mxRecord);
//                            } catch (TextParseException ignored) {}
//                        });
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ANSWER);
//                        break;
//                    }
//                    case Type.TXT: {
//                        recordRoundRobinList.forEach(recordRoundRobin -> {
//                            TXTRecord txtRecord = new TXTRecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), new String(Base64.getDecoder().decode(recordRoundRobin.getContent().getBytes())));
//                            message.addRecord(txtRecord, Section.ANSWER);
//                            rRset.addRR(txtRecord);
//                        });
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ANSWER);
//                        break;
//                    }
//                    case Type.CAA: {
//                        recordRoundRobinList.forEach(recordRoundRobin -> {
//                            String[] caaSection = StringUtils.split(recordRoundRobin.getContent(), " ");
//                            CAARecord caaRecord = new CAARecord(message.getQuestion().getName(), DClass.IN, recordRoundRobin.getTtl(), Integer.parseInt(caaSection[0]), caaSection[1], caaSection[2]);
//                            message.addRecord(caaRecord, Section.ANSWER);
//                            rRset.addRR(caaRecord);
//                        });
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.ANSWER);
//                        break;
//                    }
//                    case Type.SOA: {
//                        recordRoundRobinList.forEach(recordRoundRobin -> {
//                            try {
//                                String[] soaSection = StringUtils.split(recordRoundRobin.getContent(), " ");
//                                SOARecord soaRecord = new SOARecord(Name.fromString(domainName + "."), DClass.IN, recordRoundRobin.getTtl(), Name.fromString(soaSection[0] + "."), Name.fromString(soaSection[1] + "."), Long.parseLong(soaSection[2]), Long.parseLong(soaSection[3]), Long.parseLong(soaSection[4]), Long.parseLong(soaSection[5]), Long.parseLong(soaSection[6]));
//                                message.addRecord(soaRecord, Section.AUTHORITY);
//                                rRset.addRR(soaRecord);
//                            } catch (TextParseException ignored) {}
//                        });
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.AUTHORITY);
//                        rRset.clear();
//                        Name name = Name.fromString(domainName + ".");
//                        NSECRecord nsecRecord = new NSECRecord(name, DClass.IN, Constants.DNSSEC_TTL, name, Constants.DNSSEC_TYPES);
//                        rRset.addRR(nsecRecord);
//                        message.addRecord(nsecRecord, Section.AUTHORITY);
//                        message.addRecord(DNSSEC.sign(rRset, zskRecord, zskPrivateKey, Instant.parse(signTime[0]), Instant.parse(signTime[1])), Section.AUTHORITY);
//                        break;
//                    }
//                }
//            } catch (NoSuchAlgorithmException | InvalidKeySpecException | TextParseException | DNSSEC.DNSSECException | UnknownHostException ignored) {}



        } catch (TextParseException e) {
            throw new RuntimeException(e);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}

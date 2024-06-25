package cn.zhangmenglong.platform.utils;

import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
import cn.zhangmenglong.platform.mapper.DnsDomainNameRecordMapper;
import cn.zhangmenglong.platform.rabbitmq.RabbitMQ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.*;
import org.xbill.DNS.Record;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.IDN;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;
import java.util.regex.Pattern;

@Component
public class DnsDomainNameUtils {

    @Autowired
    private RabbitMQ rabbitMQ;

    @Autowired
    private DnsDomainNameRecordMapper dnsDomainNameRecordMapper;

    private final Pattern ipv4Pattern = Pattern.compile("^((1?[1-9]?\\d|[1-2][0-4]\\d|25[0-5])\\.){3}(1?[1-9]?\\d|[1-2][0-4]\\d|25[0-5])$");

    private final Pattern ipv6Pattern = Pattern.compile("^\\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}))|:)))(%.+)?\\s*$");

    private final Pattern emailPattern = Pattern.compile("^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$");

    public InetAddress getIpv4Address(String ipv4) {
        try {
            return ipv4Pattern.matcher(ipv4).find() ? InetAddress.getByName(ipv4) : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public InetAddress getIpv6Address(String ipv6) {
        try {
            return ipv6Pattern.matcher(ipv6).find() ? InetAddress.getByName(ipv6) : null;
        } catch (UnknownHostException e) {
            return null;
        }
    }

    public String emailToPunycode(String email) {
        //将域名中的中文。替换为.
        email = email.replaceAll("。", ".");
        //将域名分割为不同段落
        String[] emailSection = email.split("\\.");
        //储存punycode后的域名
        StringBuilder emailBuilder = new StringBuilder();
        try {
            //循环拼接转换后的域名
            for (String nameSection : emailSection) {
                emailBuilder.append(IDN.toASCII(nameSection)).append(".");
            }
            emailBuilder.deleteCharAt(emailBuilder.length() - 1);
            //如果转换后的邮箱不符合格式就抛出异常
            if (!emailPattern.matcher(emailBuilder.toString()).find()) {
                return null;
            }
        } catch (Exception exception) {
            return null;
        }
        return emailBuilder.toString().toLowerCase();
    }

    public String nameToPunycode(String domainName) {
        //将域名中的中文。替换为.
        domainName = domainName.replaceAll("。", ".");
        //将域名结尾替换为.
        domainName = domainName.endsWith(".") ? domainName : domainName + ".";
        //将域名分割为不同段落
        String[] dnsDomainNameSection = domainName.split("\\.");
        //储存punycode后的域名
        StringBuilder domainNameBuilder = new StringBuilder();
        try {
            //循环拼接转换后的域名
            for (String nameSection : dnsDomainNameSection) {
                domainNameBuilder.append(IDN.toASCII(nameSection)).append(".");
            }
            //如果转换后的域名不符合格式就抛出异常
            if (!domainNameBuilder.toString().matches("^[.a-zA-Z0-9_-]+$")) {
                return null;
            }
        } catch (Exception exception) {
            return null;
        }
        return domainNameBuilder.toString().toLowerCase();
    }

    private void dnssecSign(List<Record> recordList, PrivateKey kskPrivateKey, DNSKEYRecord kskDnskeyRecord, PrivateKey zskPrivateKey, DNSKEYRecord zskDnskeyRecord) throws DNSSEC.DNSSECException {

        recordList.add(kskDnskeyRecord);

        recordList.add(zskDnskeyRecord);

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

    public void transformZone(DnsDomainName dnsDomainName, String queue) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, DNSSEC.DNSSECException {
        DnsDomainNameRecord dnsDomainNameRecord = new DnsDomainNameRecord();
        dnsDomainNameRecord.setDomainNameId(dnsDomainName.getId());
        List<DnsDomainNameRecord> dnsDomainNameRecordList = dnsDomainNameRecordMapper.selectDnsDomainNameRecordByDomainNameId(dnsDomainNameRecord);
        Map<String, Object> zoneMap = new HashMap<>();
        zoneMap.put("domain", dnsDomainName.getDomainName());
        zoneMap.put("dnssec", dnsDomainName.getDomainNameDnssec());
        zoneMap.put("type", "update");
        Map<String, List<Record>> geoZone = new HashMap<>();
        for (DnsDomainNameRecord dnsDomainNameRecordTemp : dnsDomainNameRecordList) {
            List<Record> recordList = geoZone.get(dnsDomainNameRecordTemp.getRecordGeo());
            recordList = (recordList == null) ? new LinkedList<>() : recordList;
            recordList.add(Record.fromWire(Base64.getDecoder().decode(dnsDomainNameRecordTemp.getRecordContent()), Section.ANSWER));
            geoZone.put(dnsDomainNameRecordTemp.getRecordGeo(), recordList);
        }

        if (dnsDomainName.getDomainNameDnssec()) {
            KeyFactory keyFactory = KeyFactory.getInstance("EC");
            PKCS8EncodedKeySpec pKCS8EncodedKeySpecKskPrivateKey = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(dnsDomainName.getDomainNameDnssecKskPrivateKey()));
            X509EncodedKeySpec x509EncodedKeySpecKskPublicKey = new X509EncodedKeySpec(Base64.getDecoder().decode(dnsDomainName.getDomainNameDnssecKskPublicKey()));
            PKCS8EncodedKeySpec pKCS8EncodedKeySpecZskPrivateKey = new PKCS8EncodedKeySpec(Base64.getDecoder().decode(dnsDomainName.getDomainNameDnssecZskPrivateKey()));
            X509EncodedKeySpec x509EncodedKeySpecZskPublicKey = new X509EncodedKeySpec(Base64.getDecoder().decode(dnsDomainName.getDomainNameDnssecZskPublicKey()));
            PrivateKey kskPrivateKey = keyFactory.generatePrivate(pKCS8EncodedKeySpecKskPrivateKey);
            PublicKey kskPublicKey = keyFactory.generatePublic(x509EncodedKeySpecKskPublicKey);
            PrivateKey zskPrivateKey = keyFactory.generatePrivate(pKCS8EncodedKeySpecZskPrivateKey);
            PublicKey zskPublicKey = keyFactory.generatePublic(x509EncodedKeySpecZskPublicKey);
            DNSKEYRecord kskDnsRecord = new DNSKEYRecord(Name.fromString(dnsDomainName.getDomainName()), DClass.IN, 3600, 0x101, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, kskPublicKey);
            DNSKEYRecord zskDnsRecord = new DNSKEYRecord(Name.fromString(dnsDomainName.getDomainName()), DClass.IN, 3600, DNSKEYRecord.Flags.ZONE_KEY, DNSKEYRecord.Protocol.DNSSEC, DNSSEC.Algorithm.ECDSAP256SHA256, zskPublicKey);
            NSECRecord nsecRecord = new NSECRecord(new Name(dnsDomainName.getDomainName()), DClass.IN, 3600, new Name(dnsDomainName.getDomainName()), new int[]{Type.SOA});
            geoZone.get("*").add(nsecRecord);
            for (String geo : geoZone.keySet()) {
                dnssecSign(geoZone.get(geo), kskPrivateKey, kskDnsRecord, zskPrivateKey, zskDnsRecord);
            }
        }

        zoneMap.put("geoZone", geoZone);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(zoneMap);

        rabbitMQ.send(byteArrayOutputStream.toByteArray(), queue);

    }

    public void deleteZone(DnsDomainName dnsDomainName) throws IOException {
        Map<String, Object> zoneMap = new HashMap<>();
        zoneMap.put("domain", dnsDomainName.getDomainName());
        zoneMap.put("type", "delete");
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ObjectOutputStream objectOutputStream = new ObjectOutputStream(byteArrayOutputStream);
        objectOutputStream.writeObject(zoneMap);
        rabbitMQ.send(byteArrayOutputStream.toByteArray(), "");
    }



}

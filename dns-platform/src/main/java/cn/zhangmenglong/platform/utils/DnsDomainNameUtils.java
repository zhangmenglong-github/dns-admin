package cn.zhangmenglong.platform.utils;

import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
import cn.zhangmenglong.platform.mapper.DnsDomainNameRecordMapper;
import cn.zhangmenglong.platform.rabbitmq.RabbitMQ;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.xbill.DNS.*;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.*;

@Component
public class DnsDomainNameUtils {

    @Autowired
    private RabbitMQ rabbitMQ;

    @Autowired
    private DnsDomainNameRecordMapper dnsDomainNameRecordMapper;

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

    public void transformZone(DnsDomainName dnsDomainName) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException, DNSSEC.DNSSECException {
        DnsDomainNameRecord dnsDomainNameRecord = new DnsDomainNameRecord();
        dnsDomainNameRecord.setDomainNameId(dnsDomainName.getId());
        List<DnsDomainNameRecord> dnsDomainNameRecordList = dnsDomainNameRecordMapper.selectDnsDomainNameRecordByDomainNameId(dnsDomainNameRecord);
        Map<String, Object> zoneMap = new HashMap<>();
        zoneMap.put("domain", dnsDomainName.getDomainName());
        zoneMap.put("dnssec", dnsDomainName.getDomainNameDnssec());
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

        rabbitMQ.send(byteArrayOutputStream.toByteArray());

    }

}

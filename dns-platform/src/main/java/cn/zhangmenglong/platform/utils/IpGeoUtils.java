package cn.zhangmenglong.platform.utils;

import com.maxmind.db.CHMCache;
import com.maxmind.geoip2.DatabaseReader;
import com.maxmind.geoip2.exception.GeoIp2Exception;
import com.maxmind.geoip2.model.CityResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;
import org.springframework.util.ResourceUtils;

import java.io.IOException;
import java.net.InetAddress;

@Component
public class IpGeoUtils {

    private DatabaseReader reader;
    public IpGeoUtils() {
        try {

            reader = new DatabaseReader.Builder(ResourceUtils.getFile("classpath:geo.mmdb")).withCache(new CHMCache()).build();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getCountry(String ip) {
        CityResponse response = null;
        try {
            response = reader.city(InetAddress.getByName(ip));
            return response.getCountry().getIsoCode();
        } catch (IOException | GeoIp2Exception ignored) {}
        return null;
    }

}

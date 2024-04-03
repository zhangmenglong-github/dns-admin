package cn.zhangmenglong.platform.config;

import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.api.impl.WxMpServiceImpl;
import me.chanjar.weixin.mp.config.WxMpConfigStorage;
import me.chanjar.weixin.mp.config.impl.WxMpDefaultConfigImpl;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class WechatMPConfig {

    @Value("${wechat.mp.appid}")
    private String appid;

    @Value("${wechat.mp.secret}")
    private String secret;

    @Value("${wechat.mp.token}")
    private String token;

    @Value("${wechat.mp.aeskey}")
    private String aeskey;

    /**
     * 微信客户端配置存储
     *
     * @return
     */
    @Bean
    public WxMpConfigStorage wxMpConfigStorage() {
        WxMpDefaultConfigImpl configStorage = new WxMpDefaultConfigImpl();
        // 公众号appId
        configStorage.setAppId(appid);
        // 公众号appSecret
        configStorage.setSecret(secret);
        // 公众号Token
        configStorage.setToken(token);
        // 公众号EncodingAESKey
        configStorage.setAesKey(aeskey);
        return configStorage;
    }

    /**
     * 声明实例
     *
     * @return
     */
    @Bean
    public WxMpService wxMpService() {
        WxMpService wxMpService = new WxMpServiceImpl();
        wxMpService.setWxMpConfigStorage(wxMpConfigStorage());
        return wxMpService;
    }

}

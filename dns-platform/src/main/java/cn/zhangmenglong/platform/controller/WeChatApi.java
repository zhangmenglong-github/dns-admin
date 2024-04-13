package cn.zhangmenglong.platform.controller;

import cn.zhangmenglong.common.annotation.Anonymous;
import cn.zhangmenglong.common.core.domain.entity.SysUser;
import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.uuid.IdUtils;
import cn.zhangmenglong.platform.constant.PlatformUserRegisterConstants;
import cn.zhangmenglong.platform.domain.DnsDomainNameWechatWithUser;
import cn.zhangmenglong.platform.mapper.DnsDomainNameWechatWithUserMapper;
import cn.zhangmenglong.system.service.ISysUserService;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Base64;
import java.util.Random;
import java.util.concurrent.TimeUnit;

@Anonymous
@RestController
public class WeChatApi {

    @Autowired
    private WxMpService wxMpService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private DnsDomainNameWechatWithUserMapper dnsDomainNameWechatWithUserMapper;

    @Autowired
    private RedisCache redisCache;

    @GetMapping(value = "/wechat/platform", produces = "application/xml; charset=UTF-8")
    public String getWechatServerPlatform(String signature, String timestamp, String nonce, String echostr) {
        if (wxMpService.checkSignature(timestamp, nonce, signature)) {
            return echostr;
        } else {
            return null;
        }
    }

    @PostMapping(value = "/wechat/platform", produces = "application/xml; charset=UTF-8")
    public String postWechatServerPlatform(@RequestBody String requestBody, @RequestParam("signature") String signature, @RequestParam(name = "encrypt_type", required = false) String encType, @RequestParam(name = "msg_signature", required = false) String msgSignature,
                                           @RequestParam("timestamp") String timestamp, @RequestParam("nonce") String nonce) {
        if (!this.wxMpService.checkSignature(timestamp, nonce, signature)) {
            throw new IllegalArgumentException("非法请求，可能属于伪造的请求！");
        }
        WxMpXmlMessage inMessage = null;
        if (encType == null) {
            // 明文传输的消息
            inMessage = WxMpXmlMessage.fromXml(requestBody);
        } else if ("aes".equals(encType)) {
            // aes加密的消息
            inMessage = WxMpXmlMessage.fromEncryptedXml(requestBody, wxMpService.getWxMpConfigStorage(), timestamp, nonce, msgSignature);
        }
        if (inMessage.getEvent() == null) {
            WxMpXmlOutTextMessage wxMpXmlOutTextMessage = new WxMpXmlOutTextMessage();
            wxMpXmlOutTextMessage.setToUserName(inMessage.getFromUser());
            wxMpXmlOutTextMessage.setMsgType("text");
            wxMpXmlOutTextMessage.setCreateTime(System.currentTimeMillis());
            wxMpXmlOutTextMessage.setFromUserName(inMessage.getToUser());
            long WECHAT_REGISTER_ACCESS_CACHE = redisCache.redisTemplate.opsForValue().increment(PlatformUserRegisterConstants.WECHAT_REGISTER_ACCESS_CACHE + inMessage.getFromUser());
            if (WECHAT_REGISTER_ACCESS_CACHE >= 10) {
                wxMpXmlOutTextMessage.setContent("操作频繁，等待五分钟后再操作");
            } else {
                redisCache.expire(PlatformUserRegisterConstants.WECHAT_REGISTER_ACCESS_CACHE + inMessage.getFromUser(), 5, TimeUnit.MINUTES);
                DnsDomainNameWechatWithUser dnsDomainNameWechatWithUser = dnsDomainNameWechatWithUserMapper.selectDnsDomainNameWechatWithUserByWechatMpOpenid(inMessage.getFromUser());
                if (dnsDomainNameWechatWithUser != null) {
                    SysUser sysUser = userService.selectUserById(dnsDomainNameWechatWithUser.getUserId());
                    wxMpXmlOutTextMessage.setContent("该微信已注册用户，用户名为【" + sysUser.getUserName() + "】，如忘记密码，请输入并发送找回密码");
                } else {
                    String WECHAT_REGISTER_USERNAME_CACHE = redisCache.getCacheObject(PlatformUserRegisterConstants.WECHAT_REGISTER_USERNAME_CACHE + inMessage.getContent());
                    if (WECHAT_REGISTER_USERNAME_CACHE != null) {
                        wxMpXmlOutTextMessage.setContent("该用户名注册锁定中，请稍后再试");
                    } else {
                        SysUser user = userService.selectUserByUserName(inMessage.getContent());
                        if (user == null) {
                            if ((inMessage.getContent().length() >= 5) && (inMessage.getContent().length() <= 20)) {
                                Random random = new Random();
                                int min = 100000;
                                int max = 999999;
                                int randomNumber = random.nextInt(max + 1 - min) + min;
                                redisCache.setCacheObject(PlatformUserRegisterConstants.WECHAT_REGISTER_USERNAME_CACHE + inMessage.getContent(), Base64.getEncoder().encodeToString(inMessage.getFromUser().getBytes()) + "&" + randomNumber, 5, TimeUnit.MINUTES);
                                wxMpXmlOutTextMessage.setContent("该用户名可注册，注册码为：" + randomNumber + ",五分钟内有效");
                            } else {
                                wxMpXmlOutTextMessage.setContent("用户名长度应该在5-20个字符之间");
                            }
                        } else {
                            wxMpXmlOutTextMessage.setContent("该用户名不可注册，请重新输入");
                        }
                    }
                }
            }
            return wxMpXmlOutTextMessage.toEncryptedXml(wxMpService.getWxMpConfigStorage());
        } else if ("subscribe".contentEquals(inMessage.getEvent())) {
            WxMpXmlOutTextMessage wxMpXmlOutTextMessage = new WxMpXmlOutTextMessage();
            wxMpXmlOutTextMessage.setToUserName(inMessage.getFromUser());
            wxMpXmlOutTextMessage.setMsgType("text");
            wxMpXmlOutTextMessage.setContent("请输入需要注册的用户名用来获取注册码");
            wxMpXmlOutTextMessage.setCreateTime(System.currentTimeMillis());
            wxMpXmlOutTextMessage.setFromUserName(inMessage.getToUser());
            return wxMpXmlOutTextMessage.toEncryptedXml(wxMpService.getWxMpConfigStorage());
        }
        return "";
    }
}

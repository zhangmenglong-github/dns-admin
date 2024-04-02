package cn.zhangmenglong.web.wechat;

import cn.zhangmenglong.common.annotation.Anonymous;
import cn.zhangmenglong.common.core.domain.entity.SysUser;
import cn.zhangmenglong.common.utils.uuid.IdUtils;
import cn.zhangmenglong.system.service.ISysUserService;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import me.chanjar.weixin.mp.bean.message.WxMpXmlOutTextMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Random;

@Anonymous
@RestController
public class WeChatApi {

    @Autowired
    private WxMpService wxMpService;

    @Autowired
    private ISysUserService userService;

    @GetMapping(value = "/wechat/platform", produces = "application/xml; charset=UTF-8")
    public String getWechatServerPlatform(String signature, String timestamp, String nonce, String echostr) {
        System.out.println(nonce);
        System.out.println(echostr);
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
            SysUser user = userService.selectUserByUserName(inMessage.getContent());
            WxMpXmlOutTextMessage wxMpXmlOutTextMessage = new WxMpXmlOutTextMessage();
            wxMpXmlOutTextMessage.setToUserName(inMessage.getFromUser());
            wxMpXmlOutTextMessage.setMsgType("text");
            if (user == null) {
                Random random = new Random();
                int min = 100000;
                int max = 999999;
                int randomNumber = random.nextInt(max + 1 - min) + min;
                wxMpXmlOutTextMessage.setContent("该用户名可注册，注册码为：" + randomNumber);
            } else {
                wxMpXmlOutTextMessage.setContent("该用户名不可注册，请重新输入");
            }

            wxMpXmlOutTextMessage.setCreateTime(System.currentTimeMillis());
            wxMpXmlOutTextMessage.setFromUserName(inMessage.getToUser());
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

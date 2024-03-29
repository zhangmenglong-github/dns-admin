package cn.zhangmenglong.web.wechat;

import cn.zhangmenglong.common.annotation.Anonymous;
import me.chanjar.weixin.mp.api.WxMpService;
import me.chanjar.weixin.mp.bean.message.WxMpXmlMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@Anonymous
@RestController
public class WeChatApi {

    @Autowired
    private WxMpService wxMpService;

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
            return "";
        }

        System.out.println(inMessage.getEvent());

        return "";
    }
}

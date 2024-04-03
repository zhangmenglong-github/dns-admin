package cn.zhangmenglong.platform.service.impl;

import cn.zhangmenglong.common.core.domain.model.RegisterBody;
import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.StringUtils;
import cn.zhangmenglong.framework.web.service.SysRegisterService;
import cn.zhangmenglong.platform.constant.PlatformUserRegisterConstants;
import cn.zhangmenglong.platform.service.IPlatformUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.Map;

@Service
public class PlatformUserServiceImpl implements IPlatformUserService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private SysRegisterService registerService;

    @Override
    public int registerService(RegisterBody registerBody) {

        String WECHAT_REGISTER_USERNAME_CACHE = redisCache.getCacheObject(PlatformUserRegisterConstants.WECHAT_REGISTER_USERNAME_CACHE + registerBody.getUsername());
        if (StringUtils.isNotEmpty(WECHAT_REGISTER_USERNAME_CACHE)) {
            String platformUserWechatOpenid = WECHAT_REGISTER_USERNAME_CACHE.split("&")[0];
            String code = WECHAT_REGISTER_USERNAME_CACHE.split("&")[1];
            if (code.contentEquals(registerBody.getCode())) {
                System.out.println(new String(Base64.getDecoder().decode(platformUserWechatOpenid)));
                return 0;
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }
}

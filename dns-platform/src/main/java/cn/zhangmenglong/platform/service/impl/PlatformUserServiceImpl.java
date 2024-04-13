package cn.zhangmenglong.platform.service.impl;

import cn.zhangmenglong.common.constant.Constants;
import cn.zhangmenglong.common.constant.UserConstants;
import cn.zhangmenglong.common.core.domain.entity.SysUser;
import cn.zhangmenglong.common.core.domain.model.RegisterBody;
import cn.zhangmenglong.common.core.redis.RedisCache;
import cn.zhangmenglong.common.utils.MessageUtils;
import cn.zhangmenglong.common.utils.SecurityUtils;
import cn.zhangmenglong.common.utils.StringUtils;
import cn.zhangmenglong.framework.manager.AsyncManager;
import cn.zhangmenglong.framework.manager.factory.AsyncFactory;
import cn.zhangmenglong.framework.web.service.SysRegisterService;
import cn.zhangmenglong.platform.constant.PlatformUserRegisterConstants;
import cn.zhangmenglong.platform.domain.DnsDomainNameWechatWithUser;
import cn.zhangmenglong.platform.mapper.DnsDomainNameWechatWithUserMapper;
import cn.zhangmenglong.platform.service.IPlatformUserService;
import cn.zhangmenglong.system.service.ISysRoleService;
import cn.zhangmenglong.system.service.ISysUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Base64;
import java.util.Map;

@Service
public class PlatformUserServiceImpl implements IPlatformUserService {

    @Autowired
    private RedisCache redisCache;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private DnsDomainNameWechatWithUserMapper dnsDomainNameWechatWithUserMapper;

    @Transactional
    @Override
    public int registerService(RegisterBody registerBody) {
        String WECHAT_REGISTER_USERNAME_CACHE = redisCache.getCacheObject(PlatformUserRegisterConstants.WECHAT_REGISTER_USERNAME_CACHE + registerBody.getUsername());
        if (StringUtils.isNotEmpty(WECHAT_REGISTER_USERNAME_CACHE)) {
            String platformUserWechatOpenid = WECHAT_REGISTER_USERNAME_CACHE.split("&")[0];
            String code = WECHAT_REGISTER_USERNAME_CACHE.split("&")[1];
            if (code.contentEquals(registerBody.getCode())) {
                String username = registerBody.getUsername(), password = registerBody.getPassword();
                SysUser sysUser = new SysUser();
                sysUser.setUserName(username);
                if (StringUtils.isEmpty(password))
                {
                    return -2;
                }
                else if (password.length() < UserConstants.PASSWORD_MIN_LENGTH
                        || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
                {
                    return -3;
                }
                else if (!userService.checkUserNameUnique(sysUser))
                {
                    return -4;
                }
                else
                {
                    sysUser.setNickName(username);
                    sysUser.setPassword(SecurityUtils.encryptPassword(password));
                    boolean regFlag = userService.registerUser(sysUser);
                    if (!regFlag)
                    {
                        return -5;
                    }
                    else
                    {
                        AsyncManager.me().execute(AsyncFactory.recordLogininfor(username, Constants.REGISTER, MessageUtils.message("user.register.success")));
                    }
                    Long[] roleIds = new Long[1];
                    roleIds[0] = 2L;
                    userService.insertUserAuth(sysUser.getUserId(), roleIds);
                    DnsDomainNameWechatWithUser dnsDomainNameWechatWithUser = new DnsDomainNameWechatWithUser();
                    dnsDomainNameWechatWithUser.setWechatMpOpenid(new String(Base64.getDecoder().decode(platformUserWechatOpenid)));
                    dnsDomainNameWechatWithUser.setUserId(sysUser.getUserId());
                    dnsDomainNameWechatWithUserMapper.insertDnsDomainNameWechatWithUser(dnsDomainNameWechatWithUser);
                    redisCache.deleteObject(PlatformUserRegisterConstants.WECHAT_REGISTER_USERNAME_CACHE + registerBody.getUsername());
                }
                return 0;
            } else {
                return -1;
            }
        } else {
            return -1;
        }
    }
}

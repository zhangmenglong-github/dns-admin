package cn.zhangmenglong.platform.controller;


import cn.zhangmenglong.common.annotation.Anonymous;
import cn.zhangmenglong.common.core.domain.AjaxResult;
import cn.zhangmenglong.common.core.domain.model.RegisterBody;
import cn.zhangmenglong.platform.service.IPlatformUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


@Anonymous
@RestController
@RequestMapping("/platform/user")
public class PlatformUserController {

    @Autowired
    private IPlatformUserService platformUserService;

    @PostMapping("/register")
    public AjaxResult register(@RequestBody RegisterBody registerBody) {
        return AjaxResult.success(platformUserService.registerService(registerBody));
    }

}

package cn.zhangmenglong.platform.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import cn.zhangmenglong.common.annotation.Log;
import cn.zhangmenglong.common.core.controller.BaseController;
import cn.zhangmenglong.common.core.domain.AjaxResult;
import cn.zhangmenglong.common.enums.BusinessType;
import cn.zhangmenglong.platform.domain.po.DnsDomainName;
import cn.zhangmenglong.platform.service.IDnsDomainNameService;
import cn.zhangmenglong.common.utils.poi.ExcelUtil;
import cn.zhangmenglong.common.core.page.TableDataInfo;

/**
 * 域名Controller
 * 
 * @author dns
 * @date 2024-04-13
 */
@RestController
@RequestMapping("/platform/dnsDomainName")
public class DnsDomainNameController extends BaseController
{
    @Autowired
    private IDnsDomainNameService dnsDomainNameService;

    /**
     * 查询域名列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/list")
    public TableDataInfo list(DnsDomainName dnsDomainName)
    {
        startPage();
        List<DnsDomainName> list = dnsDomainNameService.selectDnsDomainNameList(dnsDomainName);
        return getDataTable(list);
    }

    /**
     * 导出域名列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:export')")
    @Log(title = "域名", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DnsDomainName dnsDomainName)
    {
        List<DnsDomainName> list = dnsDomainNameService.selectDnsDomainNameList(dnsDomainName);
        ExcelUtil<DnsDomainName> util = new ExcelUtil<DnsDomainName>(DnsDomainName.class);
        util.exportExcel(response, list, "域名数据");
    }

    /**
     * 获取域名详细信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(dnsDomainNameService.selectDnsDomainNameById(id));
    }

    /**
     * 新增域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:add')")
    @Log(title = "域名", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DnsDomainName dnsDomainName)
    {
        return toAjax(dnsDomainNameService.insertDnsDomainName(dnsDomainName));
    }

    /**
     * 修改域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:edit')")
    @Log(title = "域名", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DnsDomainName dnsDomainName)
    {
        return toAjax(dnsDomainNameService.updateDnsDomainName(dnsDomainName));
    }

    /**
     * 删除域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:remove')")
    @Log(title = "域名", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(dnsDomainNameService.deleteDnsDomainNameByIds(ids));
    }
}

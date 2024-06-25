package cn.zhangmenglong.platform.controller;

import java.util.List;
import javax.servlet.http.HttpServletResponse;

import cn.zhangmenglong.common.utils.SecurityUtils;
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
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
import org.xbill.DNS.Type;

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
     * 导出域名记录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:export')")
    @Log(title = "域名记录导出", businessType = BusinessType.EXPORT)
    @PostMapping("/record/export")
    public void export(HttpServletResponse response, DnsDomainNameRecord dnsDomainNameRecord)
    {
        List<DnsDomainNameRecord> list = dnsDomainNameService.selectDnsDomainNameRecord(dnsDomainNameRecord);
        ExcelUtil<DnsDomainNameRecord> util = new ExcelUtil<DnsDomainNameRecord>(DnsDomainNameRecord.class);
        util.exportExcel(response, list, "域名记录数据");
    }


    /**
     * 删除域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:remove')")
    @Log(title = "域名记录删除", businessType = BusinessType.DELETE)
    @DeleteMapping("/record/{ids}")
    public AjaxResult removeRecords(@PathVariable Long[] ids)
    {
        return AjaxResult.success(dnsDomainNameService.deleteDnsDomainNameRecordByIds(ids));
    }


    /**
     * 查询域名纪录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:edit')")
    @Log(title = "修改域名记录", businessType = BusinessType.UPDATE)
    @PostMapping("/record/update")
    public AjaxResult updateRecord(@RequestBody DnsDomainNameRecord dnsDomainNameRecord)
    {
        return AjaxResult.success(dnsDomainNameService.updateDnsDomainNameRecord(dnsDomainNameRecord));
    }

    /**
     * 查询域名纪录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/record/get")
    public AjaxResult getRecord(DnsDomainNameRecord dnsDomainNameRecord)
    {
        return AjaxResult.success(dnsDomainNameService.getDnsDomainNameRecord(dnsDomainNameRecord));
    }

    /**
     * 查询域名纪录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:add')")
    @Log(title = "添加域名记录", businessType = BusinessType.INSERT)
    @PostMapping("/record/add")
    public AjaxResult addRecord(@RequestBody DnsDomainNameRecord dnsDomainNameRecord)
    {
        return AjaxResult.success(dnsDomainNameService.insertDnsDomainNameRecord(dnsDomainNameRecord));
    }

    /**
     * 查询域名纪录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/record/list")
    public TableDataInfo listRecord(DnsDomainNameRecord dnsDomainNameRecord)
    {
        List<DnsDomainNameRecord> list = dnsDomainNameService.selectDnsDomainNameRecord(dnsDomainNameRecord);
        return getDataTable(list);
    }

    /**
     * 查询域名统计数量
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/list/statistics/count")
    public AjaxResult listStatisticsCount()
    {
        return AjaxResult.success(dnsDomainNameService.selectDnsDomainNameStatisticsCountByUserId(SecurityUtils.getUserId()));
    }

    /**
     * 查询域名正常数量
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/list/normal")
    public TableDataInfo listNormalDnsDomainName(DnsDomainName dnsDomainName)
    {
        startPage();
        dnsDomainName.setDomainNameStatus("0");
        List<DnsDomainName> list = dnsDomainNameService.selectDnsDomainNameList(dnsDomainName);
        return getDataTable(list);
    }


    /**
     * 查询域名解析统计信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/statistics")
    public AjaxResult statistics(DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.queryDnsDomainNameResolutionStatistics(dnsDomainName));
    }

    /**
     * 查询域名解析地理位置统计信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/statistics/geo")
    public AjaxResult statisticsQueryGeo(DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.queryDnsDomainNameResolutionStatisticsQueryGeo(dnsDomainName));
    }


    /**
     * 查询域名子域解析统计信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/statistics/query/name")
    public AjaxResult statisticsQueryName(DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.queryDnsDomainNameResolutionStatisticsQueryName(dnsDomainName));
    }

    /**
     * 查询域名解析类型统计信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/statistics/query/type")
    public AjaxResult statisticsQueryType(DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.queryDnsDomainNameResolutionStatisticsQueryType(dnsDomainName));
    }

    /**
     * 查询域名子域解析类型统计信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:list')")
    @GetMapping("/statistics/query/name/type")
    public AjaxResult statisticsQueryNameType(DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.queryDnsDomainNameResolutionStatisticsQueryNameType(dnsDomainName));
    }

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
    @Log(title = "域名导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DnsDomainName dnsDomainName)
    {
        dnsDomainName.setUserId(SecurityUtils.getUserId());
        List<DnsDomainName> list = dnsDomainNameService.selectDnsDomainNameList(dnsDomainName);
        ExcelUtil<DnsDomainName> util = new ExcelUtil<DnsDomainName>(DnsDomainName.class);
        util.exportExcel(response, list, "域名数据");
    }

    /**
     * 新增域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:add')")
    @Log(title = "域名添加", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.insertDnsDomainName(dnsDomainName));
    }

    /**
     * 验证域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:edit')")
    @Log(title = "域名验证", businessType = BusinessType.UPDATE)
    @PutMapping("/validate")
    public AjaxResult validate(@RequestBody DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.validateDnsDomainName(dnsDomainName));
    }

    /**
     * 刷新域名验证
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:edit')")
    @Log(title = "域名验证刷新", businessType = BusinessType.UPDATE)
    @PutMapping("/validate/refresh")
    public AjaxResult validateRefresh(@RequestBody DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.validateRefreshDnsDomainName(dnsDomainName));
    }

    /**
     * 修改域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:edit')")
    @Log(title = "域名DNSSEC修改", businessType = BusinessType.UPDATE)
    @PutMapping("/dnssec")
    public AjaxResult dnssec(@RequestBody DnsDomainName dnsDomainName)
    {
        return AjaxResult.success(dnsDomainNameService.updateDnsDomainNameDnssec(dnsDomainName));
    }

    /**
     * 删除域名
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainName:remove')")
    @Log(title = "域名删除", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return AjaxResult.success(dnsDomainNameService.deleteDnsDomainNameByIds(ids));
    }
}

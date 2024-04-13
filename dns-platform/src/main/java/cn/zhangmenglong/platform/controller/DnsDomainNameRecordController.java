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
import cn.zhangmenglong.platform.domain.po.DnsDomainNameRecord;
import cn.zhangmenglong.platform.service.IDnsDomainNameRecordService;
import cn.zhangmenglong.common.utils.poi.ExcelUtil;
import cn.zhangmenglong.common.core.page.TableDataInfo;

/**
 * 域名记录Controller
 * 
 * @author dns
 * @date 2024-04-13
 */
@RestController
@RequestMapping("/platform/dnsDomainNameRecord")
public class DnsDomainNameRecordController extends BaseController
{
    @Autowired
    private IDnsDomainNameRecordService dnsDomainNameRecordService;

    /**
     * 查询域名记录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainNameRecord:list')")
    @GetMapping("/list")
    public TableDataInfo list(DnsDomainNameRecord dnsDomainNameRecord)
    {
        startPage();
        List<DnsDomainNameRecord> list = dnsDomainNameRecordService.selectDnsDomainNameRecordList(dnsDomainNameRecord);
        return getDataTable(list);
    }

    /**
     * 导出域名记录列表
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainNameRecord:export')")
    @Log(title = "域名记录", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, DnsDomainNameRecord dnsDomainNameRecord)
    {
        List<DnsDomainNameRecord> list = dnsDomainNameRecordService.selectDnsDomainNameRecordList(dnsDomainNameRecord);
        ExcelUtil<DnsDomainNameRecord> util = new ExcelUtil<DnsDomainNameRecord>(DnsDomainNameRecord.class);
        util.exportExcel(response, list, "域名记录数据");
    }

    /**
     * 获取域名记录详细信息
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainNameRecord:query')")
    @GetMapping(value = "/{id}")
    public AjaxResult getInfo(@PathVariable("id") Long id)
    {
        return success(dnsDomainNameRecordService.selectDnsDomainNameRecordById(id));
    }

    /**
     * 新增域名记录
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainNameRecord:add')")
    @Log(title = "域名记录", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@RequestBody DnsDomainNameRecord dnsDomainNameRecord)
    {
        return toAjax(dnsDomainNameRecordService.insertDnsDomainNameRecord(dnsDomainNameRecord));
    }

    /**
     * 修改域名记录
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainNameRecord:edit')")
    @Log(title = "域名记录", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@RequestBody DnsDomainNameRecord dnsDomainNameRecord)
    {
        return toAjax(dnsDomainNameRecordService.updateDnsDomainNameRecord(dnsDomainNameRecord));
    }

    /**
     * 删除域名记录
     */
    @PreAuthorize("@ss.hasPermi('platform:dnsDomainNameRecord:remove')")
    @Log(title = "域名记录", businessType = BusinessType.DELETE)
	@DeleteMapping("/{ids}")
    public AjaxResult remove(@PathVariable Long[] ids)
    {
        return toAjax(dnsDomainNameRecordService.deleteDnsDomainNameRecordByIds(ids));
    }
}

package cn.zhangmenglong.common.exception.file;

import cn.zhangmenglong.common.exception.base.BaseException;

/**
 * 文件信息异常类
 * 
 * @author dns
 */
public class FileException extends BaseException
{
    private static final long serialVersionUID = 1L;

    public FileException(String code, Object[] args)
    {
        super("file", code, args, null);
    }

}

package fy.cfg.print;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 打印dot文件时候，可能源码中一些符号，导致构图失败
 * 比如: 字符串中的引号
 * 这个工具就是用来过滤这些噪音的
 */
public class DotPrintFilter {
    private static Pattern p;
    private static Pattern dotP;
    private static Pattern quotationP;

    static {
        p = Pattern.compile("\r|\n|\r\n");
        dotP = Pattern.compile(",");
        quotationP = Pattern.compile("\"");
    }

    public static String filterQuotation(String originalStr){
        return originalStr.replaceAll("\"","'");
    }

    public static String AstNodeFilter(String originalStr){
        Matcher matcher = p.matcher(originalStr);
        Matcher matcher2 = dotP.matcher(matcher.replaceAll(""));
        Matcher matcher3 = quotationP.matcher(matcher2.replaceAll("."));
        return matcher3.replaceAll("'");
    }
}

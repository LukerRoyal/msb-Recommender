package com.alibaba.dubbo.demo.impl;

import java.text.SimpleDateFormat;
import java.util.Date;

public class DateUtil {


    //获取时间  粒度到分钟级别
    //202004072050
    public static String getMin(Date date){
        String pattern = "yyyyMMddHHmm";
        SimpleDateFormat dateFormat = new SimpleDateFormat(pattern);
        String min = dateFormat.format(date);
        return min;
    }
}

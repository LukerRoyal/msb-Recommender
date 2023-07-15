package com.alibaba.dubbo.demo;

import java.util.List;

public interface ProgramRecommendService {
    List<String> getProgramRecommendList(String uid);
}

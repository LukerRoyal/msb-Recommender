package org.dubbo.consumer2;

import com.alibaba.dubbo.demo.ProgramRecommendService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

import java.util.List;
//我讲的可能是错的，一定自己测试
public class Consumer3 {
    public static void main(String[] args) {
        //测试常规服务
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("consumer.xml");
        context.start();
        ProgramRecommendService prService = context.getBean(ProgramRecommendService.class);
        while (true){
            List<String> recommendList = prService.getProgramRecommendList("cd00000000");
            for (String item : recommendList) {
                System.out.println(item);
            }
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

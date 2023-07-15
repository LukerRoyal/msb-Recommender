package org.dubbo.consumer2;

import com.alibaba.dubbo.demo.DemoService;
import com.alibaba.dubbo.demo.RcmdService;
import org.springframework.context.support.ClassPathXmlApplicationContext;

/**
 * Created by zfg on 2020/1/13.
 */
public class Consumer2 {
    public static void main(String[] args) {

        //测试常规服务
        ClassPathXmlApplicationContext context = new ClassPathXmlApplicationContext("consumer.xml");
        context.start();
        System.out.println("consumer2 start");
        DemoService demoService = context.getBean(DemoService.class);
        while (true){
            System.out.println("consumer2");
            demoService.sayName("Angelababy");
            try {
                Thread.sleep(10000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

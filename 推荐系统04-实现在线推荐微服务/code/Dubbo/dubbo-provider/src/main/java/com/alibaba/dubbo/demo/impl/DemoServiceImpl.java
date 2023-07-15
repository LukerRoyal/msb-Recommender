package com.alibaba.dubbo.demo.impl;

import com.alibaba.dubbo.demo.DemoService;

public class DemoServiceImpl implements DemoService {

	@Override
	public void sayName(String name) {
		 System.out.println("say name: " + name);
	}

}

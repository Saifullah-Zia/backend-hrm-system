package com.hrm.system;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class HrmSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(HrmSystemApplication.class, args);
	}

}

package com.dsms;

import org.springframework.boot.SpringApplication;

public class TestDsmsBackendApplication {

	public static void main(String[] args) {
		SpringApplication.from(DsmsBackendApplication::main).with(TestcontainersConfiguration.class).run(args);
	}

}

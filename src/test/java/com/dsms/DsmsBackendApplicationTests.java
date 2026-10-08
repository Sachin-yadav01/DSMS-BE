package com.dsms;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
@EnabledIf("dockerAvailable")
class DsmsBackendApplicationTests {

	static boolean dockerAvailable() {
		try {
			return org.testcontainers.DockerClientFactory.instance().isDockerAvailable();
		} catch (Exception ex) {
			return false;
		}
	}

	@Test
	void contextLoads() {
	}
}

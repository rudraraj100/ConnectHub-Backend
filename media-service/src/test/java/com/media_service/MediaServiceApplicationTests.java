package com.media_service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Disabled;
import org.springframework.boot.test.context.SpringBootTest;

@Disabled("Requires S3 / local path - skipped in unit test phase")
@SpringBootTest
class MediaServiceApplicationTests {
	@Test
	void contextLoads() {
	}
}
package com.stream_app;

import com.stream_app.services.VideoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.IOException;

@SpringBootTest
class SpringStreamBackendApplicationTests {

	@Autowired
	VideoService videoService;

	@Test
	void contextLoads() throws IOException {

		videoService.processVideo("04601909-d43a-49f5-8534-22a24bb84c8a");
	}

}

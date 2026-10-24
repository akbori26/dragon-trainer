package com.akbori.solution.mugloar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The CLI runner is switched off; otherwise it starts the menu loop and waits for keyboard input forever
@SpringBootTest(properties = "mugloar.cli.enabled=false")
class DragonTrainerApplicationTests {

	@Test
	void contextLoads() {
	}

}
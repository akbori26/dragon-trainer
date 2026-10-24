package com.akbori.solution.mugloar;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

// The CLI runner is switched off; otherwise it starts the menu loop and waits for keyboard input forever.
// An in-memory database keeps the test independent of the local data/mugloar.db.
@SpringBootTest(properties = {
		"mugloar.cli.enabled=false",
		"spring.datasource.url=jdbc:sqlite:file:contexttest?mode=memory&cache=shared"
})
class DragonTrainerApplicationTests {

	@Test
	void contextLoads() {
	}

}
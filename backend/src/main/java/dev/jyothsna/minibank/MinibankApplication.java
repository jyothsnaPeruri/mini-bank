package dev.jyothsna.minibank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class MinibankApplication {

	public static void main(String[] args) {
		SpringApplication.run(MinibankApplication.class, args);
	}

}

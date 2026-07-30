package com.dezxxx.minios3;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class Minios3Application {

	public static void main(String[] args) {
		SpringApplication.run(Minios3Application.class, args);
	}

}

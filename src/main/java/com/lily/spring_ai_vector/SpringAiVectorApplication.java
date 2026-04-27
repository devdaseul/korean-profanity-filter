package com.lily.spring_ai_vector;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import com.lily.spring_ai_vector.config.FilterPipelineProperties;

@SpringBootApplication
@EnableConfigurationProperties(FilterPipelineProperties.class)
public class SpringAiVectorApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringAiVectorApplication.class, args);
	}

}

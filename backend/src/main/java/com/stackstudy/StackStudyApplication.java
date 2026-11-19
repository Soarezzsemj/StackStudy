package com.stackstudy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

// O login é por JWT; não queremos o usuário em memória que o Spring cria por padrão.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
@ConfigurationPropertiesScan
public class StackStudyApplication {

  public static void main(String[] args) {
    SpringApplication.run(StackStudyApplication.class, args);
  }
}

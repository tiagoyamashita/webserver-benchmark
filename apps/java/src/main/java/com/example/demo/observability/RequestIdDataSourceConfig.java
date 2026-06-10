package com.example.demo.observability;

import javax.sql.DataSource;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("postgres")
public class RequestIdDataSourceConfig implements BeanPostProcessor {

  @Override
  public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
    if ("dataSource".equals(beanName)
        && bean instanceof DataSource dataSource
        && !(bean instanceof RequestIdDataSource)) {
      return new RequestIdDataSource(dataSource);
    }
    return bean;
  }
}

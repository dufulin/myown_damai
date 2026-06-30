package com.myown.damai.common.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/**
 * Registers common trace infrastructure for Servlet-based Damai services.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class ServletObservabilityConfiguration {

    /**
     * Registers the trace filter before controllers and exception handlers.
     */
    @Bean
    public FilterRegistrationBean<TraceContextFilter> traceContextFilterRegistration() {
        FilterRegistrationBean<TraceContextFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(new TraceContextFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.setName("damaiTraceContextFilter");
        return registration;
    }
}

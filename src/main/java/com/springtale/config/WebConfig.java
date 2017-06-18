package com.springtale.config;

import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;

/**
 * Created by wsy on 2017/6/10.
 */

@Configuration
@EnableWebMvc
@ComponentScan("com.springtale.controller")
public class WebConfig {
}

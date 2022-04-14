package com.redislabs.demos.redisbank;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class SecurityConfiguration extends WebSecurityConfigurerAdapter {

    @Value("${spring.redis.bank.username}")
    private String username;

    @Value("${spring.redis.bank.password}")
    private String password;

    @Value("${spring.redis.bank.roles}")
    private String roles;

    @Override
    public void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
            .authorizeRequests()
            .antMatchers("/auth-login.html").permitAll()
            .antMatchers("/assets/**").permitAll()
            .anyRequest().authenticated()
            .and()
            .formLogin()
            .loginPage("/auth-login.html")
            .loginProcessingUrl("/perform_login")
            .defaultSuccessUrl("/index.html")
            .failureUrl("/auth-login.html?error=true");
    }

    @Override
    public void configure(AuthenticationManagerBuilder auth) throws Exception   {
        PasswordEncoder encoder =
                PasswordEncoderFactories.createDelegatingPasswordEncoder();
        auth.inMemoryAuthentication()
        .withUser(username)
        .password(encoder.encode(password))
        .roles(roles);
    }

}

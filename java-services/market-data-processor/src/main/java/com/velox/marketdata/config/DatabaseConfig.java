package com.velox.marketdata.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Database configuration for the Market Data Processor microservice.
 * Configures PostgreSQL with TimescaleDB for time-series data storage.
 */
@Configuration
@EnableJpaRepositories(basePackages = "com.velox.marketdata.repository")
@EnableTransactionManagement
public class DatabaseConfig {

    @Value("${spring.datasource.url}")
    private String databaseUrl;

    @Value("${spring.datasource.username}")
    private String username;

    @Value("${spring.datasource.password}")
    private String password;

    @Value("${spring.datasource.driver-class-name}")
    private String driverClassName;

    @Value("${spring.jpa.hibernate.ddl-auto}")
    private String ddlAuto;

    @Value("${spring.jpa.show-sql}")
    private boolean showSql;

    @Value("${spring.jpa.properties.hibernate.dialect}")
    private String dialect;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_size}")
    private int batchSize;

    @Value("${spring.jpa.properties.hibernate.order_inserts}")
    private boolean orderInserts;

    @Value("${spring.jpa.properties.hibernate.order_updates}")
    private boolean orderUpdates;

    @Value("${spring.jpa.properties.hibernate.jdbc.batch_versioned_data}")
    private boolean batchVersionedData;

    /**
     * DataSource configuration for PostgreSQL with TimescaleDB.
     * Optimized for high-throughput time-series data operations.
     */
    @Bean
    public DataSource dataSource() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(driverClassName);
        dataSource.setUrl(databaseUrl);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        return dataSource;
    }

    /**
     * Entity manager factory configuration with Hibernate.
     * Optimized for time-series data handling with TimescaleDB.
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory() {
        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource());
        em.setPackagesToScan("com.velox.marketdata.model");

        HibernateJpaVendorAdapter vendorAdapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(vendorAdapter);

        Map<String, Object> properties = new HashMap<>();
        
        // Basic Hibernate settings
        properties.put("hibernate.hbm2ddl.auto", ddlAuto);
        properties.put("hibernate.show_sql", showSql);
        properties.put("hibernate.dialect", dialect);
        
        // Performance optimizations for high-throughput operations
        properties.put("hibernate.jdbc.batch_size", batchSize);
        properties.put("hibernate.order_inserts", orderInserts);
        properties.put("hibernate.order_updates", orderUpdates);
        properties.put("hibernate.jdbc.batch_versioned_data", batchVersionedData);
        
        // Connection pool settings
        properties.put("hibernate.connection.provider_disables_autocommit", true);
        properties.put("hibernate.connection.handling_mode", "DELAYED_ACQUISITION_AND_RELEASE_AFTER_STATEMENT");
        
        // Query optimization
        properties.put("hibernate.cache.use_second_level_cache", false);
        properties.put("hibernate.cache.use_query_cache", false);
        
        // TimescaleDB specific optimizations
        properties.put("hibernate.jdbc.time_zone", "UTC");
        properties.put("hibernate.type.included_standard_types", "false");
        
        // Statement batching and streaming
        properties.put("hibernate.jdbc.fetch_size", 1000);
        properties.put("hibernate.default_batch_fetch_size", 100);
        
        em.setJpaProperties(properties);
        return em;
    }

    /**
     * Transaction manager configuration.
     */
    @Bean
    public PlatformTransactionManager transactionManager() {
        JpaTransactionManager transactionManager = new JpaTransactionManager();
        transactionManager.setEntityManagerFactory(entityManagerFactory().getObject());
        return transactionManager;
    }
}
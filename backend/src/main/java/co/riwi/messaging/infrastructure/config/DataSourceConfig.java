package co.riwi.messaging.infrastructure.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

/**
 * D-07: the running application connects ONLY as rw_app (NOSUPERUSER,
 * NOBYPASSRLS) — {@link #rwAppDataSource()} is the sole datasource used by
 * every controller/use case/repository. rw_migrator credentials are only
 * ever wired into the seed loader, and only under the {@code seed} Spring
 * profile (never present in a normal running instance's classpath
 * configuration path) — see infrastructure.seed.SeedRunner.
 */
@Configuration
@EnableTransactionManagement
public class DataSourceConfig {

    @Bean
    @Primary
    public DataSource rwAppDataSource(
            @Value("${app.datasource.jdbc-url}") String url,
            @Value("${app.datasource.app-user}") String user,
            @Value("${app.datasource.app-password}") String password
    ) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(user)
                .password(password)
                .build();
        ds.setPoolName("rw-app-pool");
        ds.setMaximumPoolSize(20);
        return ds;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource rwAppDataSource) {
        return new JdbcTemplate(rwAppDataSource);
    }

    @Bean
    public NamedParameterJdbcTemplate namedParameterJdbcTemplate(DataSource rwAppDataSource) {
        return new NamedParameterJdbcTemplate(rwAppDataSource);
    }

    @Bean
    public PlatformTransactionManager transactionManager(DataSource rwAppDataSource) {
        return new org.springframework.jdbc.datasource.DataSourceTransactionManager(rwAppDataSource);
    }

    @Bean
    @Profile("seed")
    public DataSource rwMigratorDataSource(
            @Value("${app.datasource.jdbc-url}") String url,
            @Value("${app.datasource.migrator-user}") String user,
            @Value("${app.datasource.migrator-password}") String password
    ) {
        HikariDataSource ds = DataSourceBuilder.create()
                .type(HikariDataSource.class)
                .url(url)
                .username(user)
                .password(password)
                .build();
        ds.setPoolName("rw-migrator-seed-pool");
        ds.setMaximumPoolSize(2);
        return ds;
    }
}

package com.bart.maris.middleware.config;

import com.bart.maris.middleware.legacy.LegacyProgramService;
import com.bart.maris.middleware.legacy.client.LegacyBackendHealthIndicator;
import com.bart.maris.middleware.legacy.client.LegacyServiceClient;
import com.bart.maris.middleware.legacy.control.MessageResolver;
import com.bart.maris.middleware.legacy.layout.LegacyCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/** Wires the legacy layer: catalog, HTTP client, program service, health check. */
@Configuration(proxyBeanMethods = false)
public class LegacyConfig {

    @Bean
    LegacyCatalog legacyCatalog(ResourceLoader loader, MarisProperties props) {
        return LegacyCatalog.load(loader, props.catalog().location());
    }

    @Bean
    RestClient legacyRestClient(MarisProperties props) {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(props.legacy().connectTimeout())
                .version(HttpClient.Version.HTTP_1_1)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(jdk);
        factory.setReadTimeout(props.legacy().readTimeout());
        return RestClient.builder().baseUrl(props.legacy().baseUrl()).requestFactory(factory).build();
    }

    @Bean
    LegacyServiceClient legacyServiceClient(RestClient legacyRestClient, ObjectMapper objectMapper, MarisProperties props) {
        return new LegacyServiceClient(legacyRestClient, objectMapper, props.legacy());
    }

    @Bean
    LegacyProgramService legacyProgramService(LegacyCatalog catalog, LegacyServiceClient client) {
        return new LegacyProgramService(catalog, client);
    }

    @Bean
    MessageResolver messageResolver(LegacyCatalog catalog) {
        return new MessageResolver(catalog);
    }

    @Bean("legacyBackend")
    LegacyBackendHealthIndicator legacyBackendHealthIndicator(RestClient legacyRestClient, LegacyServiceClient client) {
        return new LegacyBackendHealthIndicator(legacyRestClient, client);
    }
}

package com.codesolutions.config;

import com.codesolutions.integrations.http.LegacyHttpClient;
import com.codesolutions.integrations.sftp.SftpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.URL;

/**
 * Beans for legacy integrations.
 *
 * Both clients are non-reactive at the lowest layer (SOAP and SFTP are
 * inherently blocking). They are wrapped / used through Mono.fromCallable
 * in the service layer to keep the WebFlux pipeline non-blocking.
 */
@Configuration
public class IntegrationConfig {

    @Bean
    public LegacyHttpClient legacyHttpClient(
            @Value("${integrations.legacy-http.base-url:http://localhost:9000}") String baseUrl
    ) {
        return new LegacyHttpClient(baseUrl);
    }

    /**
     * SOAP client — points at a published WSDL.
     * Disabled by default; enable in production by setting
     * integrations.soap.wsdl-url.
     */
    @Bean
    public com.codesolutions.integrations.soap.LegacyErpClient legacyErpClient(
            @Value("${integrations.soap.wsdl-url:}") String wsdlUrl
    ) throws Exception {
        if (wsdlUrl == null || wsdlUrl.isEmpty()) {
            // Fallback: in-process implementation
            return new com.codesolutions.integrations.soap.LegacyErpClient(
                    new URL("http://localhost/wsdl-stub/LegacyErpService.wsdl")
            ) {
                // override the underlying port to use the @Component bean instead
            };
        }
        return new com.codesolutions.integrations.soap.LegacyErpClient(new URL(wsdlUrl));
    }

    /**
     * SFTP client — lazy because the connection should only open
     * when there's actual work to do.
     */
    @Bean
    public SftpClientFactory sftpClientFactory(
            @Value("${integrations.sftp.host:localhost}") String host,
            @Value("${integrations.sftp.port:22}") int port,
            @Value("${integrations.sftp.user:dummy}") String user,
            @Value("${integrations.sftp.private-key-path:/dev/null}") String key
    ) {
        return () -> new SftpClient(host, port, user, key);
    }

    @FunctionalInterface
    public interface SftpClientFactory {
        SftpClient create() throws Exception;
    }
}

package br.com.itau.geradornotafiscal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;

import java.net.URI;

/**
 * Cliente do SQS, com o mesmo cliente HTTP do DynamoDB. Com endpoint configurado, aponta para o emulador local.
 */
@Configuration
public class SqsConfig {

    @Bean(destroyMethod = "close")
    public SqsClient sqsClient(SdkHttpClient clienteHttpAws,
                               @Value("${aws.regiao:}") String regiao,
                               @Value("${aws.sqs.endpoint:}") String endpoint) {
        var builder = SqsClient.builder().httpClient(clienteHttpAws);
        if (!regiao.isBlank()) {
            builder.region(Region.of(regiao));
        }
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint))
                    .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local")));
        }
        return builder.build();
    }
}

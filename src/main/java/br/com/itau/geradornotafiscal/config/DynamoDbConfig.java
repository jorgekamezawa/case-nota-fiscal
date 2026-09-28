package br.com.itau.geradornotafiscal.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.SdkHttpClient;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

/**
 * Cliente do DynamoDB e o cliente HTTP dos SDKs da AWS. Na AWS, região e credencial vêm do ambiente (papel IAM da tarefa); com endpoint configurado, o
 * cliente aponta para o emulador local, que aceita qualquer credencial.
 */
@Configuration
public class DynamoDbConfig {

    // Pool explícito: virtual threads não limitam quantas chamadas disputam as conexões (ADR-0002, E02-NF-07).
    @Bean(destroyMethod = "close")
    public SdkHttpClient clienteHttpAws(@Value("${aws.http.maximo-de-conexoes:50}") int maximoDeConexoes) {
        return Apache5HttpClient.builder().maxConnections(maximoDeConexoes).build();
    }

    @Bean(destroyMethod = "close")
    public DynamoDbClient dynamoDbClient(SdkHttpClient clienteHttpAws,
                                         @Value("${aws.regiao:}") String regiao,
                                         @Value("${aws.dynamodb.endpoint:}") String endpoint) {
        var builder = DynamoDbClient.builder().httpClient(clienteHttpAws);
        if (!regiao.isBlank()) {
            builder.region(Region.of(regiao));
        }
        if (!endpoint.isBlank()) {
            builder.endpointOverride(URI.create(endpoint)).credentialsProvider(credencialLocal());
        }
        return builder.build();
    }

    private static AwsCredentialsProvider credencialLocal() {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create("local", "local"));
    }
}

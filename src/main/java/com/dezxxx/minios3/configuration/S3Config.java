package com.dezxxx.minios3.configuration;


import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

import java.net.URI;

@Configuration
@RequiredArgsConstructor

public class S3Config {

    private final S3Properties s3Properties;
@Bean
    public S3AsyncClient s3AsyncClient () {
        return S3AsyncClient.builder()
                .endpointOverride(URI.create(s3Properties.endpoint()))
                .region(Region.of(s3Properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(
                                s3Properties.accessKey(),
                                s3Properties.secretKey())))
                .forcePathStyle(s3Properties.pathStyleAccess())
                .build();
    }




}


package com.dezxxx.minios3.storage;

import com.dezxxx.minios3.configuration.S3Properties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

/**
 * Everything this application knows about talking to object storage.
 *
 * <p>Deliberately apart from FileService: the service decides what a file means and who
 * may touch it, this class only moves bytes. It also keeps the SDK out of the service's
 * unit tests, where mocking one method replaces a running MinIO.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class S3Storage {

    private final S3AsyncClient s3AsyncClient;
    private final S3Properties s3Properties;

    /**
     * Stores the bytes under {@code key}, replacing whatever was there before. Nothing
     * useful comes back: the caller invented the key and already knows it.
     *
     * <p>The whole file is in memory by the time it gets here. That is a deliberate
     * limit — S3 needs the content length before the first byte, and the alternatives
     * are a temporary file or a multipart upload. Neither is worth it at this size.
     */
    public Mono<Void> upload(String key, byte[] content, String contentType) {
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(s3Properties.bucket())
                .key(key)
                .contentType(contentType)
                .contentLength((long) content.length)
                .build();

        // A supplier, not a ready future: the upload has to start on subscribe. Call
        // putObject here and the bytes leave even for a Mono nobody ever subscribes to.
        return Mono.fromFuture(
                        () -> s3AsyncClient.putObject(request, AsyncRequestBody.fromBytes(content)))
                .doOnSuccess(response -> log.info("Stored {} ({} bytes) in bucket {}",
                        key, content.length, s3Properties.bucket()))
                .then();
    }
}

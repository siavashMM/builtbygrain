package com.builtbygrain.backend.storage;

import java.net.URI;
import java.util.Map;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Component
@ConditionalOnProperty(name = "app.object-storage.provider", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorage implements ObjectStorage {

    private final String bucket;
    private final boolean createBucket;
    private final S3Client client;

    public S3ObjectStorage(
        @Value("${app.object-storage.endpoint:}") String endpoint,
        @Value("${app.object-storage.region:eu-central-1}") String region,
        @Value("${app.object-storage.bucket:builtbygrain-media}") String bucket,
        @Value("${app.object-storage.access-key:}") String accessKey,
        @Value("${app.object-storage.secret-key:}") String secretKey,
        @Value("${app.object-storage.path-style:false}") boolean pathStyle,
        @Value("${app.object-storage.create-bucket:false}") boolean createBucket
    ) {
        this.bucket = bucket;
        this.createBucket = createBucket;
        var builder = S3Client.builder()
            .httpClientBuilder(UrlConnectionHttpClient.builder())
            .region(Region.of(region))
            .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(pathStyle).build());
        if (StringUtils.hasText(accessKey) && StringUtils.hasText(secretKey)) {
            builder.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)));
        } else {
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }
        if (StringUtils.hasText(endpoint)) {
            builder.endpointOverride(URI.create(endpoint));
        }
        this.client = builder.build();
    }

    @PostConstruct
    void createBucketWhenConfigured() {
        if (!createBucket) return;
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (S3Exception exception) {
            if (exception.statusCode() != 404) throw storageFailure("Object bucket could not be checked.", exception);
            client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object bucket could not be checked.", exception);
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType, String sha256) {
        try {
            client.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType)
                    .metadata(Map.of("sha256", sha256))
                    .build(),
                RequestBody.fromBytes(bytes)
            );
        } catch (S3Exception exception) {
            throw storageFailure("Object could not be stored.", exception);
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object could not be stored.", exception);
        }
    }

    @Override
    public ObjectData get(String key) {
        try {
            ResponseBytes<GetObjectResponse> response = client.getObject(
                GetObjectRequest.builder().bucket(bucket).key(key).build(),
                ResponseTransformer.toBytes()
            );
            GetObjectResponse metadata = response.response();
            return new ObjectData(
                response.asByteArray(),
                new ObjectMetadata(
                    metadata.contentLength(),
                    metadata.contentType(),
                    metadata.eTag(),
                    metadata.lastModified(),
                    metadata.metadata().get("sha256")
                )
            );
        } catch (NoSuchKeyException exception) {
            throw new ObjectStorageException("Object not found.", true);
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) throw new ObjectStorageException("Object not found.", true);
            throw storageFailure("Object could not be read.", exception);
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object could not be read.", exception);
        }
    }

    @Override
    public ObjectMetadata head(String key) {
        try {
            HeadObjectResponse metadata = client.headObject(
                HeadObjectRequest.builder().bucket(bucket).key(key).build()
            );
            return new ObjectMetadata(
                metadata.contentLength(),
                metadata.contentType(),
                metadata.eTag(),
                metadata.lastModified(),
                metadata.metadata().get("sha256")
            );
        } catch (S3Exception exception) {
            if (exception.statusCode() == 404) throw new ObjectStorageException("Object not found.", true);
            throw storageFailure("Object metadata could not be read.", exception);
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object metadata could not be read.", exception);
        }
    }

    @Override
    public void delete(String key) {
        try {
            client.deleteObject(request -> request.bucket(bucket).key(key));
        } catch (S3Exception exception) {
            throw storageFailure("Object could not be deleted.", exception);
        } catch (SdkException exception) {
            throw new ObjectStorageException("Object could not be deleted.", exception);
        }
    }

    @PreDestroy
    void close() {
        client.close();
    }

    private ObjectStorageException storageFailure(String message, S3Exception exception) {
        return new ObjectStorageException(message, exception);
    }
}

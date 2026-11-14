package vn.unicom.thi360.shared.storage;

import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.BucketAlreadyExistsException;
import software.amazon.awssdk.services.s3.model.BucketAlreadyOwnedByYouException;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;
import vn.unicom.thi360.shared.api.ExternalServiceException;

@Component
@ConditionalOnProperty(name = "thi360.s3.mode", havingValue = "s3", matchIfMissing = true)
public class S3ObjectStorage implements ObjectStorage {

    private static final Logger externalLog = LoggerFactory.getLogger("thi360.external");

    private final S3Properties properties;
    private final S3Client s3Client;
    private final String bucket;
    private final AtomicBoolean bucketEnsured = new AtomicBoolean(false);

    public S3ObjectStorage(S3Properties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        if (isBlank(properties.endpoint())
                || isBlank(properties.bucket())
                || isBlank(properties.accessKey())
                || isBlank(properties.secretKey())) {
            throw new IllegalStateException(
                    "S3 configuration (endpoint, bucket, access-key, secret-key) must be set when thi360.s3.mode=s3");
        }
        this.bucket = properties.bucket().trim();
        String region = isBlank(properties.region()) ? "us-east-1" : properties.region().trim();
        this.s3Client = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint().trim()))
                .forcePathStyle(true)
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(Duration.ofSeconds(30))
                        .apiCallAttemptTimeout(Duration.ofSeconds(15))
                        .build())
                .build();
    }

    @PreDestroy
    public void close() {
        s3Client.close();
    }

    @Override
    public void put(String key, byte[] content, String contentType) {
        Objects.requireNonNull(key, "key must not be null");
        Objects.requireNonNull(content, "content must not be null");
        long startNanos = System.nanoTime();
        try {
            ensureBucketIfConfigured();
            PutObjectRequest request = PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .contentType(contentType != null && !contentType.isBlank()
                            ? contentType
                            : "application/octet-stream")
                    .contentLength((long) content.length)
                    .build();
            s3Client.putObject(request, RequestBody.fromBytes(content));
            logExternal("put", key, "success", startNanos);
        } catch (SdkException ex) {
            logExternalFailure("put", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        }
    }

    @Override
    public Optional<byte[]> get(String key) {
        Objects.requireNonNull(key, "key must not be null");
        long startNanos = System.nanoTime();
        try {
            ensureBucketIfConfigured();
            ResponseBytes<GetObjectResponse> bytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(bucket).key(key).build());
            logExternal("get", key, "success", startNanos);
            return Optional.of(bytes.asByteArray());
        } catch (NoSuchKeyException ex) {
            logExternal("get", key, "not_found", startNanos);
            return Optional.empty();
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                logExternal("get", key, "not_found", startNanos);
                return Optional.empty();
            }
            logExternalFailure("get", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        } catch (SdkException ex) {
            logExternalFailure("get", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        }
    }

    @Override
    public void delete(String key) {
        Objects.requireNonNull(key, "key must not be null");
        long startNanos = System.nanoTime();
        try {
            ensureBucketIfConfigured();
            s3Client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
            logExternal("delete", key, "success", startNanos);
        } catch (NoSuchKeyException | NoSuchBucketException ex) {
            logExternal("delete", key, "not_found", startNanos);
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                logExternal("delete", key, "not_found", startNanos);
                return;
            }
            logExternalFailure("delete", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        } catch (SdkException ex) {
            logExternalFailure("delete", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        }
    }

    @Override
    public boolean exists(String key) {
        Objects.requireNonNull(key, "key must not be null");
        long startNanos = System.nanoTime();
        try {
            ensureBucketIfConfigured();
            s3Client.headObject(HeadObjectRequest.builder().bucket(bucket).key(key).build());
            logExternal("exists", key, "found", startNanos);
            return true;
        } catch (NoSuchKeyException ex) {
            logExternal("exists", key, "not_found", startNanos);
            return false;
        } catch (S3Exception ex) {
            if (ex.statusCode() == 404) {
                logExternal("exists", key, "not_found", startNanos);
                return false;
            }
            logExternalFailure("exists", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        } catch (SdkException ex) {
            logExternalFailure("exists", key, startNanos, ex);
            throw new ExternalServiceException("INTERNAL_ERROR", null, ex);
        }
    }

    private void ensureBucketIfConfigured() {
        if (!properties.autoCreateBucket() || bucketEnsured.get()) {
            return;
        }
        synchronized (bucketEnsured) {
            if (bucketEnsured.get()) {
                return;
            }
            long startNanos = System.nanoTime();
            try {
                s3Client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
                bucketEnsured.set(true);
            } catch (NoSuchBucketException ex) {
                createBucketIgnoreAlreadyExists();
                bucketEnsured.set(true);
                logExternal("createBucket", bucket, "created", startNanos);
            } catch (S3Exception ex) {
                if (ex.statusCode() == 404) {
                    createBucketIgnoreAlreadyExists();
                    bucketEnsured.set(true);
                    logExternal("createBucket", bucket, "created", startNanos);
                } else {
                    throw ex;
                }
            }
        }
    }

    private void createBucketIgnoreAlreadyExists() {
        try {
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucket).build());
        } catch (BucketAlreadyOwnedByYouException | BucketAlreadyExistsException ignored) {
            // Concurrent creation succeeded.
        }
    }

    private void logExternal(String operation, String key, String outcome, long startNanos) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        externalLog.atInfo()
                .addKeyValue("target", "s3")
                .addKeyValue("operation", operation)
                .addKeyValue("bucket", bucket)
                .addKeyValue("key", key)
                .addKeyValue("outcome", outcome)
                .addKeyValue("durationMs", durationMs)
                .log("S3 storage operation");
    }

    private void logExternalFailure(String operation, String key, long startNanos, SdkException ex) {
        long durationMs = (System.nanoTime() - startNanos) / 1_000_000L;
        externalLog.atWarn()
                .addKeyValue("target", "s3")
                .addKeyValue("operation", operation)
                .addKeyValue("bucket", bucket)
                .addKeyValue("key", key)
                .addKeyValue("outcome", "error")
                .addKeyValue("errorType", ex.getClass().getSimpleName())
                .addKeyValue("durationMs", durationMs)
                .log("S3 storage operation failed");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

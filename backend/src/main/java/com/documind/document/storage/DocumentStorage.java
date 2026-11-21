package com.documind.document.storage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;
import java.io.InputStream;
import java.net.URI;

@Component
public class DocumentStorage implements AutoCloseable {
 private final S3Client s3;
 private final String bucket;
 public DocumentStorage(@Value("${storage.endpoint}") String endpoint,
                        @Value("${storage.region}") String region,
                        @Value("${storage.bucket}") String bucket,
                        @Value("${storage.access-key}") String access,
                        @Value("${storage.secret-key}") String secret) {
  this.bucket=bucket;
  s3=S3Client.builder().endpointOverride(URI.create(endpoint))
      .region(software.amazon.awssdk.regions.Region.of(region))
      .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(access,secret)))
      .forcePathStyle(true).build();
 }
 public void put(String key,byte[] data,String type){
  s3.putObject(PutObjectRequest.builder().bucket(bucket).key(key).contentType(type).build(),RequestBody.fromBytes(data));
 }
 public byte[] get(String key){return s3.getObjectAsBytes(GetObjectRequest.builder().bucket(bucket).key(key).build()).asByteArray();}
 public void delete(String key){s3.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());}
 @Override public void close(){s3.close();}
}

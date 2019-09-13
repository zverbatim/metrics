package com.munteanu;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchAsyncClient;
import io.micrometer.cloudwatch.CloudWatchConfig;
import io.micrometer.cloudwatch.CloudWatchMeterRegistry;
import io.micrometer.core.instrument.Clock;
import io.micrometer.spring.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import io.micrometer.spring.autoconfigure.MetricsAutoConfiguration;
import io.micrometer.spring.autoconfigure.export.StringToDurationConverter;
import io.micrometer.spring.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cloud.aws.context.annotation.ConditionalOnAwsCloudEnvironment;
import org.springframework.cloud.aws.context.annotation.ConditionalOnMissingAmazonClient;
import org.springframework.cloud.aws.core.config.AmazonWebserviceClientFactoryBean;
import org.springframework.cloud.aws.core.credentials.CredentialsProviderFactoryBean;
import org.springframework.cloud.aws.core.region.RegionProvider;
import org.springframework.cloud.aws.core.region.StaticRegionProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for exporting metrics to a CloudWatch
 * under Spring Boot 1.5.
 *
 * TODO Upgrade to Spring Boot 2.x then replace these with the library
 * org.springframework.cloud:spring-cloud-aws-autoconfigure:[at least 2.0.0]
 * See https://stackoverflow.com/a/55844256/204205
 */
@Configuration
@AutoConfigureBefore({
    CompositeMeterRegistryAutoConfiguration.class,
    SimpleMetricsExportAutoConfiguration.class})
@AutoConfigureAfter(MetricsAutoConfiguration.class)
@ConditionalOnClass(
    value = {CloudWatchMeterRegistry.class},
    name = {"com.amazonaws.services.cloudwatch.AmazonCloudWatchAsync"}
)
//@ConditionalOnProperty(prefix = "management.metrics.export.cloudwatch", name = "enabled", havingValue = "true", matchIfMissing = true)
//@EnableConfigurationProperties(CloudWatchProperties.class)
@Import({StringToDurationConverter.class})
@ConditionalOnAwsCloudEnvironment
public class CloudWatchMetricsExportAutoConfiguration4SpringBoot1_5 {

    @Autowired
    private Environment springEnv;

    // This magic property should automatically ensure that AWS uses the right region
    // but it appears we lack some spring cloud libraries to do that
    @Value("${cloud.aws.region.static}") String awsRegion;

    private RegionProvider regionProvider() {
        String region = System.getenv("AWS_REGION");
        return new StaticRegionProvider((region != null) ? region : awsRegion);
    }

    /** Produce the bean needed by CloudWatchMeterRegistry */
    @Bean
    @ConditionalOnMissingAmazonClient(AmazonCloudWatchAsync.class)
    AmazonWebserviceClientFactoryBean<AmazonCloudWatchAsyncClient> amazonCloudWatchAsync(AWSCredentialsProvider credentialsProvider) {
        return new AmazonWebserviceClientFactoryBean<>(AmazonCloudWatchAsyncClient.class, credentialsProvider, regionProvider());
    }

    @Bean
    CredentialsProviderFactoryBean amazonCredentialsProvider() {
        return new CredentialsProviderFactoryBean();
    }

    @Bean
    @ConditionalOnMissingBean
    public CloudWatchConfig cloudWatchConfig() {
        return key -> springEnv.getProperty("management.metrics.export." + key);
    }

    @Bean
    @ConditionalOnMissingBean
    public CloudWatchMeterRegistry cloudWatchRegistry(CloudWatchConfig config, Clock clock, AmazonCloudWatchAsync amazonCloudWatchAsync) {
        return new CloudWatchMeterRegistry(config, clock, amazonCloudWatchAsync);
    }
}

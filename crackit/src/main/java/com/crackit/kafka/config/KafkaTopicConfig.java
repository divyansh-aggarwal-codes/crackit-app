package com.crackit.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@ConditionalOnProperty(name = "kafka.enabled", havingValue = "true", matchIfMissing = true)
public class KafkaTopicConfig {

    public static final String INTERVIEW_PREP_TOPIC = "crackit.interview.prep.requests";
    public static final String INTERVIEW_PREP_DLT_TOPIC = "crackit.interview.prep.requests-dlt";
    public static final String JOB_DISCOVERED_TOPIC = "crackit.job.discovered.events";

    @Bean
    public NewTopic interviewPrepTopic() {
        return TopicBuilder.name(INTERVIEW_PREP_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic interviewPrepDltTopic() {
        return TopicBuilder.name(INTERVIEW_PREP_DLT_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobDiscoveredTopic() {
        return TopicBuilder.name(JOB_DISCOVERED_TOPIC)
                .partitions(3)
                .replicas(1)
                .build();
    }
}

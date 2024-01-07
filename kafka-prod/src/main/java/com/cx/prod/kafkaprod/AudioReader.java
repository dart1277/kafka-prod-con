package com.cx.prod.kafkaprod;

import com.cx.prod.kafkaprod.audio.AudioCommand;
import io.confluent.kafka.serializers.KafkaAvroDeserializer;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Properties;

import static com.cx.prod.kafkaprod.AudioService.TOPIC_NAME;

@Slf4j
@RequiredArgsConstructor
@Service
public class AudioReader {

    @EventListener(ApplicationReadyEvent.class)
    public  void startApp() {

        final Properties props = new Properties();
        props.setProperty("bootstrap.servers", "localhost:29092");
        props.setProperty("group.id", "consumer-service-group-name-avro");
        //props.setProperty("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        props.setProperty("key.deserializer", StringDeserializer.class.getName());
        props.setProperty("value.deserializer", KafkaAvroDeserializer.class.getName());
        props.setProperty(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8085");
        props.setProperty(KafkaAvroDeserializerConfig.SPECIFIC_AVRO_READER_CONFIG, "true");
        props.setProperty(KafkaAvroDeserializerConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicRecordNameStrategy.class.getName());
        props.setProperty(ConsumerConfig.AUTO_COMMIT_INTERVAL_MS_CONFIG, "1000");
        props.setProperty(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest"); // read from beginning
        props.setProperty(ConsumerConfig.HEARTBEAT_INTERVAL_MS_CONFIG, "1000");
        props.setProperty(ConsumerConfig.SESSION_TIMEOUT_MS_CONFIG, "30000"); // timeout for heartbeat
        props.setProperty(ConsumerConfig.MAX_POLL_INTERVAL_MS_CONFIG, "5000"); // timeout for poll (secondary hearbeat)

        try( KafkaConsumer<Integer, AudioCommand> consumer = new KafkaConsumer<>(props)) {
            consumer.subscribe(List.of(TOPIC_NAME));

            while (true) {
                var records = consumer.poll(Duration.of(1, ChronoUnit.SECONDS)); //Duration.ofSeconds()

                for(var record: records) {
                    AudioCommand ac = record.value();
                    log.warn(ac.getType().toString());
                    log.warn(record.toString());
                }
            }
        }

    }
}

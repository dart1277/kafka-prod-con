package com.cx.consumer.kafkacon;

import com.cx.consumer.kafkacon.audio.AudioCommand;
import com.cx.consumer.kafkacon.audio.Type;
import io.confluent.kafka.serializers.*;
import io.confluent.kafka.serializers.KafkaAvroDeserializerConfig;
import io.confluent.kafka.serializers.subject.TopicRecordNameStrategy;
import io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicListing;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class AudioStreamsService {

    public static String REGULAR_AUDIO_TOPIC = "avro-audio-regular-command";
    public static String ADVANCED_AUDIO_TOPIC = "avro-audio-advanced-command";
    public static String AUDIO_TOPIC = "avro-audio-command";

    private Map<String, String> getSchemaConfig() {
        // https://docs.confluent.io/platform/current/schema-registry/develop/api.html#schemas
        return Map.of(KafkaAvroDeserializerConfig.SCHEMA_REGISTRY_URL_CONFIG, "http://localhost:8085",
                // http://localhost:8085/subjects
                KafkaAvroDeserializerConfig.VALUE_SUBJECT_NAME_STRATEGY, TopicRecordNameStrategy.class.getName(),
                AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, "false",
                KafkaAvroSerializerConfig.AVRO_REMOVE_JAVA_PROPS_CONFIG, "true"
        );
    }

    @EventListener(ApplicationReadyEvent.class)
    public void streamsInit() {

        List<String> topicNames = List.of(AUDIO_TOPIC, REGULAR_AUDIO_TOPIC, ADVANCED_AUDIO_TOPIC);
        createTopics(topicNames);

        Properties props = new Properties();
        // https://kafka.apache.org/21/documentation/streams/developer-guide/config-streams.html#acks
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "avro-audio-streams-app");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:29092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.String().getClass());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, SpecificAvroSerde.class);
        props.put(KafkaAvroSerializerConfig.AVRO_REMOVE_JAVA_PROPS_CONFIG, "true");
        props.put( AbstractKafkaSchemaSerDeConfig.AUTO_REGISTER_SCHEMAS, "false");
//        props.put(StreamsConfig.REPLICATION_FACTOR_CONFIG, 3);
//        props.put(StreamsConfig.topicPrefix(TopicConfig.MIN_IN_SYNC_REPLICAS_CONFIG), 2);
//        props.put(StreamsConfig.producerPrefix(ProducerConfig.ACKS_CONFIG), "all");
        props.putAll(getSchemaConfig());

        final StreamsBuilder builder = new StreamsBuilder();

        // https://docs.confluent.io/platform/current/streams/developer-guide/datatypes.html#avro-primitive
        // final Map<String, String> serdeConfig = Collections.singletonMap("schema.registry.url",
        //         "http://my-schema-registry:8081");
        // // `Foo` and `Bar` are Java classes generated from Avro schemas
        // final Serde<Foo> keySpecificAvroSerde = new SpecificAvroSerde<>();
        // keySpecificAvroSerde.configure(serdeConfig, true); // `true` for record keys
        // final Serde<Bar> valueSpecificAvroSerde = new SpecificAvroSerde<>();
        // valueSpecificAvroSerde.configure(serdeConfig, false); // `false` for record values

        // StreamsBuilder builder = new StreamsBuilder();
        // KStream<Foo, Bar> textLines = builder.stream("my-avro-topic",
        //         Consumed.with(keySpecificAvroSerde, valueSpecificAvroSerde));

        buildStream(builder);

        final Topology topology = builder.build();

        final KafkaStreams streams = new KafkaStreams(topology, props);
        final CountDownLatch latch = new CountDownLatch(1);

        // attach shutdown handler to catch control-c
        Runtime.getRuntime().addShutdownHook(new Thread("streams-shutdown-hook") {
            @Override
            public void run() {
                streams.close();
                latch.countDown();
            }
        });

        try {
            streams.start();
            latch.await();
        } catch (Throwable e) {
            System.exit(1);
        }
        System.exit(0);
    }

    private void createTopics(List<String> topicNames) {
        try (Admin admin = Admin.create(Map.of(
                "bootstrap.servers", "localhost:29092"))) {
            final Map<String, TopicListing> topicListings = admin.listTopics().namesToListings().get();

            log.warn(topicListings.toString());

            Set<String> topicNameSet = new HashSet<>(topicNames);
            topicNameSet.removeAll(topicListings.keySet());
            topicNames = List.of(topicNameSet.toArray(new String[]{}));

            for (String topicName : topicNames) {

                //if (!topicListings.containsKey(REGULAR_AUDIO_TOPIC)) {
                final NewTopic newTopic = new NewTopic(topicName, 2, (short) 1)
                        .configs(Map.of("min.insync.replicas", "1",
                                "segment.bytes", "573741824",
                                "segment.ms", "86400000",
                                "retention.bytes", "1073741824",
                                "retention.ms", "604800000"));
                // final NewTopic newTopic = new NewTopic(REGULAR_AUDIO_TOPIC, Optional.empty(),
                // Optional.empty());
                admin.createTopics(List.of(newTopic)).all();
//                } else {
//                    System.out.println(topicListings);
//                }
                System.out.println(admin.describeTopics(List.of(topicName)).allTopicNames().get());
            }

        } catch (InterruptedException | ExecutionException ex) {

        }
    }

    private void buildStream(final StreamsBuilder builder) {

        try (final Serde<AudioCommand> valueSpecificAvroSerde = new SpecificAvroSerde<>()) {
            valueSpecificAvroSerde.configure(getSchemaConfig(), false); // `false` for record values

            builder.stream(AUDIO_TOPIC, Consumed.with(Serdes.String(), valueSpecificAvroSerde))
                    .filter((k, v) -> v.getType() == Type.REGULAR)
                    .peek((k, v) -> {
                        log.warn("Item REGULAR : ");
                        log.warn(k);
                        log.warn(v.toString());
                    })
                    .to(REGULAR_AUDIO_TOPIC);

            builder.stream(AUDIO_TOPIC, Consumed.with(Serdes.String(), valueSpecificAvroSerde))
                    .filter((k, v) -> v.getType() == Type.ADVANCED)
                    .peek((k, v) -> {
                        log.warn("Item ADVANCED: ");
                        log.warn(k);
                        log.warn(v.toString());
                    })
                    .to(ADVANCED_AUDIO_TOPIC);

            builder.stream(AUDIO_TOPIC, Consumed.with(Serdes.String(), valueSpecificAvroSerde))
                    .peek((k, v) -> {
                        log.warn("Item ALL: ");
                        log.warn(k);
                        log.warn(v.toString());
                    })
                    .to(REGULAR_AUDIO_TOPIC);
        }
    }

}

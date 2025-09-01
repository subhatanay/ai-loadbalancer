package com.bits.commomutil.models;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import lombok.Data;

@Data
@Configuration
@ConfigurationProperties(prefix = "app")
public class AppProperties {
    private Service service = new Service();
    private Cache cache = new Cache();
    private Database database = new Database();
    private Messaging messaging = new Messaging();

    @Data
    public static class Service {
        private String name;
        private String host = "localhost";
        private int port;
        private Registration registration = new Registration();

        @Data
        public static class Registration {
            private long ttlSeconds = 30;
            private long refreshIntervalMs = 20000;
        }
    }

    @Data
    public static class Cache {
        private String host = "localhost";
        private int port = 6379;
        private int database = 0;
        private long ttlMinutes = 60;
    }

    @Data
    public static class Database {
        private String url;
        private String username;
        private String password;
        private String driverClassName;
    }

    @Data
    public static class Messaging {
        private String brokers = "localhost:9092";
        private String consumerGroup;
    }
}


#!/bin/bash

# Add simple builder methods to remaining event classes

# NotificationSentEvent
cat >> src/main/java/com/bits/notification/event/NotificationSentEvent.java << 'EOF'

    // Manual builder method
    public static NotificationSentEventBuilder builder() {
        return new NotificationSentEventBuilder();
    }
    
    public static class NotificationSentEventBuilder {
        private String notificationId;
        private String userId;
        private List<NotificationChannel> channels;
        private LocalDateTime timestamp;
        
        public NotificationSentEventBuilder notificationId(String notificationId) { this.notificationId = notificationId; return this; }
        public NotificationSentEventBuilder userId(String userId) { this.userId = userId; return this; }
        public NotificationSentEventBuilder channels(List<NotificationChannel> channels) { this.channels = channels; return this; }
        public NotificationSentEventBuilder timestamp(LocalDateTime timestamp) { this.timestamp = timestamp; return this; }
        
        public NotificationSentEvent build() {
            NotificationSentEvent event = new NotificationSentEvent();
            event.notificationId = this.notificationId;
            event.userId = this.userId;
            event.channels = this.channels;
            event.timestamp = this.timestamp;
            return event;
        }
    }
EOF

# Remove the closing brace and add builder
sed -i '' '$ d' src/main/java/com/bits/notification/event/NotificationSentEvent.java

echo "}" >> src/main/java/com/bits/notification/event/NotificationSentEvent.java

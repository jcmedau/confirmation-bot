package com.skypower.confirmation_bot.whatsapp;

// No @JsonIgnoreProperties needed — spring.jackson.deserialization.fail-on-unknown-properties=false
// is already set globally in application.properties.

public class WebhookPayload {

    private String event;
    private String instance;
    private Data data;

    public String getEvent() { return event; }
    public void setEvent(String event) { this.event = event; }

    public String getInstance() { return instance; }
    public void setInstance(String instance) { this.instance = instance; }

    public Data getData() { return data; }
    public void setData(Data data) { this.data = data; }

    public static class Data {
        private Key key;
        private Message message;
        private String pushName;

        public Key getKey() { return key; }
        public void setKey(Key key) { this.key = key; }

        public Message getMessage() { return message; }
        public void setMessage(Message message) { this.message = message; }

        public String getPushName() { return pushName; }
        public void setPushName(String pushName) { this.pushName = pushName; }
    }

    public static class Key {
        private String remoteJid;
        private boolean fromMe;

        public String getRemoteJid() { return remoteJid; }
        public void setRemoteJid(String remoteJid) { this.remoteJid = remoteJid; }

        public boolean isFromMe() { return fromMe; }
        public void setFromMe(boolean fromMe) { this.fromMe = fromMe; }
    }

    public static class Message {
        // Simple text message
        private String conversation;

        // Quoted reply — WhatsApp sends this when the user replies to a previous message
        private ExtendedTextMessage extendedTextMessage;

        public String getConversation() { return conversation; }
        public void setConversation(String conversation) { this.conversation = conversation; }

        public ExtendedTextMessage getExtendedTextMessage() { return extendedTextMessage; }
        public void setExtendedTextMessage(ExtendedTextMessage e) { this.extendedTextMessage = e; }

        /**
         * Returns the message text regardless of whether it arrived as a plain
         * conversation or as a quoted/extended text message.
         */
        public String getText() {
            if (conversation != null && !conversation.isBlank()) return conversation;
            if (extendedTextMessage != null && extendedTextMessage.getText() != null
                    && !extendedTextMessage.getText().isBlank()) {
                return extendedTextMessage.getText();
            }
            return null;
        }
    }

    public static class ExtendedTextMessage {
        private String text;

        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
    }
}

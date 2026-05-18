package com.FriendsChatMonitor;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.util.HashMap;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import net.runelite.api.events.ChatMessage;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.util.Text;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import java.util.Map;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@PluginDescriptor(
    name = "Friends Chat Monitor",
    description = "Forwards Friends Chat notifications to a Discord SaaS with deduplication",
    tags = {"friends chat", "discord", "loot", "saas"}
)
public class FriendsChatMonitorPlugin extends Plugin
{
    @Inject
    private Client client;

    @Inject
    private FriendsChatMonitorConfig config;

    @Inject
    private OkHttpClient okHttpClient;

    @Inject
    private Gson gson;

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
    
    // Replace with your actual Cloudflare Worker URL when deployed
    private static final String API_ENDPOINT = "https://friends-chat-monitor-cloudflare-worker.nhwhite3118.workers.dev/ingest";

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss")
            .withZone(ZoneId.of("UTC"));

    @Provides
    FriendsChatMonitorConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(FriendsChatMonitorConfig.class);
    }

    @Subscribe
    public void onChatMessage(ChatMessage event)
    {
        // Filter for Friends Chat notification
        if (config.enabled() && event.getType() == ChatMessageType.FRIENDSCHAT)
        {
            String monitorName = config.friendsChatName();
            if (monitorName.isEmpty())
            {
                log.debug("Friends Chat name not configured");
                return;
            }
            
            // Check that Friends Chat Name matches
            net.runelite.api.FriendsChatManager friendsChatManager = client.getFriendsChatManager();
            if (friendsChatManager == null || 
                !Text.standardize(friendsChatManager.getOwner()).equalsIgnoreCase(Text.standardize(monitorName)))
            {
                return;
            }
            Instant time = Instant.ofEpochSecond(event.getTimestamp());
            String formattedTime = TIME_FORMATTER.format(time);
            
            if (config.useDeduplicationService()) {
                sendToSaaS(sanitizeName(event.getName()), sanitizeMessage(event.getMessage()), formattedTime);
            } else {
                sendToSingleUserWebhook(sanitizeName(event.getName()), sanitizeMessage(event.getMessage()), formattedTime);
            }
        }
    }

    private String sanitizeName(String name)
    {
        if (name == null)
        {
            return "";
        }

        // Replace common image tags with readable text labels
        String sanitized = name
            .replace("<img=0>", "[PMod]")
            .replace("<img=1>", "[JMod]")
            .replace("<img=2>", "[Iron]")
            .replace("<img=3>", "[UIM]")
            .replace("<img=10>", "[HCIM]");

        // Remove any remaining tags (like colors) and fix non-breaking spaces
        return Text.removeTags(sanitized).replace('\u00A0', ' ').trim();
    }

    private String sanitizeMessage(String message)
    {
        if (message == null)
        {
            return "";
        }
        // Remove chat formatting tags and fix non-breaking spaces for consistent hashing
        return Text.removeTags(message).replace('\u00A0', ' ').trim();
    }

    private void sendToSaaS(String author, String content, String clientTimestamp)
    {
        if (config.apiKey().isEmpty())
        {
            log.warn("FriendsChatMonitor: SaaS API Key is not configured.");
            return;
        }
        if (!config.useDeduplicationService()) {
            // This method should only be called if the deduplication service is enabled
            return; 
        }

        Map<String, String> data = new HashMap<>();
        data.put("author", author);
        data.put("clientTimestamp", clientTimestamp);
        data.put("content", content);

        Request request = new Request.Builder()
            .url(API_ENDPOINT)
            .addHeader("X-SaaS-Token", config.apiKey())
            .post(RequestBody.create(JSON, gson.toJson(data)))
            .build();

        // Async execution ensures the game thread isn't blocked by the HTTP request
        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("FriendsChatMonitor: Failed to send data to SaaS", e);
            }


            @Override
            public void onResponse(Call call, Response response) throws IOException
            {
                response.close();
            }
        });
    }

    private void sendToSingleUserWebhook(String author, String content, String clientTimestamp)
    {
        String webhookUrl = config.singleUserWebhookUrl();
        if (webhookUrl.isEmpty())
        {
            log.warn("FriendsChatMonitor: Discord Webhook URL is not configured for single-user mode.");
            return;
        }

        Map<String, String> data = new HashMap<>(); // Use clientTimestamp for display in the free mode webhook
        data.put("content", String.format("[%s UTC] **%s**: %s", clientTimestamp, author, content));

        Request request = new Request.Builder()
                .url(webhookUrl)
                .post(RequestBody.create(JSON, gson.toJson(data)))
                .build();

        okHttpClient.newCall(request).enqueue(new Callback()
        {
            @Override
            public void onFailure(Call call, IOException e)
            {
                log.warn("FriendsChatMonitor: Failed to send message to single-user Discord webhook", e);
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException { response.close(); }
        });
    }
}
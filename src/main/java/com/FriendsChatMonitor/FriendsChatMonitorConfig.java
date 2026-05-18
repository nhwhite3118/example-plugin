package com.FriendsChatMonitor;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("friendschatmonitor")
public interface FriendsChatMonitorConfig extends Config
{
    @ConfigSection(
            name = "General Settings",
            description = "General plugin settings",
            position = 0
    )
    String generalSettings = "generalSettings";

    @ConfigSection(
            name = "Deduplication Service (Paid)",
            description = "Settings for the multi-user deduplication service",
            position = 1
    )
    String deduplicationServiceSettings = "deduplicationServiceSettings";

    @ConfigSection(
            name = "Single User Webhook (Free)",
            description = "Settings for direct Discord webhook (no deduplication)",
            position = 2
    )
    String singleUserWebhookSettings = "singleUserWebhookSettings";

    @ConfigItem(
        keyName = "enabled",
        name = "Enable Monitoring",
        description = "Toggle the monitoring and forwarding of Friends Chat messages",
        position = 0,
        section = generalSettings
    )
    default boolean enabled() { return false; }

    @ConfigItem(
            keyName = "useDeduplicationService",
            name = "Use Deduplication Service",
            description = "Toggle to use the paid deduplication service or a free single-user webhook.",
            position = 1,
            section = generalSettings,
            warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
    )
    default boolean useDeduplicationService() { return false; } // Default to free mode

    @ConfigItem(
        keyName = "apiKey",
        name = "SaaS API Key",
        description = "Your unique API key from the dashboard for the deduplication service",
        position = 0,
        section = deduplicationServiceSettings,
        secret = true
    )
    default String apiKey() { return ""; }

    @ConfigItem(
        keyName = "friendsChatName",
        name = "Friends Chat Name",
        description = "The username of the friends chat owner to monitor",
        position = 1,
        section = generalSettings
    )
    default String friendsChatName() { return ""; }

    @ConfigItem(
            keyName = "singleUserWebhookUrl",
            name = "Discord Webhook URL",
            description = "Your Discord webhook URL for direct message forwarding (no deduplication)",
            position = 0,
            section = singleUserWebhookSettings,
            secret = true
    )
    default String singleUserWebhookUrl() { return ""; }
}
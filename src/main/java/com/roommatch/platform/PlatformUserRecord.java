package com.roommatch.platform;

import com.google.gson.annotations.SerializedName;
import java.util.Map;

/**
 * A user exactly as the housing platform describes them. Field names, types and habit keys
 * are the platform's own, deliberately different from RoomMatch's model, so the adapter has
 * real translation work to do.
 */
public class PlatformUserRecord {
    public static final String STATUS_ACTIVE = "ACTIVE";

    @SerializedName("user_id") private String userId;
    private String city;
    @SerializedName("rent_max") private Double rentMax;
    @SerializedName("available_from") private String availableFrom;
    private String status;
    private Map<String, String> habits;

    /** Needed by Gson. */
    private PlatformUserRecord() { }

    public PlatformUserRecord(String userId, String city, Double rentMax, String availableFrom,
                              String status, Map<String, String> habits) {
        this.userId = userId;
        this.city = city;
        this.rentMax = rentMax;
        this.availableFrom = availableFrom;
        this.status = status;
        this.habits = habits;
    }

    public String getUserId() { return userId; }
    public String getCity() { return city; }
    public Double getRentMax() { return rentMax; }
    public String getAvailableFrom() { return availableFrom; }
    public String getStatus() { return status; }
    public Map<String, String> getHabits() { return habits == null ? Map.of() : habits; }
    public boolean isActive() { return STATUS_ACTIVE.equalsIgnoreCase(status); }
}

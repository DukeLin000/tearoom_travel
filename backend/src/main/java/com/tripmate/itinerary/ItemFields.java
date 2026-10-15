package com.tripmate.itinerary;

import com.tripmate.shared.ApiException;
import com.tripmate.shared.Money;
import jakarta.validation.constraints.*;
import java.net.URI;
import java.time.LocalTime;

public record ItemFields(@NotBlank @Size(max=160) String title,
    @NotNull @Pattern(regexp="FOOD|TRANSPORT|STAY|ACTIVITY|SHOPPING|OTHER") String category,
    LocalTime localStartTime, LocalTime localEndTime,
    @Size(max=255) String locationName, @Size(max=2048) String mapUrl,
    @NotNull String estimatedAmount, @NotNull @Size(max=10000) String note) {
    public void validateDomain() {
        Money.parse(estimatedAmount);
        if (localStartTime != null && localEndTime != null && !localEndTime.isAfter(localStartTime)) {
            throw ApiException.invalid("Activity end time must be later than start time on the same day");
        }
        if (mapUrl != null && !mapUrl.isBlank()) {
            try {
                URI url=URI.create(mapUrl);
                if (!("https".equals(url.getScheme()) || "http".equals(url.getScheme())) || url.getHost()==null || url.getUserInfo()!=null) {
                    throw ApiException.invalid("Map link must be an absolute http or https URL without credentials");
                }
            } catch (IllegalArgumentException exception) { throw ApiException.invalid("Invalid map URL"); }
        }
    }
}

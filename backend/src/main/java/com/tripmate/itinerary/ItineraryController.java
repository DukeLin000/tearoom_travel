package com.tripmate.itinerary;

import com.fasterxml.jackson.databind.JsonNode;
import com.tripmate.identity.User;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.net.URI;
import java.time.LocalTime;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/trips/{tripId}")
public class ItineraryController {
    public record CreateItem(@NotNull UUID dayId, String title, String category, LocalTime localStartTime, LocalTime localEndTime,
        String locationName, String mapUrl, String estimatedAmount, String note) {
        ItemFields fields() { return new ItemFields(title,category,localStartTime,localEndTime,locationName,mapUrl,estimatedAmount,note == null ? "" : note); }
    }
    private final ItineraryService items;
    public ItineraryController(ItineraryService items) { this.items=items; }
    @GetMapping("/schedule")
    public ItineraryService.Schedule schedule(@PathVariable UUID tripId,@AuthenticationPrincipal User user) { return items.schedule(tripId,user.id()); }
    @PostMapping("/items")
    public ResponseEntity<Item> create(@PathVariable UUID tripId,@Valid @RequestBody CreateItem input,@AuthenticationPrincipal User user) {
        Item item=items.create(tripId,input.dayId(),input.fields(),user.id());
        return ResponseEntity.created(URI.create("/api/v1/trips/"+tripId+"/items/"+item.id())).body(item);
    }
    @PatchMapping("/items/{itemId}")
    public Item patch(@PathVariable UUID tripId,@PathVariable UUID itemId,@RequestBody JsonNode patch,@AuthenticationPrincipal User user) {
        return items.patch(tripId,itemId,patch,user.id());
    }
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<Void> delete(@PathVariable UUID tripId,@PathVariable UUID itemId,
        @RequestParam @PositiveOrZero long expectedVersion,@AuthenticationPrincipal User user) {
        items.delete(tripId,itemId,expectedVersion,user.id());
        return ResponseEntity.noContent().build();
    }
}

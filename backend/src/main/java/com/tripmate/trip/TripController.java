package com.tripmate.trip;

import com.tripmate.identity.User;
import com.tripmate.membership.MembershipService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.net.URI;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping("/api/v1/trips")
public class TripController {
    private final TripService trips;
    private final MembershipService memberships;
    public TripController(TripService trips, MembershipService memberships) { this.trips=trips; this.memberships=memberships; }
    @PostMapping
    public ResponseEntity<TripService.Trip> create(@Valid @RequestBody TripService.CreateTrip input, @AuthenticationPrincipal User user) {
        var trip=trips.create(input,user.id());
        return ResponseEntity.created(URI.create("/api/v1/trips/"+trip.id())).body(trip);
    }
    @GetMapping
    public TripService.TripPage list(@AuthenticationPrincipal User user,
        @RequestParam(defaultValue="0") @Min(0) int page,
        @RequestParam(defaultValue="20") @Min(1) @Max(100) int size) {
        return trips.list(user.id(),page,size);
    }
    @GetMapping("/{tripId}")
    public TripService.Trip get(@PathVariable UUID tripId, @AuthenticationPrincipal User user) { return trips.get(tripId,user.id()); }
    @GetMapping("/{tripId}/members")
    public List<User> members(@PathVariable UUID tripId, @AuthenticationPrincipal User user) { return memberships.members(tripId,user.id()); }
}

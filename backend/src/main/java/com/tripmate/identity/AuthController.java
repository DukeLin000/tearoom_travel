package com.tripmate.identity;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {
    private final IdentityService identities;
    private final SecurityContextRepository contexts;
    public AuthController(IdentityService identities, SecurityContextRepository contexts) {
        this.identities = identities; this.contexts = contexts;
    }
    public record DevLogin(@NotBlank @Pattern(regexp="[a-zA-Z0-9_-]{1,40}") String subject,
        @NotBlank @Size(max=80) String displayName) {}
    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("token", token.getToken(), "headerName", token.getHeaderName());
    }
    @PostMapping("/dev-login")
    public User login(@Valid @RequestBody DevLogin body, HttpServletRequest request, HttpServletResponse response) {
        User user = identities.devUser(body.subject(), body.displayName().trim());
        if (request.getSession(false) != null) request.changeSessionId();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(user, null, List.of()));
        SecurityContextHolder.setContext(context);
        contexts.saveContext(context, request, response);
        return user;
    }
    @GetMapping("/me")
    public User me(@AuthenticationPrincipal User user) { return user; }
}

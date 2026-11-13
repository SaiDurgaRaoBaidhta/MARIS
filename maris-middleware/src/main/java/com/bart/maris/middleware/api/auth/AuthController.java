package com.bart.maris.middleware.api.auth;

import com.bart.maris.middleware.domain.auth.SignonResult;
import com.bart.maris.middleware.domain.auth.SignonService;
import com.bart.maris.middleware.domain.auth.UiRoles;
import com.bart.maris.middleware.security.MarisUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Sign-on")
public class AuthController {
    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final SignonService signonService;
    private final SecurityContextRepository contextRepository;

    public AuthController(SignonService signonService, SecurityContextRepository contextRepository) {
        this.signonService = signonService;
        this.contextRepository = contextRepository;
    }

    /** Field sizes come from MRS02A_CRIT: USER_GRP char(8), USER_PASSWORD char(6). */
    public record SignonRequest(@NotBlank @Size(max = 8) String groupId, @NotBlank @Size(max = 6) String password) {}

    public record CsrfResponse(String headerName, String token) {}

    @GetMapping("/csrf")
    @Operation(summary = "Get the CSRF token (also set as the XSRF-TOKEN cookie). Call once before signing on.")
    public CsrfResponse csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return token == null ? new CsrfResponse(null, null) : new CsrfResponse(token.getHeaderName(), token.getToken());
    }

    @PostMapping("/signon")
    @Operation(summary = "Sign on with MARIS group ID and password (legacy program MRS02A). Returns the UI session.")
    public ResponseEntity<?> signon(@Valid @RequestBody SignonRequest body, HttpServletRequest request,
                                    HttpServletResponse response) {
        SignonResult r = signonService.signon(body.groupId(), body.password());
        if (r.status() != SignonResult.Status.VALID) {
            log.info("sign-on refused group={} reason={}", r.groupId(), r.status());
            ProblemDetail p = ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Group ID or password is incorrect.");
            p.setProperty("code", "SIGNON_FAILED");
            p.setProperty("requestId", org.slf4j.MDC.get(com.bart.maris.middleware.config.RequestIdFilter.MDC_KEY));
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(p);
        }
        MarisUser user = new MarisUser(r.groupId(), r.groupDescription(), r.menu(), r.userType(), r.yard(),
                r.incidentPrefix(), UiRoles.role(r.groupId(), r.userType(), r.menu()), Instant.now());
        Authentication auth = UsernamePasswordAuthenticationToken.authenticated(user, null,
                List.of(new SimpleGrantedAuthority("ROLE_MARIS_USER")));
        if (request.getSession(false) != null) request.changeSessionId();   // no session fixation
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepository.saveContext(ctx, request, response);
        log.info("sign-on ok group={} menu={} role={}", user.groupId(), user.menu(), user.role());
        return ResponseEntity.ok(UiSession.of(user));
    }

    @PostMapping("/signoff")
    @Operation(summary = "Sign off and end the session")
    public ResponseEntity<Void> signoff(HttpServletRequest request, HttpServletResponse response, Authentication auth) {
        new SecurityContextLogoutHandler().logout(request, response, auth);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    @Operation(summary = "The signed-on user group (lets the UI restore its session after a page reload)")
    public UiSession me(@AuthenticationPrincipal MarisUser user) {
        return UiSession.of(user);
    }
}

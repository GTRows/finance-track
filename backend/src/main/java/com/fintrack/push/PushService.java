package com.fintrack.push;

import com.fintrack.common.entity.PushSubscription;
import io.jsonwebtoken.Jwts;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Sends Web Push messages via VAPID. This implementation uses empty-body push
 * (no payload encryption): the service worker on the client is expected to
 * fetch notification details from the backend when woken up. Keeping the body
 * empty avoids the ECIES/AES-128-GCM payload encryption machinery while still
 * giving reliable wake-ups.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PushService {

    private static final Duration JWT_TTL = Duration.ofHours(12);

    private final PushSubscriptionRepository repo;
    private final VapidKeyManager vapid;
    private final PushProperties props;
    private final WebClient webClient = WebClient.builder().build();

    @Transactional
    public PushSubscription subscribe(UUID userId, String endpoint, String p256dh,
                                       String auth, String userAgent) {
        PushSubscription sub = repo.findByEndpoint(endpoint).orElseGet(PushSubscription::new);
        sub.setUserId(userId);
        sub.setEndpoint(endpoint);
        sub.setP256dh(p256dh);
        sub.setAuth(auth);
        sub.setUserAgent(userAgent);
        PushSubscription saved = repo.save(sub);
        log.info("Push subscription stored for user={} endpoint={}", userId, trim(endpoint));
        return saved;
    }

    @Transactional
    public void unsubscribe(UUID userId, String endpoint) {
        repo.findByEndpoint(endpoint)
                .filter(s -> s.getUserId().equals(userId))
                .ifPresent(repo::delete);
    }

    @Transactional(readOnly = true)
    public List<PushSubscription> subscriptionsFor(UUID userId) {
        return repo.findByUserId(userId);
    }

    /**
     * Fire a wake-up to every device the user has registered. Returns the count
     * of subscriptions that accepted the push; the remainder were either stale
     * (410/404 -> deleted) or failed transiently (logged).
     */
    @Transactional
    public int sendToUser(UUID userId) {
        List<PushSubscription> subs = repo.findByUserId(userId);
        int ok = 0;
        for (PushSubscription sub : subs) {
            if (send(sub)) ok++;
        }
        return ok;
    }

    private boolean send(PushSubscription sub) {
        String audience = audienceOf(sub.getEndpoint());
        String token = signVapidJwt(audience);

        try {
            webClient.post()
                    .uri(sub.getEndpoint())
                    .header("Authorization", "vapid t=" + token + ", k=" + vapid.getPublicKeyB64Url())
                    .header("TTL", "3600")
                    .header("Urgency", "normal")
                    .header("Content-Length", "0")
                    .retrieve()
                    .toBodilessEntity()
                    .block(Duration.ofSeconds(15));
            return true;
        } catch (WebClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                log.info("Push endpoint gone ({} {}), removing subscription id={}",
                        status, e.getStatusText(), sub.getId());
                repo.delete(sub);
            } else {
                log.warn("Push delivery failed endpoint={} status={} body={}",
                        trim(sub.getEndpoint()), status, e.getResponseBodyAsString());
            }
            return false;
        } catch (Exception e) {
            log.warn("Push delivery error endpoint={}: {}", trim(sub.getEndpoint()), e.getMessage());
            return false;
        }
    }

    private String signVapidJwt(String audience) {
        String subject = (props.subject() == null || props.subject().isBlank())
                ? "mailto:admin@fintrack.local"
                : props.subject();
        Instant now = Instant.now();
        return Jwts.builder()
                .header().add("typ", "JWT").and()
                .claims(Map.of(
                        "aud", audience,
                        "exp", now.plus(JWT_TTL).getEpochSecond(),
                        "sub", subject))
                .issuedAt(Date.from(now))
                .signWith(vapid.keyPair().getPrivate(), Jwts.SIG.ES256)
                .compact();
    }

    private static String audienceOf(String endpoint) {
        URI uri = URI.create(endpoint);
        String port = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return uri.getScheme() + "://" + uri.getHost() + port;
    }

    private static String trim(String endpoint) {
        if (endpoint == null) return "";
        return endpoint.length() <= 60 ? endpoint : endpoint.substring(0, 60) + "...";
    }
}

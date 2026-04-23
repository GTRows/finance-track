package com.fintrack.push;

import com.fintrack.common.entity.PushSubscription;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushServiceTest {

    @Mock PushSubscriptionRepository repo;
    @Mock VapidKeyManager vapid;
    @Mock PushProperties props;

    @InjectMocks PushService service;

    private final UUID userId = UUID.randomUUID();

    @BeforeEach
    void stubSave() {
        // Save is used in subscribe; stubbed lenient-style via doAnswer to give back the arg.
    }

    private PushSubscription subscription(UUID user, String endpoint) {
        PushSubscription s = new PushSubscription();
        s.setId(UUID.randomUUID());
        s.setUserId(user);
        s.setEndpoint(endpoint);
        return s;
    }

    @Test
    void subscribeCreatesNewRowWhenEndpointUnknown() {
        when(repo.findByEndpoint("e1")).thenReturn(Optional.empty());
        when(repo.save(any(PushSubscription.class))).thenAnswer(inv -> inv.getArgument(0));

        PushSubscription saved = service.subscribe(userId, "e1", "pk", "auth", "Mozilla");

        ArgumentCaptor<PushSubscription> captor = ArgumentCaptor.forClass(PushSubscription.class);
        verify(repo).save(captor.capture());
        PushSubscription persisted = captor.getValue();
        assertThat(persisted.getUserId()).isEqualTo(userId);
        assertThat(persisted.getEndpoint()).isEqualTo("e1");
        assertThat(persisted.getP256dh()).isEqualTo("pk");
        assertThat(persisted.getAuth()).isEqualTo("auth");
        assertThat(persisted.getUserAgent()).isEqualTo("Mozilla");
        assertThat(saved.getEndpoint()).isEqualTo("e1");
    }

    @Test
    void subscribeUpdatesKeysOnExistingEndpoint() {
        PushSubscription existing = subscription(userId, "e1");
        existing.setP256dh("old");
        when(repo.findByEndpoint("e1")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        service.subscribe(userId, "e1", "new-pk", "new-auth", "Firefox");

        assertThat(existing.getP256dh()).isEqualTo("new-pk");
        assertThat(existing.getAuth()).isEqualTo("new-auth");
        assertThat(existing.getUserAgent()).isEqualTo("Firefox");
    }

    @Test
    void subscribeReassignsEndpointToDifferentUser() {
        UUID newUser = UUID.randomUUID();
        PushSubscription existing = subscription(userId, "e1");
        when(repo.findByEndpoint("e1")).thenReturn(Optional.of(existing));
        when(repo.save(existing)).thenReturn(existing);

        service.subscribe(newUser, "e1", "pk", "auth", "UA");

        assertThat(existing.getUserId()).isEqualTo(newUser);
    }

    @Test
    void unsubscribeRemovesWhenEndpointOwnedByUser() {
        PushSubscription sub = subscription(userId, "e1");
        when(repo.findByEndpoint("e1")).thenReturn(Optional.of(sub));

        service.unsubscribe(userId, "e1");

        verify(repo).delete(sub);
    }

    @Test
    void unsubscribeIsNoOpWhenEndpointOwnedByDifferentUser() {
        UUID otherUser = UUID.randomUUID();
        PushSubscription sub = subscription(otherUser, "e1");
        when(repo.findByEndpoint("e1")).thenReturn(Optional.of(sub));

        service.unsubscribe(userId, "e1");

        verify(repo, never()).delete(any());
    }

    @Test
    void unsubscribeIsNoOpWhenEndpointNotFound() {
        when(repo.findByEndpoint("missing")).thenReturn(Optional.empty());

        service.unsubscribe(userId, "missing");

        verify(repo, never()).delete(any());
    }

    @Test
    void subscriptionsForDelegatesToRepository() {
        PushSubscription s1 = subscription(userId, "e1");
        PushSubscription s2 = subscription(userId, "e2");
        when(repo.findByUserId(userId)).thenReturn(List.of(s1, s2));

        List<PushSubscription> res = service.subscriptionsFor(userId);

        assertThat(res).containsExactly(s1, s2);
    }

    @Test
    void sendToUserReturnsZeroWhenNoSubscriptions() {
        when(repo.findByUserId(userId)).thenReturn(List.of());

        assertThat(service.sendToUser(userId)).isZero();
    }
}

import { beforeEach, describe, expect, it } from 'vitest';
import { useAuthStore } from './auth.store';
import type { User } from '@/types/auth.types';

function makeUser(overrides: Partial<User> = {}): User {
  return {
    id: 'u1',
    username: 'ali',
    email: 'ali@example.com',
    role: 'USER',
    emailVerified: true,
    createdAt: '2026-01-01T00:00:00Z',
    ...overrides,
  };
}

describe('useAuthStore', () => {
  beforeEach(() => {
    useAuthStore.setState({ user: null, accessToken: null, refreshToken: null });
    window.localStorage.clear();
  });

  it('starts with null identity and no tokens', () => {
    const s = useAuthStore.getState();
    expect(s.user).toBeNull();
    expect(s.accessToken).toBeNull();
    expect(s.refreshToken).toBeNull();
    expect(s.isAuthenticated()).toBe(false);
  });

  it('setAuth populates user and both tokens', () => {
    const user = makeUser();
    useAuthStore.getState().setAuth(user, 'access-1', 'refresh-1');

    const s = useAuthStore.getState();
    expect(s.user).toEqual(user);
    expect(s.accessToken).toBe('access-1');
    expect(s.refreshToken).toBe('refresh-1');
    expect(s.isAuthenticated()).toBe(true);
  });

  it('clearAuth wipes everything', () => {
    useAuthStore.getState().setAuth(makeUser(), 'a', 'r');
    useAuthStore.getState().clearAuth();

    const s = useAuthStore.getState();
    expect(s.user).toBeNull();
    expect(s.accessToken).toBeNull();
    expect(s.refreshToken).toBeNull();
    expect(s.isAuthenticated()).toBe(false);
  });

  it('isAuthenticated reflects only the accessToken', () => {
    useAuthStore.setState({ user: makeUser(), accessToken: null, refreshToken: 'r' });
    expect(useAuthStore.getState().isAuthenticated()).toBe(false);

    useAuthStore.setState({ accessToken: 'a' });
    expect(useAuthStore.getState().isAuthenticated()).toBe(true);
  });

  it('persists only user and refreshToken (not the access token) to localStorage', () => {
    useAuthStore.getState().setAuth(makeUser({ username: 'veli' }), 'access-xyz', 'refresh-xyz');

    const raw = window.localStorage.getItem('fintrack-auth');
    expect(raw).toBeTruthy();
    const parsed = JSON.parse(raw!);
    expect(parsed.state.user.username).toBe('veli');
    expect(parsed.state.refreshToken).toBe('refresh-xyz');
    expect(parsed.state.accessToken).toBeUndefined();
  });
});

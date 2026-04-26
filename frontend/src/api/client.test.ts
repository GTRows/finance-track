import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import axios, { AxiosError, type AxiosRequestConfig, type AxiosResponse } from 'axios';
import client from './client';
import { useAuthStore } from '@/store/auth.store';

type AdapterFn = (config: AxiosRequestConfig) => Promise<AxiosResponse>;

function ok<T = unknown>(config: AxiosRequestConfig, data: T, status = 200): AxiosResponse<T> {
  return {
    data,
    status,
    statusText: 'OK',
    headers: {},
    config: config as never,
  };
}

function fail(config: AxiosRequestConfig, status: number): AxiosError {
  const response = {
    data: { error: 'err' },
    status,
    statusText: 'Err',
    headers: {},
    config: config as never,
  };
  return new AxiosError('request failed', String(status), config as never, null, response);
}

describe('api client interceptors', () => {
  const originalAdapter = client.defaults.adapter;

  beforeEach(() => {
    useAuthStore.setState({ user: null, accessToken: null, refreshToken: null });
  });

  afterEach(() => {
    client.defaults.adapter = originalAdapter;
    vi.restoreAllMocks();
  });

  it('attaches Authorization header when access token is present', async () => {
    useAuthStore.setState({ accessToken: 'the-token' });
    const seen: AxiosRequestConfig[] = [];
    const adapter: AdapterFn = async (cfg) => {
      seen.push(cfg);
      return ok(cfg, { ok: true });
    };
    client.defaults.adapter = adapter as never;

    await client.get('/ping');

    expect(seen).toHaveLength(1);
    expect(seen[0].headers?.Authorization).toBe('Bearer the-token');
  });

  it('omits Authorization header when there is no access token', async () => {
    const seen: AxiosRequestConfig[] = [];
    const adapter: AdapterFn = async (cfg) => {
      seen.push(cfg);
      return ok(cfg, {});
    };
    client.defaults.adapter = adapter as never;

    await client.get('/ping');

    expect(seen[0].headers?.Authorization).toBeUndefined();
  });

  it('forwards non-401 errors without attempting refresh', async () => {
    const postSpy = vi.spyOn(axios, 'post');
    const adapter: AdapterFn = async (cfg) => {
      throw fail(cfg, 500);
    };
    client.defaults.adapter = adapter as never;

    await expect(client.get('/boom')).rejects.toMatchObject({ response: { status: 500 } });
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('does not retry 401 on /auth/login', async () => {
    const postSpy = vi.spyOn(axios, 'post');
    const adapter: AdapterFn = async (cfg) => {
      throw fail(cfg, 401);
    };
    client.defaults.adapter = adapter as never;

    await expect(
      client.post('/auth/login', { username: 'x', password: 'y' }),
    ).rejects.toMatchObject({ response: { status: 401 } });
    expect(postSpy).not.toHaveBeenCalled();
  });

  it('refreshes on 401, updates the store, and retries the original request', async () => {
    useAuthStore.setState({ accessToken: 'old-access', refreshToken: 'rt-1' });

    const calls: Array<{ url?: string; auth?: string }> = [];
    let first = true;
    const adapter: AdapterFn = async (cfg) => {
      calls.push({
        url: cfg.url,
        auth: (cfg.headers as Record<string, string> | undefined)?.Authorization,
      });
      if (cfg.url === '/data' && first) {
        first = false;
        throw fail(cfg, 401);
      }
      return ok(cfg, { done: true });
    };
    client.defaults.adapter = adapter as never;

    const postSpy = vi.spyOn(axios, 'post').mockResolvedValue({
      data: {
        accessToken: 'new-access',
        refreshToken: 'rt-2',
        user: {
          id: 'u1',
          username: 'ali',
          email: 'a@b',
          role: 'USER',
          emailVerified: true,
          createdAt: '2026-01-01',
        },
      },
    } as never);

    const res = await client.get('/data');

    expect(res.data).toEqual({ done: true });
    expect(postSpy).toHaveBeenCalledTimes(1);
    expect(postSpy.mock.calls[0][1]).toEqual({ refreshToken: 'rt-1' });

    const s = useAuthStore.getState();
    expect(s.accessToken).toBe('new-access');
    expect(s.refreshToken).toBe('rt-2');

    expect(calls[0].auth).toBe('Bearer old-access');
    expect(calls.at(-1)?.auth).toBe('Bearer new-access');
  });

  it('clears auth and redirects to /login when there is no refresh token', async () => {
    useAuthStore.setState({ accessToken: 'a', refreshToken: null });
    const originalLocation = window.location;
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        ...originalLocation,
        set href(v: string) {
          hrefSetter(v);
        },
        get href() {
          return '/';
        },
      },
    });

    const adapter: AdapterFn = async (cfg) => {
      throw fail(cfg, 401);
    };
    client.defaults.adapter = adapter as never;

    await expect(client.get('/data')).rejects.toBeTruthy();

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(hrefSetter).toHaveBeenCalledWith('/login');

    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    });
  });

  it('clears auth and redirects when the refresh call itself fails', async () => {
    useAuthStore.setState({ accessToken: 'a', refreshToken: 'rt-stale' });
    const originalLocation = window.location;
    const hrefSetter = vi.fn();
    Object.defineProperty(window, 'location', {
      configurable: true,
      value: {
        ...originalLocation,
        set href(v: string) {
          hrefSetter(v);
        },
        get href() {
          return '/';
        },
      },
    });

    const adapter: AdapterFn = async (cfg) => {
      throw fail(cfg, 401);
    };
    client.defaults.adapter = adapter as never;
    vi.spyOn(axios, 'post').mockRejectedValue(new Error('refresh denied'));

    await expect(client.get('/data')).rejects.toBeTruthy();

    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(useAuthStore.getState().refreshToken).toBeNull();
    expect(hrefSetter).toHaveBeenCalledWith('/login');

    Object.defineProperty(window, 'location', {
      configurable: true,
      value: originalLocation,
    });
  });
});

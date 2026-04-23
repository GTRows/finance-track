import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { portfolioApi } from './portfolio.api';
import client from './client';
import type { CreatePortfolioRequest, UpdatePortfolioRequest } from '@/types/portfolio.types';

describe('portfolioApi', () => {
  beforeEach(() => {
    vi.restoreAllMocks();
  });

  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('list issues GET /portfolios', async () => {
    const spy = vi.spyOn(client, 'get').mockResolvedValue({ data: [] } as never);

    const res = await portfolioApi.list();

    expect(spy).toHaveBeenCalledWith('/portfolios');
    expect(res).toEqual([]);
  });

  it('get issues GET /portfolios/:id', async () => {
    const payload = { id: 'p1', name: 'Main', portfolioType: 'INDIVIDUAL', active: true };
    const spy = vi.spyOn(client, 'get').mockResolvedValue({ data: payload } as never);

    const res = await portfolioApi.get('p1');

    expect(spy).toHaveBeenCalledWith('/portfolios/p1');
    expect(res).toEqual(payload);
  });

  it('create issues POST /portfolios with body', async () => {
    const req: CreatePortfolioRequest = { name: 'Main', portfolioType: 'INDIVIDUAL' } as never;
    const returned = { id: 'p1', name: 'Main', portfolioType: 'INDIVIDUAL', active: true };
    const spy = vi.spyOn(client, 'post').mockResolvedValue({ data: returned } as never);

    const res = await portfolioApi.create(req);

    expect(spy).toHaveBeenCalledWith('/portfolios', req);
    expect(res).toEqual(returned);
  });

  it('update issues PUT /portfolios/:id with body', async () => {
    const req: UpdatePortfolioRequest = { name: 'Updated' } as never;
    const returned = { id: 'p1', name: 'Updated', portfolioType: 'INDIVIDUAL', active: true };
    const spy = vi.spyOn(client, 'put').mockResolvedValue({ data: returned } as never);

    const res = await portfolioApi.update('p1', req);

    expect(spy).toHaveBeenCalledWith('/portfolios/p1', req);
    expect(res).toEqual(returned);
  });

  it('delete issues DELETE /portfolios/:id and returns void', async () => {
    const spy = vi.spyOn(client, 'delete').mockResolvedValue({ data: undefined } as never);

    await portfolioApi.delete('p1');

    expect(spy).toHaveBeenCalledWith('/portfolios/p1');
  });
});

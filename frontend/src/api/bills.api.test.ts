import { afterEach, describe, expect, it, vi } from 'vitest';
import { billsApi } from './bills.api';
import client from './client';

describe('billsApi', () => {
  afterEach(() => {
    vi.restoreAllMocks();
  });

  it('list issues GET /bills', async () => {
    const spy = vi.spyOn(client, 'get').mockResolvedValue({ data: [] } as never);

    await billsApi.list();

    expect(spy).toHaveBeenCalledWith('/bills');
  });

  it('create posts the body to /bills', async () => {
    const body = { name: 'Rent', amount: 1000, dueDay: 1 } as never;
    const spy = vi.spyOn(client, 'post').mockResolvedValue({ data: body } as never);

    await billsApi.create(body);

    expect(spy).toHaveBeenCalledWith('/bills', body);
  });

  it('pay routes to POST /bills/:id/pay with body', async () => {
    const spy = vi.spyOn(client, 'post').mockResolvedValue({ data: {} } as never);

    await billsApi.pay('b1', { amount: 1000 } as never);

    expect(spy).toHaveBeenCalledWith('/bills/b1/pay', { amount: 1000 });
  });

  it('history issues GET /bills/:id/history', async () => {
    const spy = vi.spyOn(client, 'get').mockResolvedValue({ data: [] } as never);

    await billsApi.history('b1');

    expect(spy).toHaveBeenCalledWith('/bills/b1/history');
  });

  it('delete issues DELETE /bills/:id', async () => {
    const spy = vi.spyOn(client, 'delete').mockResolvedValue({ data: undefined } as never);

    await billsApi.delete('b1');

    expect(spy).toHaveBeenCalledWith('/bills/b1');
  });

  it('markUsed issues POST /bills/:id/mark-used without body', async () => {
    const spy = vi.spyOn(client, 'post').mockResolvedValue({ data: {} } as never);

    await billsApi.markUsed('b1');

    expect(spy).toHaveBeenCalledWith('/bills/b1/mark-used');
  });

  it('audit issues GET /bills/audit', async () => {
    const spy = vi.spyOn(client, 'get').mockResolvedValue({ data: {} } as never);

    await billsApi.audit();

    expect(spy).toHaveBeenCalledWith('/bills/audit');
  });
});

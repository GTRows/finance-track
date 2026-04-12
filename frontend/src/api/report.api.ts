import client from './client';

const triggerDownload = (blob: Blob, filename: string) => {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  document.body.removeChild(a);
  URL.revokeObjectURL(url);
};

export const reportApi = {
  downloadPortfolioPdf: async (portfolioId: string, portfolioName: string): Promise<void> => {
    const { data } = await client.get(`/reports/portfolio/${portfolioId}`, {
      responseType: 'blob',
    });
    const safeName = portfolioName.replace(/[^a-z0-9]+/gi, '-').toLowerCase();
    triggerDownload(data, `portfolio-${safeName}.pdf`);
  },

  downloadBudgetCsv: async (from: string, to: string): Promise<void> => {
    const { data } = await client.get('/reports/budget', {
      params: { from, to },
      responseType: 'blob',
    });
    triggerDownload(data, `budget-${from}-to-${to}.csv`);
  },
};

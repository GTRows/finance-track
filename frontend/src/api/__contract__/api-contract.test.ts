import { describe, expectTypeOf, it } from 'vitest';

import type { components } from '../openapi.types';
import type { PriceSyncResult } from '../price.api';
import type { PricePoint, TefasFundSearchRow } from '../asset.api';
import type { AuthResponse, TotpSetup, TotpStatus } from '@/types/auth.types';
import type {
  Bill,
  BillPayment,
  CreateBillRequest,
  PayBillRequest,
} from '@/types/bill.types';
import type {
  BudgetTransaction,
  CategoriesData,
  Category,
  CreateCategoryRequest,
  CreateTransactionRequest,
  MonthlySummary,
} from '@/types/budget.types';
import type {
  AddHoldingRequest,
  Asset,
  CreatePortfolioRequest,
  Holding,
  Portfolio,
  RecordTransactionRequest,
  Transaction,
  UpdatePortfolioRequest,
} from '@/types/portfolio.types';

type Schemas = NonNullable<components['schemas']>;

/**
 * Drops `?:` from optional fields. Springdoc emits every field without an explicit
 * `@JsonProperty(required = true)` as optional; the hand-written types express true presence,
 * so the comparison only makes sense after stripping optionality on the generated side.
 */
type Concrete<T> = { [K in keyof T]-?: T[K] };

describe('OpenAPI contract: auth surface', () => {
  it('AuthResponse aligns with the generated AuthResponse schema', () => {
    type Generated = Concrete<Schemas['AuthResponse']>;
    expectTypeOf<keyof AuthResponse>().toEqualTypeOf<keyof Generated>();
    expectTypeOf<AuthResponse['accessExpiresIn']>().toMatchTypeOf<number>();
    expectTypeOf<AuthResponse['requiresTotp']>().toMatchTypeOf<boolean>();
  });

  it('TotpStatus aligns with the generated TotpStatusResponse schema', () => {
    type Generated = Concrete<Schemas['TotpStatusResponse']>;
    expectTypeOf<keyof TotpStatus>().toEqualTypeOf<keyof Generated>();
  });

  it('TotpSetup aligns with the generated TotpSetupResponse schema', () => {
    type Generated = Concrete<Schemas['TotpSetupResponse']>;
    expectTypeOf<keyof TotpSetup>().toEqualTypeOf<keyof Generated>();
  });
});

describe('OpenAPI contract: portfolio surface', () => {
  it('Portfolio response shape aligns with PortfolioResponse', () => {
    type Generated = Concrete<Schemas['PortfolioResponse']>;
    expectTypeOf<keyof Portfolio>().toEqualTypeOf<keyof Generated>();
    expectTypeOf<Portfolio['type']>().toMatchTypeOf<Generated['type']>();
  });

  it('CreatePortfolioRequest aligns with the request schema', () => {
    type Generated = Schemas['CreatePortfolioRequest'];
    expectTypeOf<CreatePortfolioRequest>().toMatchTypeOf<Generated>();
    expectTypeOf<CreatePortfolioRequest['type']>().toMatchTypeOf<Generated['type']>();
  });

  it('UpdatePortfolioRequest aligns with the request schema', () => {
    type Generated = Schemas['UpdatePortfolioRequest'];
    expectTypeOf<UpdatePortfolioRequest>().toMatchTypeOf<Generated>();
  });

  it('Holding aligns with the generated HoldingResponse schema', () => {
    type Generated = Concrete<Schemas['HoldingResponse']>;
    expectTypeOf<keyof Holding>().toEqualTypeOf<keyof Generated>();
  });

  it('AddHoldingRequest aligns with the request schema', () => {
    type Generated = Schemas['AddHoldingRequest'];
    expectTypeOf<AddHoldingRequest>().toMatchTypeOf<Generated>();
  });

  it('Transaction aligns with the generated InvestmentTransactionResponse schema', () => {
    type Generated = Concrete<Schemas['InvestmentTransactionResponse']>;
    expectTypeOf<keyof Transaction>().toEqualTypeOf<keyof Generated>();
  });

  it('RecordTransactionRequest aligns with the request schema', () => {
    type Generated = Schemas['RecordTransactionRequest'];
    expectTypeOf<RecordTransactionRequest>().toMatchTypeOf<Generated>();
    expectTypeOf<RecordTransactionRequest['txnType']>().toMatchTypeOf<Generated['txnType']>();
  });
});

describe('OpenAPI contract: budget surface', () => {
  it('BudgetTransaction aligns with the generated TransactionResponse schema', () => {
    type Generated = Concrete<Schemas['TransactionResponse']>;
    expectTypeOf<keyof BudgetTransaction>().toEqualTypeOf<keyof Generated>();
    expectTypeOf<BudgetTransaction['txnType']>().toMatchTypeOf<Generated['txnType']>();
  });

  it('CreateTransactionRequest aligns with the request schema', () => {
    type Generated = Schemas['CreateTransactionRequest'];
    expectTypeOf<CreateTransactionRequest>().toMatchTypeOf<Generated>();
    expectTypeOf<CreateTransactionRequest['txnType']>().toMatchTypeOf<Generated['txnType']>();
  });

  it('MonthlySummary aligns with the generated MonthlySummaryResponse schema', () => {
    type Generated = Concrete<Schemas['MonthlySummaryResponse']>;
    expectTypeOf<keyof MonthlySummary>().toEqualTypeOf<keyof Generated>();
  });

  it('Category aligns with the generated CategoryResponse schema', () => {
    type Generated = Concrete<Schemas['CategoryResponse']>;
    expectTypeOf<keyof Category>().toEqualTypeOf<keyof Generated>();
  });

  it('CategoriesData aligns with the generated CategoriesResponse schema', () => {
    type Generated = Concrete<Schemas['CategoriesResponse']>;
    expectTypeOf<keyof CategoriesData>().toEqualTypeOf<keyof Generated>();
  });

  it('CreateCategoryRequest aligns with the request schema', () => {
    type Generated = Schemas['CreateCategoryRequest'];
    expectTypeOf<CreateCategoryRequest>().toMatchTypeOf<Generated>();
  });
});

describe('OpenAPI contract: bills surface', () => {
  it('Bill aligns with the generated BillResponse schema', () => {
    type Generated = Concrete<Schemas['BillResponse']>;
    expectTypeOf<keyof Bill>().toEqualTypeOf<keyof Generated>();
  });

  it('BillPayment aligns with the generated PaymentHistoryResponse schema', () => {
    type Generated = Concrete<Schemas['PaymentHistoryResponse']>;
    expectTypeOf<keyof BillPayment>().toEqualTypeOf<keyof Generated>();
  });

  it('CreateBillRequest aligns with the request schema', () => {
    type Generated = Schemas['CreateBillRequest'];
    expectTypeOf<CreateBillRequest>().toMatchTypeOf<Generated>();
  });

  it('PayBillRequest aligns with the request schema', () => {
    type Generated = Schemas['PayBillRequest'];
    expectTypeOf<PayBillRequest>().toMatchTypeOf<Generated>();
  });
});

describe('OpenAPI contract: assets surface', () => {
  it('Asset aligns with the generated AssetResponse schema', () => {
    type Generated = Concrete<Schemas['AssetResponse']>;
    expectTypeOf<keyof Asset>().toEqualTypeOf<keyof Generated>();
    expectTypeOf<Asset['assetType']>().toMatchTypeOf<Generated['assetType']>();
  });

  it('PricePoint aligns with the generated PricePoint schema', () => {
    type Generated = Concrete<Schemas['PricePoint']>;
    expectTypeOf<keyof PricePoint>().toEqualTypeOf<keyof Generated>();
  });

  it('TefasFundSearchRow aligns with the generated FundSearchRow schema', () => {
    type Generated = Concrete<Schemas['FundSearchRow']>;
    expectTypeOf<keyof TefasFundSearchRow>().toEqualTypeOf<keyof Generated>();
  });
});

describe('OpenAPI contract: prices surface', () => {
  it('PriceSyncResult aligns with the generated SyncResult schema', () => {
    type Generated = Concrete<Schemas['SyncResult']>;
    expectTypeOf<keyof PriceSyncResult>().toEqualTypeOf<keyof Generated>();
  });
});

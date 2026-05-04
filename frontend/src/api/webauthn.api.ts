import client from './client';
import type { AuthResponse } from '@/types/auth.types';

export interface PubKeyCredParam {
  type: string;
  alg: number;
}

export interface WebAuthnRegistrationStartResponse {
  challenge: string;
  rpId: string;
  rpName: string;
  userId: string;
  userName: string;
  pubKeyCredParams: PubKeyCredParam[];
  timeout: number;
  attestation: string;
  userVerification: string;
  residentKey: string;
}

export interface WebAuthnRegistrationFinishRequest {
  name: string;
  credentialId: string;
  clientDataJSON: string;
  attestationObject: string;
  transports: string[];
}

export interface WebAuthnRegistrationFinishResponse {
  id: string;
  name: string | null;
}

export interface AllowedCredential {
  type: string;
  id: string;
  transports: string[];
}

export interface AssertionStartResponse {
  challenge: string;
  rpId: string;
  timeout: number;
  allowCredentials: AllowedCredential[];
  userVerification: string;
}

export interface AssertionFinishRequest {
  credentialId: string;
  clientDataJSON: string;
  authenticatorData: string;
  signature: string;
  userHandle: string | null;
}

export interface AuthenticatorSummary {
  id: string;
  name: string | null;
  createdAt: string;
  lastUsedAt: string | null;
  transports: string | null;
}

/** WebAuthn passkey API module. */
export const webauthnApi = {
  registerStart: () =>
    client
      .post<WebAuthnRegistrationStartResponse>('/auth/webauthn/register/start')
      .then((r) => r.data),

  registerFinish: (body: WebAuthnRegistrationFinishRequest) =>
    client
      .post<WebAuthnRegistrationFinishResponse>('/auth/webauthn/register/finish', body)
      .then((r) => r.data),

  loginStart: (username: string) =>
    client
      .post<AssertionStartResponse>('/auth/webauthn/login/start', { username })
      .then((r) => r.data),

  loginFinish: (body: AssertionFinishRequest) =>
    client.post<AuthResponse>('/auth/webauthn/login/finish', body).then((r) => r.data),

  list: () =>
    client.get<AuthenticatorSummary[]>('/auth/webauthn/authenticators').then((r) => r.data),

  remove: (id: string) =>
    client.delete(`/auth/webauthn/authenticators/${id}`).then(() => undefined),
};

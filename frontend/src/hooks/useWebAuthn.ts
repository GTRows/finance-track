import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import {
  webauthnApi,
  type AssertionFinishRequest,
  type WebAuthnRegistrationFinishRequest,
} from '@/api/webauthn.api';
import { base64UrlToBuffer, bufferToBase64Url } from '@/utils/base64url';
import type { AuthResponse } from '@/types/auth.types';

const AUTHENTICATORS_KEY = ['auth', 'webauthn', 'authenticators'] as const;

interface RegisterArgs {
  name: string;
}

/** Whether the current browser exposes the WebAuthn API. */
export function isWebAuthnSupported(): boolean {
  return typeof window !== 'undefined' && typeof window.PublicKeyCredential === 'function';
}

/** Drives the registration ceremony from start to finish. */
export function useWebAuthnRegister() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: async ({ name }: RegisterArgs) => {
      const options = await webauthnApi.registerStart();

      const publicKey: PublicKeyCredentialCreationOptions = {
        challenge: base64UrlToBuffer(options.challenge),
        rp: { id: options.rpId, name: options.rpName },
        user: {
          id: base64UrlToBuffer(options.userId),
          name: options.userName,
          displayName: options.userName,
        },
        pubKeyCredParams: options.pubKeyCredParams.map((p) => ({
          type: 'public-key',
          alg: p.alg,
        })),
        timeout: options.timeout,
        attestation: options.attestation as AttestationConveyancePreference,
        authenticatorSelection: {
          residentKey: options.residentKey as ResidentKeyRequirement,
          userVerification: options.userVerification as UserVerificationRequirement,
        },
      };

      const credential = (await navigator.credentials.create({ publicKey })) as
        | PublicKeyCredential
        | null;
      if (!credential) {
        throw new Error('WEBAUTHN_NO_CREDENTIAL');
      }
      const response = credential.response as AuthenticatorAttestationResponse;

      const transports =
        typeof response.getTransports === 'function' ? response.getTransports() : [];

      const body: WebAuthnRegistrationFinishRequest = {
        name,
        credentialId: bufferToBase64Url(credential.rawId),
        clientDataJSON: bufferToBase64Url(response.clientDataJSON),
        attestationObject: bufferToBase64Url(response.attestationObject),
        transports,
      };

      return webauthnApi.registerFinish(body);
    },
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: AUTHENTICATORS_KEY });
    },
  });
}

interface LoginArgs {
  username: string;
}

/** Drives the assertion ceremony and returns the AuthResponse from the backend. */
export function useWebAuthnLogin() {
  return useMutation({
    mutationFn: async ({ username }: LoginArgs): Promise<AuthResponse> => {
      const options = await webauthnApi.loginStart(username);

      const publicKey: PublicKeyCredentialRequestOptions = {
        challenge: base64UrlToBuffer(options.challenge),
        rpId: options.rpId,
        timeout: options.timeout,
        userVerification: options.userVerification as UserVerificationRequirement,
        allowCredentials: options.allowCredentials.map((c) => ({
          type: 'public-key',
          id: base64UrlToBuffer(c.id),
          transports: c.transports as AuthenticatorTransport[],
        })),
      };

      const credential = (await navigator.credentials.get({ publicKey })) as
        | PublicKeyCredential
        | null;
      if (!credential) {
        throw new Error('WEBAUTHN_NO_CREDENTIAL');
      }
      const response = credential.response as AuthenticatorAssertionResponse;

      const body: AssertionFinishRequest = {
        credentialId: bufferToBase64Url(credential.rawId),
        clientDataJSON: bufferToBase64Url(response.clientDataJSON),
        authenticatorData: bufferToBase64Url(response.authenticatorData),
        signature: bufferToBase64Url(response.signature),
        userHandle: response.userHandle ? bufferToBase64Url(response.userHandle) : null,
      };

      return webauthnApi.loginFinish(body);
    },
  });
}

/** Lists the current user's enrolled passkeys. */
export function useAuthenticators(enabled = true) {
  return useQuery({
    queryKey: AUTHENTICATORS_KEY,
    queryFn: webauthnApi.list,
    enabled,
  });
}

/** Revokes a passkey by id, then refreshes the list. */
export function useDeleteAuthenticator() {
  const qc = useQueryClient();
  return useMutation({
    mutationFn: (id: string) => webauthnApi.remove(id),
    onSuccess: () => {
      void qc.invalidateQueries({ queryKey: AUTHENTICATORS_KEY });
    },
  });
}

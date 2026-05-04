import { useState } from 'react';
import { useTranslation } from 'react-i18next';
import type { AxiosError } from 'axios';
import { KeyRound, Loader2 } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { isWebAuthnSupported, useWebAuthnLogin } from '@/hooks/useWebAuthn';
import type { ApiError, AuthResponse } from '@/types/auth.types';

interface WebAuthnLoginButtonProps {
  username: string;
  onSuccess: (response: AuthResponse) => void;
  onError: (message: string) => void;
}

export function WebAuthnLoginButton({
  username,
  onSuccess,
  onError,
}: WebAuthnLoginButtonProps) {
  const { t } = useTranslation();
  const loginMutation = useWebAuthnLogin();
  const [busy, setBusy] = useState(false);

  if (!isWebAuthnSupported()) {
    return null;
  }

  const trimmed = username.trim();
  const disabled = busy || loginMutation.isPending || trimmed.length === 0;

  const handleClick = async () => {
    if (trimmed.length === 0) {
      onError(t('auth.passkeyUsernameRequired'));
      return;
    }
    setBusy(true);
    try {
      const response = await loginMutation.mutateAsync({ username: trimmed });
      onSuccess(response);
    } catch (err) {
      if (err instanceof DOMException && err.name === 'NotAllowedError') {
        onError(t('auth.passkeyCancelled'));
        return;
      }
      const axiosError = err as AxiosError<ApiError>;
      onError(axiosError.response?.data?.error ?? t('common.somethingWentWrong'));
    } finally {
      setBusy(false);
    }
  };

  return (
    <Button
      type="button"
      variant="outline"
      className="w-full cursor-pointer"
      disabled={disabled}
      onClick={() => {
        void handleClick();
      }}
    >
      {busy || loginMutation.isPending ? (
        <Loader2 className="w-4 h-4 animate-spin" />
      ) : (
        <>
          <KeyRound className="w-4 h-4 mr-2" />
          {t('auth.passkeySignIn')}
        </>
      )}
    </Button>
  );
}

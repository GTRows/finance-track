/** User profile returned from the API. */
export interface User {
  id: string;
  username: string;
  email: string;
  role: 'USER' | 'ADMIN';
  createdAt: string;
}

/** Response from login, register, and refresh endpoints. */
export interface AuthResponse {
  accessToken: string | null;
  refreshToken: string | null;
  accessExpiresIn: number;
  user: User | null;
  requiresTotp: boolean;
  totpChallengeToken: string | null;
}

export interface TotpStatus {
  enabled: boolean;
}

export interface TotpSetup {
  secret: string;
  otpauthUrl: string;
}

/** API error response format. */
export interface ApiError {
  error: string;
  code: string;
  requestId: string;
  timestamp: string;
  path: string;
}

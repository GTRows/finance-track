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
  accessToken: string;
  refreshToken: string;
  accessExpiresIn: number;
  user: User;
}

/** API error response format. */
export interface ApiError {
  error: string;
  code: string;
  requestId: string;
  timestamp: string;
  path: string;
}

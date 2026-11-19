export interface User {
  id: string;
  name: string;
  email: string;
  locale: string;
  createdAt: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: 'Bearer';
  expiresIn: number;
  user: User;
}

export interface FieldProblem {
  field: string;
  message: string;
}

/** Formato padrão de erro do backend. */
export interface ApiErrorBody {
  code: string;
  message: string;
  details?: FieldProblem[];
}

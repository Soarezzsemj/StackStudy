export interface User {
  id: string;
  name: string;
  email: string;
  locale: string;
  createdAt: string;
  // Perfil (ausente = não preenchido).
  course?: string;
  institution?: string;
  currentPeriod?: string;
  bio?: string;
  /** Cor do avatar de iniciais (paleta fixa). */
  avatarColor?: string;
  /** Fuso IANA preferido; ausente = o do navegador. */
  timeZone?: string;
  /** USER ou ADMIN. Só decide se o link do painel aparece; o servidor confere de novo. */
  role?: 'USER' | 'ADMIN';
  /** Apelido padrão das salas de estudo (cadastro, Fase 11). */
  nickname?: string;
  studyArea?: string;
  focusRhythm?: 'POMODORO_25_5' | 'POMODORO_50_10' | 'FREE';
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

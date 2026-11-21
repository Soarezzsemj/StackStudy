import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import {
  Button,
  Card,
  Chip,
  EmptyState,
  ErrorState,
  LoadingState,
  TextField,
} from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import type { SessionView } from '@/lib/api/account';
import type { User } from '@/lib/api/types';
import { describeUserAgent } from './helpers';
import {
  useChangeEmail,
  useChangePassword,
  useEndAllSessions,
  useEndOtherSessions,
  useEndSession,
  useSessions,
} from './queries';
import { SavedNote } from './SavedNote';

/** E-mail, senha e dispositivos conectados. */
export function AccountSection({ user }: { user: User }) {
  const { t } = useTranslation();
  return (
    <Card title={t('profile.account.title')} aria-label={t('profile.account.title')} id="conta">
      <div className="flex flex-col gap-8">
        <EmailForm user={user} />
        <PasswordForm />
        <Sessions />
      </div>
    </Card>
  );
}

function EmailForm({ user }: { user: User }) {
  const { t } = useTranslation();
  const [email, setEmail] = useState(user.email);
  const [password, setPassword] = useState('');
  const change = useChangeEmail();
  const errors = fieldErrors(change.error);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    change.mutate(
      { email: email.trim(), currentPassword: password },
      { onSuccess: () => setPassword('') },
    );
  }

  return (
    <form
      noValidate
      onSubmit={onSubmit}
      className="flex flex-col gap-4"
      aria-labelledby="email-title"
    >
      <h3 id="email-title" className="label text-text-secondary">
        {t('profile.account.email')}
      </h3>
      <div className="grid gap-4 sm:grid-cols-2">
        <TextField
          type="email"
          autoComplete="email"
          label={t('profile.account.newEmail')}
          value={email}
          error={errors.email}
          onChange={(e) => {
            change.reset();
            setEmail(e.target.value);
          }}
        />
        <TextField
          type="password"
          autoComplete="current-password"
          label={t('profile.account.currentPassword')}
          value={password}
          error={errors.currentPassword && t('profile.account.wrongPassword')}
          onChange={(e) => {
            change.reset();
            setPassword(e.target.value);
          }}
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Button
          type="submit"
          variant="secondary"
          disabled={change.isPending || !password || email.trim() === user.email}
        >
          {t('profile.account.changeEmail')}
        </Button>
        <SavedNote show={change.isSuccess}>{t('profile.account.emailChanged')}</SavedNote>
      </div>
      {change.error && !errors.currentPassword && (
        <ErrorState message={errorMessage(t, change.error)} />
      )}
    </form>
  );
}

function PasswordForm() {
  const { t } = useTranslation();
  const [current, setCurrent] = useState('');
  const [next, setNext] = useState('');
  const [confirm, setConfirm] = useState('');
  const [localError, setLocalError] = useState<{ next?: string; confirm?: string }>({});
  const change = useChangePassword();
  const errors = fieldErrors(change.error);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const problems: { next?: string; confirm?: string } = {};
    if (next.length < 8 || next.length > 72) problems.next = t('profile.account.passwordLength');
    if (confirm !== next) problems.confirm = t('profile.account.passwordMismatch');
    setLocalError(problems);
    if (problems.next || problems.confirm) return;
    change.mutate(
      { currentPassword: current, newPassword: next },
      {
        onSuccess: () => {
          setCurrent('');
          setNext('');
          setConfirm('');
        },
      },
    );
  }

  const reset = () => {
    change.reset();
    setLocalError({});
  };

  return (
    <form
      noValidate
      onSubmit={onSubmit}
      className="flex flex-col gap-4 border-t border-border pt-6"
      aria-labelledby="password-title"
    >
      <h3 id="password-title" className="label text-text-secondary">
        {t('profile.account.password')}
      </h3>
      <p className="text-sm text-text-secondary">{t('profile.account.passwordHint')}</p>
      <div className="grid gap-4 sm:grid-cols-3">
        <TextField
          type="password"
          autoComplete="current-password"
          label={t('profile.account.currentPassword')}
          value={current}
          error={errors.currentPassword && t('profile.account.wrongPassword')}
          onChange={(e) => {
            reset();
            setCurrent(e.target.value);
          }}
        />
        <TextField
          type="password"
          autoComplete="new-password"
          label={t('profile.account.newPassword')}
          value={next}
          error={localError.next ?? (errors.newPassword && t('profile.account.passwordInvalid'))}
          onChange={(e) => {
            reset();
            setNext(e.target.value);
          }}
        />
        <TextField
          type="password"
          autoComplete="new-password"
          label={t('profile.account.confirmPassword')}
          value={confirm}
          error={localError.confirm}
          onChange={(e) => {
            reset();
            setConfirm(e.target.value);
          }}
        />
      </div>
      <div className="flex flex-wrap items-center gap-3">
        <Button type="submit" variant="secondary" disabled={change.isPending || !current}>
          {t('profile.account.changePassword')}
        </Button>
        <SavedNote show={change.isSuccess}>{t('profile.account.passwordChanged')}</SavedNote>
      </div>
      {change.error && !errors.currentPassword && !errors.newPassword && (
        <ErrorState message={errorMessage(t, change.error)} />
      )}
    </form>
  );
}

function Sessions() {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const sessions = useSessions();
  const endOne = useEndSession();
  const endOthers = useEndOtherSessions();
  const endAll = useEndAllSessions();
  const [confirmAll, setConfirmAll] = useState(false);
  const error = endOne.error ?? endOthers.error ?? endAll.error;
  const others = (sessions.data ?? []).filter((s) => !s.current).length;

  return (
    <section
      aria-labelledby="sessions-title"
      className="flex flex-col gap-4 border-t border-border pt-6"
    >
      <h3 id="sessions-title" className="label text-text-secondary">
        {t('profile.sessions.title')}
      </h3>
      {sessions.isPending ? (
        <LoadingState />
      ) : sessions.isError ? (
        <ErrorState message={t('profile.loadError')} onRetry={() => void sessions.refetch()} />
      ) : sessions.data.length === 0 ? (
        <EmptyState title={t('profile.sessions.empty')} />
      ) : (
        <ul className="m-0 flex list-none flex-col gap-2 p-0">
          {sessions.data.map((s) => (
            <SessionRow
              key={s.id}
              session={s}
              disabled={endOne.isPending}
              onEnd={() => endOne.mutate(s.id)}
            />
          ))}
        </ul>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          disabled={others === 0 || endOthers.isPending}
          onClick={() => endOthers.mutate()}
        >
          {t('profile.sessions.endOthers')}
        </Button>
        <Button
          variant={confirmAll ? 'primary' : 'ghost'}
          disabled={endAll.isPending}
          aria-describedby={confirmAll ? 'end-all-warning' : undefined}
          onClick={() => {
            if (!confirmAll) {
              setConfirmAll(true);
              return;
            }
            endAll.mutate(undefined, { onSuccess: () => navigate('/login', { replace: true }) });
          }}
        >
          {confirmAll ? t('profile.sessions.confirmEndAll') : t('profile.sessions.endAll')}
        </Button>
      </div>
      {confirmAll && (
        <p id="end-all-warning" className="text-sm text-warning">
          {t('profile.sessions.endAllWarning')}
        </p>
      )}
      {error && <ErrorState message={errorMessage(t, error)} />}
    </section>
  );
}

function SessionRow({
  session,
  disabled,
  onEnd,
}: {
  session: SessionView;
  disabled: boolean;
  onEnd: () => void;
}) {
  const { t, i18n } = useTranslation();
  const { browser, os } = describeUserAgent(session.userAgent);
  const name =
    browser && os
      ? t('profile.sessions.device', { browser, os })
      : (browser ?? os ?? t('profile.sessions.unknownDevice'));
  const when = new Intl.DateTimeFormat(i18n.language, {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(session.lastUsedAt));
  return (
    <li className="card-inner flex flex-wrap items-center justify-between gap-3 px-4 py-3">
      <div className="min-w-0">
        <p className="flex items-center gap-2 font-semibold">
          {name}
          {session.current && <Chip tone="action">{t('profile.sessions.current')}</Chip>}
        </p>
        <p className="text-sm text-text-muted">{t('profile.sessions.lastUsed', { when })}</p>
      </div>
      {!session.current && (
        <Button
          variant="ghost"
          disabled={disabled}
          aria-label={t('profile.sessions.endNamed', { name })}
          onClick={onEnd}
        >
          {t('profile.sessions.end')}
        </Button>
      )}
    </li>
  );
}

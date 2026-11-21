import { useEffect, useMemo, useRef, useState, type FormEvent, type ReactNode } from 'react';
import { useMutation, useQuery } from '@tanstack/react-query';
import { useTranslation } from 'react-i18next';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { Button, Icon, Wordmark } from '@/components/ui';
import { LegalLinks } from '@/components/shell/LegalLinks';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { DEFAULT_LANGUAGE, isLanguage } from '@/i18n';
import { authApi } from '@/lib/api/auth';
import { ApiError } from '@/lib/api/client';
import { cn } from '@/lib/cn';
import { useDebouncedValue } from '@/lib/useDebouncedValue';
import { useDeepWorkStore } from '@/stores/deepWorkStore';
import { SignupBrand, ProfilePreview } from './SignupBrand';
import {
  buildPayload,
  canGoTo,
  EMPTY_DRAFT,
  STEP_FIELDS,
  STEPS,
  validateStep,
  type Field,
  type SignupDraft,
  type Step,
} from './signupLogic';
import { AccountStep, ProfileStep, RoutineStep } from './SignupSteps';
import { uploadProfilePhoto } from './uploadPhoto';

/** Campo do servidor → campo da tela (e o passo dele). */
const SERVER_FIELDS: Record<string, Field> = {
  email: 'email',
  password: 'password',
  name: 'name',
  nickname: 'nickname',
  area: 'area',
  course: 'course',
  period: 'period',
  institution: 'institution',
  weeklyGoalHours: 'weeklyGoalHours',
  acceptTerms: 'acceptTerms',
  confirmAge: 'confirmAge',
};

/**
 * Cadastro em 3 passos (Conta, Perfil, Rotina). O rascunho fica só na memória deste componente:
 * recarregar recomeça, e a senha nunca vai para o localStorage. Tudo é enviado de uma vez no fim.
 */
export function SignupPage() {
  const { t, i18n } = useTranslation();
  const navigate = useNavigate();
  const [params] = useSearchParams();
  const [draft, setDraft] = useState<SignupDraft>(EMPTY_DRAFT);
  const [step, setStep] = useState<Step>('account');
  const [touched, setTouched] = useState<Set<Field>>(new Set());
  const [serverErrors, setServerErrors] = useState<Partial<Record<Field, string>>>({});
  const [photo, setPhoto] = useState<File | null>(null);
  const [verifySent, setVerifySent] = useState(false);
  const headingRef = useRef<HTMLHeadingElement>(null);
  const firstRender = useRef(true);

  const config = useQuery({
    queryKey: ['auth', 'config'],
    queryFn: authApi.config,
    staleTime: Infinity,
  });
  const course = useDebouncedValue(draft.course.trim(), 400);
  const suggestion = useQuery({
    queryKey: ['auth', 'suggest', draft.area, course],
    queryFn: () => authApi.suggestRoadmap(draft.area, course),
    enabled: !!(draft.area || course),
    staleTime: 5 * 60_000,
  });
  const photoUrl = useMemo(() => (photo ? URL.createObjectURL(photo) : undefined), [photo]);
  useEffect(() => () => (photoUrl ? URL.revokeObjectURL(photoUrl) : undefined), [photoUrl]);

  // Troca de passo: o foco vai para o título do passo (leitor de tela anuncia).
  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    headingRef.current?.focus();
  }, [step]);

  const index = STEPS.indexOf(step);
  const stepErrors = validateStep(step, draft);
  const error = (field: Field) => {
    if (serverErrors[field]) return serverErrors[field];
    const key = validateStep(stepOf(field), draft)[field];
    return key && touched.has(field) ? t(key) : undefined;
  };

  function set<K extends Field>(field: K, value: SignupDraft[K]) {
    setDraft((d) => ({ ...d, [field]: value }));
    setServerErrors((e) => (e[field] ? { ...e, [field]: undefined } : e));
  }

  const touch = (field: Field) => setTouched((s) => new Set(s).add(field));

  const signup = useMutation({
    mutationFn: async (d: SignupDraft) => {
      const locale = isLanguage(i18n.language) ? i18n.language : DEFAULT_LANGUAGE;
      const result = await authApi.register(buildPayload(d, locale, suggestion.data?.slug));
      if (result.kind === 'session' && photo && config.data?.profilePhotoEnabled) {
        // Foto opcional: se falhar, a conta continua criada (dá para enviar depois no Perfil).
        await uploadProfilePhoto(photo).catch(() => undefined);
      }
      return result;
    },
    onSuccess: (result) => {
      if (result.kind === 'verify') {
        setVerifySent(true);
        return;
      }
      // Ritmo de foco escolhido vira o modo padrão do Deep Work neste navegador.
      useDeepWorkStore.getState().setMode(draft.focusRhythm === 'FREE' ? 'FLOW' : 'POMODORO');
      navigate('/', { replace: true });
    },
    onError: (e) => {
      const fields = fieldErrors(e);
      const mapped: Partial<Record<Field, string>> = {};
      for (const [k, v] of Object.entries(fields)) {
        const f = SERVER_FIELDS[k];
        if (f) mapped[f] = t(`signup.server.${k}`, { defaultValue: v });
      }
      if (e instanceof ApiError && e.code === 'EMAIL_ALREADY_REGISTERED') {
        mapped.email = t('signup.server.emailTaken');
      }
      setServerErrors(mapped);
      const first = Object.keys(mapped)[0] as Field | undefined;
      if (first) setStep(stepOf(first));
    },
  });

  /** Avança se o passo vale; senão marca os campos e leva o foco ao primeiro erro. */
  function next(e?: FormEvent) {
    e?.preventDefault();
    setTouched((s) => new Set([...s, ...STEP_FIELDS[step]]));
    const errors = Object.keys(stepErrors) as Field[];
    if (errors.length > 0) {
      document.getElementById(`signup-${errors[0]}`)?.focus();
      return;
    }
    if (index < STEPS.length - 1) {
      setStep(STEPS[index + 1]!);
    } else {
      signup.mutate(draft);
    }
  }

  if (verifySent) {
    return (
      <Shell draft={draft} photoUrl={photoUrl}>
        <div role="status" className="flex flex-col gap-4">
          <h1 className="page-title text-[40px]">{t('signup.verify.title')}</h1>
          <p className="text-text-secondary">
            {t('signup.verify.body', { email: draft.email.trim() })}
          </p>
          <Link to="/login" className="font-semibold text-action hover:underline">
            {t('signup.toLogin')}
          </Link>
        </div>
      </Shell>
    );
  }

  return (
    <Shell draft={draft} photoUrl={photoUrl}>
      <div className="flex flex-col gap-2">
        <h1 className="page-title text-[40px]">{t('signup.title')}</h1>
        <p className="text-text-secondary">
          {t('signup.haveAccount')}{' '}
          <Link to="/login" className="font-semibold text-action hover:underline">
            {t('signup.toLogin')}
          </Link>
        </p>
      </div>
      {params.get('google') === 'no-account' && (
        <p role="status" className="rounded-inner border border-border px-4 py-3 text-sm">
          {t('signup.googleNoAccount')}
        </p>
      )}

      {/* Barra de etapas: só vai adiante se as anteriores valem; voltar sempre pode. */}
      <nav aria-label={t('signup.steps.label')}>
        <ol className="m-0 grid list-none grid-cols-3 gap-2 p-0">
          {STEPS.map((s, i) => {
            const reachable = i <= index || canGoTo(s, draft);
            return (
              <li key={s}>
                <button
                  type="button"
                  aria-current={s === step ? 'step' : undefined}
                  disabled={!reachable}
                  onClick={() => setStep(s)}
                  className={cn(
                    'flex min-h-11 w-full flex-col items-start rounded-inner border px-3 py-2 text-left text-sm disabled:cursor-not-allowed disabled:opacity-50',
                    s === step
                      ? 'border-action bg-action/10'
                      : i < index
                        ? 'border-action/40'
                        : 'border-border',
                  )}
                >
                  <span className="label text-text-muted">
                    {t('signup.steps.number', { n: i + 1 })}
                  </span>
                  <span className="flex items-center gap-1 font-semibold">
                    {i < index && <Icon name="check" size={14} className="text-action" />}
                    {t(`signup.steps.${s}`)}
                  </span>
                </button>
              </li>
            );
          })}
        </ol>
      </nav>

      {/* Prévia curta no celular (o painel lateral some). */}
      <div className="lg:hidden">
        {step !== 'account' && <ProfilePreview draft={draft} photoUrl={photoUrl} />}
      </div>

      <form onSubmit={next} noValidate className="flex flex-col gap-6">
        <h2 ref={headingRef} tabIndex={-1} className="card-title outline-none">
          {t(`signup.steps.${step}Title`)}
        </h2>
        {step === 'account' && (
          <AccountStep draft={draft} set={set} error={error} touch={touch} config={config.data} />
        )}
        {step === 'profile' && (
          <ProfileStep
            draft={draft}
            set={set}
            error={error}
            touch={touch}
            suggestion={suggestion.data}
            config={config.data}
            photoUrl={photoUrl}
            onPhoto={setPhoto}
          />
        )}
        {step === 'routine' && (
          <RoutineStep
            draft={draft}
            set={set}
            error={error}
            touch={touch}
            suggestion={suggestion.data}
          />
        )}

        {signup.isError && Object.keys(serverErrors).length === 0 && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, signup.error)}
          </p>
        )}

        <div className="flex flex-wrap items-center justify-between gap-3">
          {index > 0 ? (
            <Button variant="ghost" onClick={() => setStep(STEPS[index - 1]!)}>
              {t('signup.back')}
            </Button>
          ) : (
            <span />
          )}
          <Button
            type="submit"
            className="min-w-48"
            disabled={signup.isPending}
            aria-busy={signup.isPending || undefined}
          >
            {signup.isPending
              ? t('signup.creating')
              : index === STEPS.length - 1
                ? t('signup.create')
                : t('signup.continue')}
          </Button>
        </div>
      </form>
    </Shell>
  );
}

function stepOf(field: Field): Step {
  return (STEPS.find((s) => STEP_FIELDS[s].includes(field)) ?? 'account') as Step;
}

/** Duas áreas: marca à esquerda (tela larga) e formulário à direita (tela toda no celular). */
function Shell({
  draft,
  photoUrl,
  children,
}: {
  draft: SignupDraft;
  photoUrl?: string;
  children: ReactNode;
}) {
  const { t } = useTranslation();
  return (
    <div className="grid min-h-dvh lg:grid-cols-[minmax(0,5fr)_minmax(0,6fr)]">
      <SignupBrand draft={draft} photoUrl={photoUrl} />
      <main
        id="main"
        className="flex items-start justify-center px-4 py-8 sm:px-8 lg:items-center lg:py-12"
      >
        <div className="flex w-full max-w-xl flex-col gap-6">
          <div className="flex flex-col gap-1 lg:hidden">
            <Wordmark className="text-2xl" />
            <p className="text-sm text-text-secondary">{t('signup.brand.slogan')}</p>
          </div>
          {children}
          <LegalLinks className="justify-center text-xs" />
        </div>
      </main>
    </div>
  );
}

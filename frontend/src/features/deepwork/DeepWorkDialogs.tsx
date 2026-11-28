import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { Button, Dialog, ErrorState, SelectField, TextAreaField, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import { todayInput } from '@/features/stack/due';
import type { ActiveSession, DeepWorkSettings, StudySession } from '@/lib/api/deepwork';
import type { Subject } from '@/lib/api/stack';
import { notificationsSupported, requestNotificationPermission } from './alerts';
import { useSaveManualSession, useSaveSettings } from './queries';
import { useCompleteFocus } from './useFocusFlow';

const pad = (n: number) => String(n).padStart(2, '0');

/** Concluir a sessão com a "commit message" e a nota de foco opcionais. */
export function CompleteDialog({
  session,
  onClose,
}: {
  session: ActiveSession;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [note, setNote] = useState('');
  const [rating, setRating] = useState('');
  const { finish, isPending, error } = useCompleteFocus();

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    finish(session, { note: note.trim() || null, focusRating: rating ? Number(rating) : null });
    onClose();
  }

  return (
    <Dialog
      title={t('deepWork.completeTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="complete-form" disabled={isPending}>
            {t('deepWork.complete')}
          </Button>
        </>
      }
    >
      <form id="complete-form" onSubmit={onSubmit} className="flex flex-col gap-4">
        <TextAreaField
          label={t('deepWork.note')}
          hint={t('deepWork.noteHint')}
          value={note}
          maxLength={500}
          rows={3}
          data-autofocus
          onChange={(e) => setNote(e.target.value)}
        />
        <SelectField
          label={t('deepWork.focusRating')}
          value={rating}
          onChange={(e) => setRating(e.target.value)}
        >
          <option value="">{t('common.optional')}</option>
          {[1, 2, 3, 4, 5].map((n) => (
            <option key={n} value={n}>
              {t('deepWork.ratingOption', { n })}
            </option>
          ))}
        </SelectField>
        {error && <ErrorState message={errorMessage(t, error)} />}
      </form>
    </Dialog>
  );
}

/** Preferências do pomodoro, meta semanal e alertas. */
export function SettingsDialog({
  settings,
  onClose,
}: {
  settings: DeepWorkSettings;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const [form, setForm] = useState({
    focusMinutes: String(settings.focusMinutes),
    shortBreakMinutes: String(settings.shortBreakMinutes),
    longBreakMinutes: String(settings.longBreakMinutes),
    longBreakEvery: String(settings.longBreakEvery),
    weeklyGoalHours: String(Math.round((settings.weeklyGoalMinutes / 60) * 10) / 10),
  });
  const [sound, setSound] = useState(settings.soundEnabled);
  const [notifications, setNotifications] = useState(settings.notificationsEnabled);
  const [denied, setDenied] = useState(false);
  const save = useSaveSettings();
  const errors = fieldErrors(save.error);

  const field = (key: keyof typeof form) => ({
    value: form[key],
    onChange: (e: { target: { value: string } }) =>
      setForm((f) => ({ ...f, [key]: e.target.value })),
  });

  async function toggleNotifications(checked: boolean) {
    setDenied(false);
    if (!checked) {
      setNotifications(false);
      return;
    }
    // Pedido de permissão precisa vir de um gesto do usuário.
    const granted = await requestNotificationPermission();
    setNotifications(granted);
    setDenied(!granted);
  }

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    save.mutate(
      {
        focusMinutes: Number(form.focusMinutes),
        shortBreakMinutes: Number(form.shortBreakMinutes),
        longBreakMinutes: Number(form.longBreakMinutes),
        longBreakEvery: Number(form.longBreakEvery),
        weeklyGoalMinutes: Math.round(Number(form.weeklyGoalHours) * 60),
        soundEnabled: sound,
        notificationsEnabled: notifications,
      },
      { onSuccess: onClose },
    );
  }

  return (
    <Dialog
      title={t('deepWork.settings.title')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="settings-form" disabled={save.isPending}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="settings-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <div className="grid gap-4 sm:grid-cols-2">
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            max={180}
            label={t('deepWork.settings.focus')}
            error={errors.focusMinutes}
            data-autofocus
            {...field('focusMinutes')}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            max={60}
            label={t('deepWork.settings.shortBreak')}
            error={errors.shortBreakMinutes}
            {...field('shortBreakMinutes')}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            max={120}
            label={t('deepWork.settings.longBreak')}
            error={errors.longBreakMinutes}
            {...field('longBreakMinutes')}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            max={12}
            label={t('deepWork.settings.longEvery')}
            error={errors.longBreakEvery}
            {...field('longBreakEvery')}
          />
          <TextField
            type="number"
            inputMode="decimal"
            min={0}
            max={168}
            step={0.5}
            label={t('deepWork.settings.weeklyGoal')}
            error={errors.weeklyGoalMinutes}
            {...field('weeklyGoalHours')}
          />
        </div>
        <fieldset className="flex flex-col gap-2">
          <legend className="field-label mb-2 block">{t('deepWork.settings.alerts')}</legend>
          <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
            <input
              type="checkbox"
              className="h-5 w-5 accent-[var(--action)]"
              checked={sound}
              onChange={(e) => setSound(e.target.checked)}
            />
            {t('deepWork.settings.sound')}
          </label>
          <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
            <input
              type="checkbox"
              className="h-5 w-5 accent-[var(--action)]"
              checked={notifications}
              disabled={!notificationsSupported()}
              onChange={(e) => void toggleNotifications(e.target.checked)}
            />
            {t('deepWork.settings.notifications')}
          </label>
          {denied && (
            <p className="text-sm text-warning">{t('deepWork.settings.notificationsDenied')}</p>
          )}
        </fieldset>
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

/** Registrar sessão passada ou editar uma encerrada. */
export function ManualSessionDialog({
  session,
  subjects,
  defaultSubjectId,
  onClose,
}: {
  session?: StudySession;
  subjects: Subject[];
  defaultSubjectId?: string;
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const initial = session ? new Date(session.startedAt) : null;
  const [subjectId, setSubjectId] = useState(
    session?.subjectId ?? defaultSubjectId ?? subjects[0]?.id ?? '',
  );
  const [date, setDate] = useState(
    initial
      ? `${initial.getFullYear()}-${pad(initial.getMonth() + 1)}-${pad(initial.getDate())}`
      : todayInput(),
  );
  const [time, setTime] = useState(
    initial ? `${pad(initial.getHours())}:${pad(initial.getMinutes())}` : '09:00',
  );
  const [minutes, setMinutes] = useState(
    session ? String(Math.round(session.effectiveSeconds / 60)) : '60',
  );
  const [note, setNote] = useState(session?.note ?? '');
  const save = useSaveManualSession();
  const errors = fieldErrors(save.error);

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    const [y, m, d] = date.split('-').map(Number);
    const [h, min] = time.split(':').map(Number);
    const startedAt = new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1, h ?? 0, min ?? 0).toISOString();
    save.mutate(
      {
        id: session?.id,
        input: {
          subjectId,
          taskId: session?.taskId ?? null,
          startedAt,
          durationMinutes: Number(minutes),
          note: note.trim() || null,
        },
      },
      { onSuccess: onClose },
    );
  }

  return (
    <Dialog
      title={session ? t('deepWork.manual.editTitle') : t('deepWork.manual.title')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button type="submit" form="manual-form" disabled={save.isPending || !subjectId}>
            {t('common.save')}
          </Button>
        </>
      }
    >
      <form id="manual-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <SelectField
          label={t('deepWork.subject')}
          value={subjectId}
          error={errors.subjectId}
          onChange={(e) => setSubjectId(e.target.value)}
          data-autofocus
        >
          {subjects.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </SelectField>
        <div className="grid gap-4 sm:grid-cols-3">
          <TextField
            type="date"
            label={t('deepWork.manual.date')}
            value={date}
            required
            error={errors.startedAt}
            onChange={(e) => setDate(e.target.value)}
          />
          <TextField
            type="time"
            label={t('deepWork.manual.time')}
            value={time}
            required
            onChange={(e) => setTime(e.target.value)}
          />
          <TextField
            type="number"
            inputMode="numeric"
            min={1}
            max={720}
            label={t('deepWork.manual.minutes')}
            value={minutes}
            required
            error={errors.durationMinutes}
            onChange={(e) => setMinutes(e.target.value)}
          />
        </div>
        <TextAreaField
          label={t('deepWork.note')}
          value={note}
          maxLength={500}
          rows={2}
          onChange={(e) => setNote(e.target.value)}
        />
        {save.error && <ErrorState message={errorMessage(t, save.error)} />}
      </form>
    </Dialog>
  );
}

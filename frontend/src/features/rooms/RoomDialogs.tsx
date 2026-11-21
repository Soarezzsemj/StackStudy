import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Button, Dialog, SelectField, TextAreaField, TextField } from '@/components/ui';
import { errorMessage } from '@/features/auth/errorMessage';
import {
  REPORT_REASONS,
  roomsApi,
  type MemberPresence,
  type ReportReason,
  type RoomDetail,
  type RoomMode,
  type RoomVisibility,
} from '@/lib/api/rooms';
import { useAuthStore } from '@/stores/authStore';
import { useCreateRoom, useJoinRoom, useRoomAction, useUpdateMe, useUpdateRoom } from './queries';
import { formatCodeInput, validNickname } from './roomLogic';

function Checkbox({
  label,
  checked,
  onChange,
}: {
  label: string;
  checked: boolean;
  onChange: (v: boolean) => void;
}) {
  return (
    <label className="flex min-h-11 cursor-pointer items-center gap-3 text-sm">
      <input
        type="checkbox"
        className="h-5 w-5 accent-[var(--action)]"
        checked={checked}
        onChange={(e) => onChange(e.target.checked)}
      />
      {label}
    </label>
  );
}

function Footer({ onClose, busy, label }: { onClose: () => void; busy: boolean; label: string }) {
  const { t } = useTranslation();
  return (
    <div className="flex justify-end gap-2">
      <Button variant="ghost" onClick={onClose}>
        {t('common.cancel')}
      </Button>
      <Button type="submit" form="room-dialog-form" disabled={busy}>
        {label}
      </Button>
    </div>
  );
}

// ---------------------------------------------------------------------------
// criar e entrar
// ---------------------------------------------------------------------------

export function CreateRoomDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const create = useCreateRoom();
  const [name, setName] = useState('');
  const [nickname, setNickname] = useState(useAuthStore.getState().user?.nickname ?? '');
  const [visibility, setVisibility] = useState<RoomVisibility>('PRIVATE');
  const [mode, setMode] = useState<RoomMode>('FREE');
  const [goal, setGoal] = useState('');
  const [touched, setTouched] = useState(false);
  const nameOk = name.trim().length >= 3 && !/[<>]/.test(name);
  const nickOk = validNickname(nickname);

  function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!nameOk || !nickOk) return;
    create.mutate(
      {
        name: name.trim(),
        nickname: nickname.trim(),
        visibility,
        defaultMode: mode,
        ...(goal ? { weeklyGoalHours: Number(goal) } : {}),
      },
      {
        onSuccess: (room) => {
          onClose();
          navigate(`/rooms/${room.id}`);
        },
      },
    );
  }

  return (
    <Dialog
      title={t('rooms.create.title')}
      onClose={onClose}
      footer={<Footer onClose={onClose} busy={create.isPending} label={t('rooms.create.submit')} />}
    >
      <form id="room-dialog-form" onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <TextField
          label={t('rooms.create.name')}
          value={name}
          maxLength={60}
          onChange={(e) => setName(e.target.value)}
          error={touched && !nameOk ? t('rooms.create.nameError') : undefined}
        />
        <NicknameField value={nickname} onChange={setNickname} touched={touched} />
        <SelectField
          label={t('rooms.create.visibility')}
          value={visibility}
          onChange={(e) => setVisibility(e.target.value as RoomVisibility)}
          hint={t(`rooms.visibility.hint.${visibility}`)}
        >
          <option value="PRIVATE">{t('rooms.visibility.PRIVATE')}</option>
          <option value="PUBLIC">{t('rooms.visibility.PUBLIC')}</option>
        </SelectField>
        <SelectField
          label={t('rooms.create.mode')}
          value={mode}
          onChange={(e) => setMode(e.target.value as RoomMode)}
        >
          <option value="FREE">{t('rooms.mode.FREE')}</option>
          <option value="POMODORO">{t('rooms.mode.POMODORO')}</option>
        </SelectField>
        <TextField
          label={t('rooms.create.goal')}
          type="number"
          min={1}
          max={1000}
          inputMode="numeric"
          value={goal}
          onChange={(e) => setGoal(e.target.value)}
        />
        {create.error && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, create.error)}
          </p>
        )}
      </form>
    </Dialog>
  );
}

function NicknameField({
  value,
  onChange,
  touched,
}: {
  value: string;
  onChange: (v: string) => void;
  touched: boolean;
}) {
  const { t } = useTranslation();
  return (
    <TextField
      label={t('rooms.nickname.label')}
      hint={t('rooms.nickname.hint')}
      value={value}
      maxLength={24}
      autoComplete="off"
      onChange={(e) => onChange(e.target.value)}
      error={touched && !validNickname(value) ? t('rooms.nickname.error') : undefined}
    />
  );
}

export function JoinRoomDialog({ onClose }: { onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const join = useJoinRoom();
  const [code, setCode] = useState('');
  const [nickname, setNickname] = useState(useAuthStore.getState().user?.nickname ?? '');
  const [touched, setTouched] = useState(false);

  function submit(e: FormEvent) {
    e.preventDefault();
    setTouched(true);
    if (!validNickname(nickname) || code.replace(/-/g, '').length !== 12) return;
    join.mutate(
      { code, nickname: nickname.trim() },
      {
        onSuccess: (room) => {
          onClose();
          navigate(`/rooms/${room.id}`);
        },
      },
    );
  }

  return (
    <Dialog
      title={t('rooms.join.title')}
      onClose={onClose}
      footer={<Footer onClose={onClose} busy={join.isPending} label={t('rooms.join.submit')} />}
    >
      <form id="room-dialog-form" onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <TextField
          label={t('rooms.join.code')}
          hint={t('rooms.join.codeHint')}
          value={code}
          autoComplete="off"
          spellCheck={false}
          className="tabular uppercase"
          onChange={(e) => setCode(formatCodeInput(e.target.value))}
        />
        <NicknameField value={nickname} onChange={setNickname} touched={touched} />
        {join.error && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, join.error)}
          </p>
        )}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// minhas preferências e sair
// ---------------------------------------------------------------------------

export function MySettingsDialog({ room, onClose }: { room: RoomDetail; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const update = useUpdateMe(room.id);
  const leave = useRoomAction((deleteHistory: boolean) => roomsApi.leave(room.id, deleteHistory));
  const wipe = useRoomAction(() => roomsApi.deleteHistory(room.id));
  const [nickname, setNickname] = useState(room.me.nickname);
  const [showSubject, setShowSubject] = useState(room.me.showSubject);
  const [hide, setHide] = useState(room.me.hideFromRanking);
  const [deleteHistory, setDeleteHistory] = useState(false);

  function submit(e: FormEvent) {
    e.preventDefault();
    if (!validNickname(nickname)) return;
    update.mutate(
      { nickname: nickname.trim(), showSubject, hideFromRanking: hide },
      { onSuccess: onClose },
    );
  }

  return (
    <Dialog
      title={t('rooms.me.title')}
      onClose={onClose}
      footer={<Footer onClose={onClose} busy={update.isPending} label={t('common.save')} />}
    >
      <form id="room-dialog-form" onSubmit={submit} className="flex flex-col gap-3" noValidate>
        <NicknameField value={nickname} onChange={setNickname} touched />
        <Checkbox
          label={t('rooms.me.showSubject')}
          checked={showSubject}
          onChange={setShowSubject}
        />
        <Checkbox label={t('rooms.me.hideFromRanking')} checked={hide} onChange={setHide} />
        <hr className="my-2 border-border" />
        <p className="text-sm text-text-secondary">{t('rooms.me.historyHint')}</p>
        <Button
          variant="ghost"
          className="self-start"
          disabled={wipe.isPending}
          onClick={() => wipe.mutate(undefined)}
        >
          {t('rooms.me.deleteHistory')}
        </Button>
        {wipe.isSuccess && <p className="text-sm text-action">{t('rooms.me.historyDeleted')}</p>}
        {!room.owner && (
          <>
            <Checkbox
              label={t('rooms.me.leaveWithHistory')}
              checked={deleteHistory}
              onChange={setDeleteHistory}
            />
            <Button
              variant="secondary"
              className="self-start"
              disabled={leave.isPending}
              onClick={() =>
                leave.mutate(deleteHistory, {
                  onSuccess: () => {
                    onClose();
                    navigate('/rooms');
                  },
                })
              }
            >
              {t('rooms.me.leave')}
            </Button>
          </>
        )}
        {(update.error ?? leave.error) && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, update.error ?? leave.error)}
          </p>
        )}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// dono
// ---------------------------------------------------------------------------

export function OwnerSettingsDialog({ room, onClose }: { room: RoomDetail; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const update = useUpdateRoom(room.id);
  const regenerate = useRoomAction(() => roomsApi.regenerateCode(room.id));
  const remove = useRoomAction(() => roomsApi.remove(room.id));
  const [name, setName] = useState(room.name);
  const [visibility, setVisibility] = useState(room.visibility);
  const [mode, setMode] = useState(room.defaultMode);
  const [max, setMax] = useState(String(room.maxMembers));
  const [goal, setGoal] = useState(String(room.weeklyGoalHours ?? ''));
  const [focus, setFocus] = useState(String(room.pomodoro.focusMinutes));
  const [shortBreak, setShortBreak] = useState(String(room.pomodoro.shortBreakMinutes));
  const [longBreak, setLongBreak] = useState(String(room.pomodoro.longBreakMinutes));
  const [every, setEvery] = useState(String(room.pomodoro.longBreakEvery));
  const [restart, setRestart] = useState(false);
  const [confirmDelete, setConfirmDelete] = useState(false);

  function submit(e: FormEvent) {
    e.preventDefault();
    update.mutate(
      {
        name: name.trim(),
        visibility,
        defaultMode: mode,
        maxMembers: Number(max),
        weeklyGoalHours: goal ? Number(goal) : 0,
        focusMinutes: Number(focus),
        shortBreakMinutes: Number(shortBreak),
        longBreakMinutes: Number(longBreak),
        longBreakEvery: Number(every),
        restartCycle: restart,
      },
      { onSuccess: onClose },
    );
  }

  const num = (
    label: string,
    value: string,
    set: (v: string) => void,
    min: number,
    maxV: number,
  ) => (
    <TextField
      label={label}
      type="number"
      min={min}
      max={maxV}
      inputMode="numeric"
      value={value}
      onChange={(e) => set(e.target.value)}
    />
  );

  return (
    <Dialog
      title={t('rooms.owner.title')}
      onClose={onClose}
      footer={<Footer onClose={onClose} busy={update.isPending} label={t('common.save')} />}
    >
      <form id="room-dialog-form" onSubmit={submit} className="flex flex-col gap-4" noValidate>
        <TextField
          label={t('rooms.create.name')}
          value={name}
          maxLength={60}
          onChange={(e) => setName(e.target.value)}
        />
        <div className="grid gap-4 sm:grid-cols-2">
          <SelectField
            label={t('rooms.create.visibility')}
            value={visibility}
            onChange={(e) => setVisibility(e.target.value as RoomVisibility)}
          >
            <option value="PRIVATE">{t('rooms.visibility.PRIVATE')}</option>
            <option value="PUBLIC">{t('rooms.visibility.PUBLIC')}</option>
          </SelectField>
          <SelectField
            label={t('rooms.create.mode')}
            value={mode}
            onChange={(e) => setMode(e.target.value as RoomMode)}
          >
            <option value="FREE">{t('rooms.mode.FREE')}</option>
            <option value="POMODORO">{t('rooms.mode.POMODORO')}</option>
          </SelectField>
          {num(t('rooms.owner.capacity'), max, setMax, 2, 50)}
          {num(t('rooms.create.goal'), goal, setGoal, 0, 1000)}
        </div>
        <fieldset className="grid gap-4 sm:grid-cols-2">
          <legend className="label mb-2 text-text-muted">{t('rooms.owner.pomodoro')}</legend>
          {num(t('rooms.owner.focus'), focus, setFocus, 5, 120)}
          {num(t('rooms.owner.short'), shortBreak, setShortBreak, 1, 30)}
          {num(t('rooms.owner.long'), longBreak, setLongBreak, 1, 60)}
          {num(t('rooms.owner.every'), every, setEvery, 1, 12)}
        </fieldset>
        <Checkbox label={t('rooms.owner.restart')} checked={restart} onChange={setRestart} />

        <div className="card-inner flex flex-col gap-2 p-4">
          <p className="label text-text-muted">{t('rooms.owner.code')}</p>
          <p className="tabular text-xl font-semibold">{room.inviteCode}</p>
          <p className="text-xs text-text-secondary">{t('rooms.owner.codeHint')}</p>
          <Button
            variant="secondary"
            className="self-start"
            disabled={regenerate.isPending}
            onClick={() => regenerate.mutate(undefined)}
          >
            {t('rooms.owner.regenerate')}
          </Button>
        </div>

        {confirmDelete ? (
          <div className="flex flex-wrap items-center gap-2" role="alert">
            <p className="text-sm">{t('rooms.owner.deleteConfirm')}</p>
            <Button
              variant="secondary"
              className="text-danger"
              disabled={remove.isPending}
              onClick={() =>
                remove.mutate(undefined, {
                  onSuccess: () => {
                    onClose();
                    navigate('/rooms');
                  },
                })
              }
            >
              {t('rooms.owner.deleteYes')}
            </Button>
          </div>
        ) : (
          <Button variant="ghost" className="self-start" onClick={() => setConfirmDelete(true)}>
            {t('rooms.owner.delete')}
          </Button>
        )}
        {(update.error ?? remove.error) && (
          <p role="alert" className="text-sm text-danger">
            {errorMessage(t, update.error ?? remove.error)}
          </p>
        )}
      </form>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------
// denúncia (lista fechada de motivos)
// ---------------------------------------------------------------------------

export function ReportDialog({
  room,
  members,
  onClose,
}: {
  room: RoomDetail;
  members: MemberPresence[];
  onClose: () => void;
}) {
  const { t } = useTranslation();
  const report = useRoomAction(
    (input: { reason: ReportReason; detail?: string; userId?: string }) =>
      roomsApi.report(room.id, input),
  );
  const [reason, setReason] = useState<ReportReason>('SPAM');
  const [userId, setUserId] = useState('');
  const [detail, setDetail] = useState('');

  function submit(e: FormEvent) {
    e.preventDefault();
    report.mutate({
      reason,
      ...(detail.trim() ? { detail: detail.trim() } : {}),
      ...(userId ? { userId } : {}),
    });
  }

  return (
    <Dialog
      title={t('rooms.report.title')}
      onClose={onClose}
      footer={
        report.isSuccess ? (
          <div className="flex justify-end">
            <Button onClick={onClose}>{t('common.close')}</Button>
          </div>
        ) : (
          <Footer onClose={onClose} busy={report.isPending} label={t('rooms.report.submit')} />
        )
      }
    >
      {report.isSuccess ? (
        <p role="status">{t('rooms.report.thanks')}</p>
      ) : (
        <form id="room-dialog-form" onSubmit={submit} className="flex flex-col gap-4" noValidate>
          <SelectField
            label={t('rooms.report.reason')}
            value={reason}
            onChange={(e) => setReason(e.target.value as ReportReason)}
          >
            {REPORT_REASONS.map((r) => (
              <option key={r} value={r}>
                {t(`rooms.report.reasons.${r}`)}
              </option>
            ))}
          </SelectField>
          <SelectField
            label={t('rooms.report.who')}
            value={userId}
            onChange={(e) => setUserId(e.target.value)}
          >
            <option value="">{t('rooms.report.theRoom')}</option>
            {members
              .filter((m) => !m.me)
              .map((m) => (
                <option key={m.userId} value={m.userId}>
                  {m.nickname}
                </option>
              ))}
          </SelectField>
          <TextAreaField
            label={t('rooms.report.detail')}
            hint={t('rooms.report.detailHint')}
            value={detail}
            maxLength={300}
            rows={3}
            onChange={(e) => setDetail(e.target.value)}
          />
          {report.error && (
            <p role="alert" className="text-sm text-danger">
              {errorMessage(t, report.error)}
            </p>
          )}
        </form>
      )}
    </Dialog>
  );
}

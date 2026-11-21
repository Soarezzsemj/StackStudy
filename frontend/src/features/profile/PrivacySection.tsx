import { useState, type FormEvent } from 'react';
import { useTranslation } from 'react-i18next';
import { useNavigate } from 'react-router-dom';
import { Button, Card, Dialog, ErrorState, Icon, TextField } from '@/components/ui';
import { errorMessage, fieldErrors } from '@/features/auth/errorMessage';
import type { User } from '@/lib/api/types';
import { downloadJson, exportFileName } from './helpers';
import { useDeleteAccount, useExportData } from './queries';
import { SavedNote } from './SavedNote';

/** LGPD: exportar todos os dados em JSON e excluir a conta. */
export function PrivacySection({ user }: { user: User }) {
  const { t } = useTranslation();
  const exportData = useExportData();
  const [deleting, setDeleting] = useState(false);

  return (
    <Card
      title={t('profile.privacy.title')}
      aria-label={t('profile.privacy.title')}
      id="privacidade"
    >
      <div className="flex flex-col gap-8">
        <div className="flex flex-col gap-3">
          <h3 className="label text-text-secondary">{t('profile.privacy.exportTitle')}</h3>
          <p className="text-sm text-text-secondary">{t('profile.privacy.exportHint')}</p>
          <div className="flex flex-wrap items-center gap-3">
            <Button
              variant="secondary"
              disabled={exportData.isPending}
              onClick={() =>
                exportData.mutate(undefined, {
                  onSuccess: (data) => downloadJson(data, exportFileName()),
                })
              }
            >
              <Icon name="archive" size={18} />
              {exportData.isPending ? t('profile.privacy.exporting') : t('profile.privacy.export')}
            </Button>
            <SavedNote show={exportData.isSuccess}>{t('profile.privacy.exported')}</SavedNote>
          </div>
          {exportData.error && <ErrorState message={errorMessage(t, exportData.error)} />}
        </div>

        <div className="flex flex-col gap-3 border-t border-border pt-6">
          <h3 className="label text-danger">{t('profile.privacy.deleteTitle')}</h3>
          <p className="text-sm text-text-secondary">{t('profile.privacy.deleteHint')}</p>
          <div>
            <Button variant="ghost" className="text-danger" onClick={() => setDeleting(true)}>
              <Icon name="trash" size={18} />
              {t('profile.privacy.delete')}
            </Button>
          </div>
        </div>
      </div>
      {deleting && <DeleteAccountDialog user={user} onClose={() => setDeleting(false)} />}
    </Card>
  );
}

function DeleteAccountDialog({ user, onClose }: { user: User; onClose: () => void }) {
  const { t } = useTranslation();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const remove = useDeleteAccount();
  const errors = fieldErrors(remove.error);
  // O botão só libera com o e-mail da conta digitado igual (sem diferenciar maiúsculas).
  const matches = email.trim().toLowerCase() === user.email.toLowerCase();

  function onSubmit(e: FormEvent) {
    e.preventDefault();
    if (!matches || !password) return;
    remove.mutate(
      { email: email.trim(), currentPassword: password },
      { onSuccess: () => navigate('/login', { replace: true }) },
    );
  }

  return (
    <Dialog
      title={t('profile.privacy.deleteDialogTitle')}
      onClose={onClose}
      footer={
        <>
          <Button variant="secondary" onClick={onClose}>
            {t('common.cancel')}
          </Button>
          <Button
            type="submit"
            form="delete-account-form"
            disabled={!matches || !password || remove.isPending}
          >
            {t('profile.privacy.deleteConfirm')}
          </Button>
        </>
      }
    >
      <form id="delete-account-form" noValidate onSubmit={onSubmit} className="flex flex-col gap-4">
        <p className="text-sm text-text-secondary">
          {t('profile.privacy.deleteDialogBody', { email: user.email })}
        </p>
        <TextField
          type="email"
          autoComplete="off"
          label={t('profile.privacy.typeEmail', { email: user.email })}
          value={email}
          data-autofocus
          error={errors.email && t('profile.privacy.emailMismatch')}
          onChange={(e) => {
            remove.reset();
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
            remove.reset();
            setPassword(e.target.value);
          }}
        />
        {remove.error && !errors.email && !errors.currentPassword && (
          <ErrorState message={errorMessage(t, remove.error)} />
        )}
      </form>
    </Dialog>
  );
}

package com.stackstudy.account.photo;

import java.util.UUID;

/**
 * Onde guardar a foto JÁ processada ({@link ProfilePhotoProcessor}). Nenhuma implementação existe:
 * o plano grátis não tem armazenamento de arquivos (o disco do Render some a cada deploy). Para
 * ligar {@code PROFILE_PHOTO_ENABLED}, crie um bean (ex.: um bucket S3/R2) e sirva só o arquivo
 * processado; o boot falha se a flag estiver ligada sem esse bean.
 */
public interface ProfilePhotoStorage {

  void save(UUID userId, byte[] png);
}

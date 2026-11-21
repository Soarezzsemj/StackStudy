package com.stackstudy.admin;

/**
 * E-mail mascarado para o painel: 2 primeiras letras + {@code ***} + domínio ({@code
 * ana.souza@uni.br} → {@code an***@uni.br}). Parte local com 1–2 letras mostra só a primeira, para
 * nunca exibir o endereço inteiro. Sem "@", vira {@code ***}.
 */
public final class EmailMask {

  private EmailMask() {}

  public static String mask(String email) {
    if (email == null) {
      return "***";
    }
    int at = email.lastIndexOf('@');
    if (at <= 0 || at == email.length() - 1) {
      return "***";
    }
    String local = email.substring(0, at);
    String domain = email.substring(at + 1);
    int visible = local.length() > 2 ? 2 : 1;
    return local.substring(0, visible) + "***@" + domain;
  }
}

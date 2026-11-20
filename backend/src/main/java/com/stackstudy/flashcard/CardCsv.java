package com.stackstudy.flashcard;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * CSV de cartas: colunas {@code frente, verso, tags} (tags separadas por espaço; coluna opcional).
 * Segue o RFC 4180: campos com vírgula, aspas ou quebra de linha vão entre aspas, e aspas dentro
 * deles viram {@code ""}. Cabeçalho {@code front,back[,tags]} (ou {@code frente,verso[,tags]}) é
 * aceito e pulado. Puro e testado à parte.
 */
public final class CardCsv {

  /** Tamanho máximo do texto do CSV (caracteres). */
  public static final int MAX_CHARS = 1_000_000;

  /** Linhas de dados por importação. */
  public static final int MAX_ROWS = 2000;

  public static final int MAX_FIELD = 5000;
  public static final int MAX_TAGS = 10;
  public static final int MAX_TAG_LENGTH = 40;

  private CardCsv() {}

  /** Carta válida lida de uma linha. */
  public record Draft(String front, String back, List<String> tags) {}

  /**
   * Problema numa linha. {@code code} é fixo (o frontend traduz).
   *
   * @param line número da linha no arquivo (1 = primeira)
   */
  public record LineError(int line, String code) {}

  public record Result(List<Draft> drafts, List<LineError> errors) {}

  /** Lê e valida; linhas ruins viram erro e não impedem as outras. */
  public static Result parse(String text) {
    List<Draft> drafts = new ArrayList<>();
    List<LineError> errors = new ArrayList<>();
    List<Row> rows;
    try {
      rows = rows(text);
    } catch (UnclosedQuoteException e) {
      // Aspas sem fechar engolem o resto do arquivo: aproveita o que veio antes.
      rows = e.parsed;
      errors.add(new LineError(e.line, "UNCLOSED_QUOTE"));
    }
    int dataRows = 0;
    for (int i = 0; i < rows.size(); i++) {
      Row row = rows.get(i);
      if (row.blank()) {
        continue;
      }
      if (i == 0 && isHeader(row.fields)) {
        continue;
      }
      dataRows++;
      if (dataRows > MAX_ROWS) {
        errors.add(new LineError(row.line, "TOO_MANY_ROWS"));
        break;
      }
      String error = validate(row.fields);
      if (error != null) {
        errors.add(new LineError(row.line, error));
      } else {
        drafts.add(
            new Draft(
                row.fields.get(0).strip(),
                row.fields.get(1).strip(),
                row.fields.size() > 2 ? tags(row.fields.get(2)) : List.of()));
      }
    }
    errors.sort((a, b) -> Integer.compare(a.line(), b.line()));
    return new Result(drafts, errors);
  }

  /** Normaliza tags: separadas por espaço, vírgula ou ponto e vírgula; minúsculas; sem repetir. */
  public static List<String> tags(String raw) {
    Set<String> out = new LinkedHashSet<>();
    for (String part : raw.split("[\\s,;]+")) {
      String tag = part.strip().toLowerCase(Locale.ROOT);
      if (!tag.isEmpty()) {
        out.add(tag);
      }
    }
    return List.copyOf(out);
  }

  /** Erro da linha ou null se estiver ok. */
  static String validate(List<String> fields) {
    if (fields.size() < 2) {
      return "MISSING_COLUMNS";
    }
    if (fields.size() > 3) {
      return "TOO_MANY_COLUMNS";
    }
    if (fields.get(0).isBlank()) {
      return "FRONT_REQUIRED";
    }
    if (fields.get(1).isBlank()) {
      return "BACK_REQUIRED";
    }
    if (fields.get(0).strip().length() > MAX_FIELD || fields.get(1).strip().length() > MAX_FIELD) {
      return "TEXT_TOO_LONG";
    }
    if (fields.size() > 2) {
      List<String> tags = tags(fields.get(2));
      if (tags.size() > MAX_TAGS || tags.stream().anyMatch(t -> t.length() > MAX_TAG_LENGTH)) {
        return "INVALID_TAGS";
      }
    }
    return null;
  }

  private static boolean isHeader(List<String> fields) {
    if (fields.size() < 2) {
      return false;
    }
    String a = fields.get(0).strip().toLowerCase(Locale.ROOT);
    String b = fields.get(1).strip().toLowerCase(Locale.ROOT);
    return (a.equals("front") && b.equals("back")) || (a.equals("frente") && b.equals("verso"));
  }

  // -------------------------------------------------------------------------
  // escrita
  // -------------------------------------------------------------------------

  /** CSV com cabeçalho {@code front,back,tags}, linhas separadas por CRLF (RFC 4180). */
  public static String write(List<Draft> cards) {
    StringBuilder out = new StringBuilder("front,back,tags\r\n");
    for (Draft card : cards) {
      out.append(quote(card.front()))
          .append(',')
          .append(quote(card.back()))
          .append(',')
          .append(quote(String.join(" ", card.tags())))
          .append("\r\n");
    }
    return out.toString();
  }

  static String quote(String value) {
    boolean needs =
        value.contains(",")
            || value.contains("\"")
            || value.contains("\n")
            || value.contains("\r")
            || (!value.isEmpty()
                && (Character.isWhitespace(value.charAt(0))
                    || Character.isWhitespace(value.charAt(value.length() - 1))));
    return needs ? '"' + value.replace("\"", "\"\"") + '"' : value;
  }

  // -------------------------------------------------------------------------
  // leitura (RFC 4180)
  // -------------------------------------------------------------------------

  private record Row(int line, List<String> fields) {
    boolean blank() {
      return fields.stream().allMatch(String::isBlank);
    }
  }

  private static final class UnclosedQuoteException extends RuntimeException {
    final int line;
    final transient List<Row> parsed;

    UnclosedQuoteException(int line, List<Row> parsed) {
      super(null, null, false, false);
      this.line = line;
      this.parsed = parsed;
    }
  }

  private static List<Row> rows(String text) {
    List<Row> rows = new ArrayList<>();
    List<String> fields = new ArrayList<>();
    StringBuilder field = new StringBuilder();
    int line = 1;
    int rowStart = 1;
    int quoteStart = 0;
    boolean quoted = false;
    int i = 0;
    // BOM do Excel no começo.
    if (text.startsWith("﻿")) {
      i = 1;
    }
    while (i < text.length()) {
      char c = text.charAt(i);
      if (quoted) {
        if (c == '"') {
          if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
            field.append('"');
            i += 2;
            continue;
          }
          quoted = false;
        } else {
          if (c == '\n') {
            line++;
          }
          field.append(c);
        }
        i++;
        continue;
      }
      if (c == '"' && field.isEmpty()) {
        quoted = true;
        quoteStart = line;
      } else if (c == ',') {
        fields.add(field.toString());
        field.setLength(0);
      } else if (c == '\r' || c == '\n') {
        fields.add(field.toString());
        field.setLength(0);
        rows.add(new Row(rowStart, fields));
        fields = new ArrayList<>();
        if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
          i++;
        }
        line++;
        rowStart = line;
      } else {
        field.append(c);
      }
      i++;
    }
    if (quoted) {
      throw new UnclosedQuoteException(quoteStart, rows);
    }
    if (!field.isEmpty() || !fields.isEmpty()) {
      fields.add(field.toString());
      rows.add(new Row(rowStart, fields));
    }
    return rows;
  }
}

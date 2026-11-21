package com.stackstudy.roadmap;

import com.stackstudy.roadmap.RoadmapCatalog.RoadmapContent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sugestão de roadmap no cadastro (pura, testada): o curso digitado pesa mais que a área.
 *
 * <ul>
 *   <li>Curso: palavras com 4+ letras do título ou da área do roadmap que aparecem no curso (sem
 *       acento e sem diferenciar maiúsculas) valem 2 cada.
 *   <li>Área: se a área escolhida corresponde à área do roadmap, vale 1.
 * </ul>
 *
 * Sem pontos, nenhuma sugestão (o aviso some na tela). Empate: o de menor posição no catálogo.
 */
public final class RoadmapMatcher {

  /** Área do cadastro → áreas de roadmap correspondentes (os valores do seed, sem acento). */
  static final Map<String, Set<String>> AREA_TO_ROADMAP_AREAS =
      Map.of(
          "TECH_EXACT", Set.of("ciencia da computacao", "engenharia", "matematica", "fisica"),
          "HEALTH", Set.of("medicina", "enfermagem", "odontologia", "saude"),
          "LAW", Set.of("direito"),
          "HUMANITIES", Set.of("historia", "letras", "psicologia", "pedagogia"),
          "BUSINESS", Set.of("administracao", "economia", "contabilidade"),
          "ARTS_DESIGN", Set.of("design", "arquitetura", "artes"));

  /** Palavras comuns que não ajudam a casar curso e roadmap. */
  private static final Set<String> STOP = Set.of("ciencia", "ciencias", "curso", "bacharelado");

  private RoadmapMatcher() {}

  public static Optional<RoadmapContent> suggest(
      List<RoadmapContent> roadmaps, String area, String course) {
    String c = course == null ? "" : RoadmapService.fold(course);
    Set<String> areaMatches =
        area == null ? Set.of() : AREA_TO_ROADMAP_AREAS.getOrDefault(area, Set.of());
    RoadmapContent best = null;
    int bestScore = 0;
    for (RoadmapContent r : roadmaps) {
      int score = 0;
      for (String word : words(r.title() + " " + r.area())) {
        if (!c.isEmpty() && c.contains(word)) {
          score += 2;
        }
      }
      if (areaMatches.contains(RoadmapService.fold(r.area()))) {
        score += 1;
      }
      if (score > bestScore) {
        best = r;
        bestScore = score;
      }
    }
    return Optional.ofNullable(best);
  }

  private static Set<String> words(String text) {
    return Arrays.stream(RoadmapService.fold(text).split("[^a-z0-9]+"))
        .filter(w -> w.length() >= 4 && !STOP.contains(w))
        .collect(Collectors.toSet());
  }
}

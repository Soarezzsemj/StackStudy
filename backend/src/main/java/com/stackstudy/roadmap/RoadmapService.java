package com.stackstudy.roadmap;

import com.stackstudy.common.error.ApiException;
import com.stackstudy.common.error.RateLimitedException;
import com.stackstudy.common.security.CurrentUser;
import com.stackstudy.common.security.RateLimitProperties;
import com.stackstudy.common.security.RateLimiter;
import com.stackstudy.roadmap.RoadmapCatalog.NodeContent;
import com.stackstudy.roadmap.RoadmapCatalog.RoadmapContent;
import com.stackstudy.roadmap.RoadmapCatalog.StageContent;
import com.stackstudy.roadmap.RoadmapEnums.ProgressStatus;
import com.stackstudy.roadmap.RoadmapProgressCalculator.NodeProgress;
import com.stackstudy.roadmap.RoadmapRepository.Follow;
import com.stackstudy.roadmap.RoadmapRepository.ProgressRow;
import com.stackstudy.roadmap.dto.RoadmapRequests;
import com.stackstudy.roadmap.dto.RoadmapResponses.CockpitRoadmap;
import com.stackstudy.roadmap.dto.RoadmapResponses.Following;
import com.stackstudy.roadmap.dto.RoadmapResponses.Node;
import com.stackstudy.roadmap.dto.RoadmapResponses.Resource;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapDetail;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapItem;
import com.stackstudy.roadmap.dto.RoadmapResponses.RoadmapList;
import com.stackstudy.roadmap.dto.RoadmapResponses.Stage;
import com.stackstudy.roadmap.dto.RoadmapResponses.Summary;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.Normalizer;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roadmaps: conteúdo do {@link RoadmapCatalog} (memória) + progresso do usuário logado. O acesso a
 * cada etapa vem sempre de {@link RoadmapAccessService}.
 */
@Service
public class RoadmapService {

  private static final BigDecimal SECONDS_PER_HOUR = BigDecimal.valueOf(3600);

  private final RoadmapCatalog catalog;
  private final RoadmapRepository repository;
  private final RoadmapAccessService access;
  private final RoadmapProperties properties;
  private final RateLimiter limiter;
  private final RateLimitProperties rateLimits;
  private final CurrentUser currentUser;
  private final Clock clock;

  public RoadmapService(
      RoadmapCatalog catalog,
      RoadmapRepository repository,
      RoadmapAccessService access,
      RoadmapProperties properties,
      RateLimiter limiter,
      RateLimitProperties rateLimits,
      CurrentUser currentUser,
      Clock clock) {
    this.catalog = catalog;
    this.repository = repository;
    this.access = access;
    this.properties = properties;
    this.limiter = limiter;
    this.rateLimits = rateLimits;
    this.currentUser = currentUser;
    this.clock = clock;
  }

  // leitura ----------------------------------------------------------------------

  @Transactional(readOnly = true)
  public RoadmapList list(String area, String q) {
    UUID userId = currentUser.id();
    Map<UUID, Follow> follows = new HashMap<>();
    for (Follow f : repository.follows(userId)) {
      follows.put(f.roadmapId(), f);
    }
    Map<UUID, NodeProgress> progress =
        follows.isEmpty() ? Map.of() : repository.allProgress(userId);
    String needle = q == null || q.isBlank() ? null : fold(q.trim());
    String areaFilter = area == null || area.isBlank() ? null : area.trim();
    List<RoadmapItem> items = new ArrayList<>();
    for (RoadmapContent r : catalog.all()) {
      if (areaFilter != null && !r.area().equalsIgnoreCase(areaFilter)) {
        continue;
      }
      if (needle != null
          && !fold(r.title()).contains(needle)
          && !fold(r.description()).contains(needle)
          && !fold(r.area()).contains(needle)) {
        continue;
      }
      Follow f = follows.get(r.id());
      List<NodeContent> required = r.required();
      BigDecimal hours =
          required.stream()
              .map(NodeContent::estimatedHours)
              .reduce(BigDecimal.ZERO, BigDecimal::add)
              .setScale(1, RoundingMode.HALF_UP);
      BigDecimal percent =
          f == null
              ? null
              : RoadmapProgressCalculator.summarize(r.trail(), progress, null, today()).percent();
      items.add(
          new RoadmapItem(
              r.id(),
              r.slug(),
              r.title(),
              r.description(),
              r.area(),
              r.language(),
              r.access(),
              r.version(),
              r.sample(),
              r.stages().size(),
              required.size(),
              hours,
              f != null,
              f != null && f.primary(),
              percent));
    }
    List<String> areas =
        catalog.all().stream().map(RoadmapContent::area).distinct().sorted().toList();
    return new RoadmapList(items, areas, access.premiumEnabled());
  }

  @Transactional(readOnly = true)
  public RoadmapDetail detail(String slug) {
    return buildDetail(currentUser.id(), find(slug));
  }

  // seguir -----------------------------------------------------------------------

  @Transactional
  public RoadmapDetail follow(String slug, RoadmapRequests.Follow request) {
    UUID userId = currentUser.id();
    RoadmapContent r = find(slug);
    ensureFollowing(userId, r);
    if (request != null && Boolean.TRUE.equals(request.primary())) {
      repository.makePrimary(userId, r.id());
    }
    return buildDetail(userId, r);
  }

  /** Para de seguir (o progresso fica guardado: seguir de novo recupera). Idempotente. */
  @Transactional
  public void unfollow(String slug) {
    UUID userId = currentUser.id();
    RoadmapContent r = find(slug);
    Optional<Follow> f = repository.follow(userId, r.id());
    if (f.isEmpty()) {
      return;
    }
    repository.deleteFollow(userId, r.id());
    if (f.get().primary()) {
      // O mais antigo que sobrou vira o principal.
      repository.follows(userId).stream()
          .findFirst()
          .ifPresent(next -> repository.makePrimary(userId, next.roadmapId()));
    }
  }

  // progresso --------------------------------------------------------------------

  @Transactional
  public RoadmapDetail updateProgress(String slug, UUID nodeId, RoadmapRequests.Progress request) {
    UUID userId = currentUser.id();
    RoadmapContent r = find(slug);
    NodeContent node = r.nodesById().get(nodeId);
    if (node == null) {
      // Nó de outro roadmap (ou inexistente): não pertence a este.
      throw ApiException.notFound("Roadmap node");
    }
    access.requireOpen(userId, r, r.stageByNode().get(nodeId));
    ensureFollowing(userId, r);
    repository.upsertProgress(
        userId, nodeId, request.status(), request.manualHours(), clock.instant());

    Map<UUID, NodeProgress> progress = toProgress(repository.progress(userId, r.id()));
    Follow f = repository.follow(userId, r.id()).orElseThrow();
    UUID current = f.currentNodeId();
    UUID next = current;
    Predicate<NodeContent> open = openPredicate(userId, r);
    if (request.status() == ProgressStatus.DOING) {
      next = nodeId;
    } else if (request.status() == ProgressStatus.DONE) {
      boolean currentDone =
          current == null || progress.getOrDefault(current, NodeProgress.EMPTY).done();
      if (currentDone || current.equals(nodeId)) {
        next = RoadmapProgressCalculator.next(r.trail(), nodeId, progress, open);
      }
    } else if (current == null) {
      next = RoadmapProgressCalculator.next(r.trail(), null, progress, open);
    }
    if (!Objects.equals(next, current)) {
      repository.setCurrentNode(userId, r.id(), next);
    }
    return buildDetail(userId, r);
  }

  @Transactional
  public RoadmapDetail updateGoal(String slug, RoadmapRequests.Goal request) {
    UUID userId = currentUser.id();
    RoadmapContent r = find(slug);
    ensureFollowing(userId, r);
    repository.setGoal(userId, r.id(), request.weeklyGoalHours());
    return buildDetail(userId, r);
  }

  // sugestões --------------------------------------------------------------------

  /** Guarda a sugestão para o admin ler (nenhum e-mail é enviado). Texto puro, com limite. */
  @Transactional
  public UUID suggest(RoadmapRequests.Suggestion request) {
    UUID userId = currentUser.id();
    String title = plainText("title", request.title());
    String area = plainText("area", request.area());
    String details = plainText("details", request.details());
    if (title == null || title.length() < 3) {
      throw ApiException.invalidField("title", "size must be between 3 and 120");
    }
    if (rateLimits.enabled()) {
      RoadmapProperties.Limit rule = properties.suggestions();
      RateLimiter.Decision decision =
          limiter.acquire("roadmap-suggestion:" + userId, rule.limit(), rule.window());
      if (!decision.allowed()) {
        throw new RateLimitedException(decision.retryAfterSeconds());
      }
    }
    return repository.insertSuggestion(userId, title, area, details);
  }

  // cadastro (Fase 11) ---------------------------------------------------------------

  /**
   * Cadastro: começa a seguir o roadmap sugerido (sem usuário no contexto). Slug desconhecido =
   * nada.
   */
  @Transactional
  public boolean followForUser(UUID userId, String slug) {
    Optional<RoadmapContent> r = catalog.bySlug(slug);
    r.ifPresent(content -> ensureFollowing(userId, content));
    return r.isPresent();
  }

  /** Roadmap mais compatível com a área e o curso (público, só leitura). */
  public Optional<RoadmapSuggestion> suggest(String area, String course) {
    return RoadmapMatcher.suggest(catalog.all(), area, course)
        .map(r -> new RoadmapSuggestion(r.slug(), r.title(), r.area()));
  }

  public record RoadmapSuggestion(String slug, String title, String area) {}

  // integração ---------------------------------------------------------------------

  /** Deep Work: o tópico ligado à sessão precisa existir (roadmap ativo) e estar aberto. */
  public void requireFocusNode(UUID userId, UUID nodeId) {
    RoadmapContent r =
        catalog.byNode(nodeId).orElseThrow(() -> ApiException.notFound("Roadmap node"));
    access.requireOpen(userId, r, r.stageByNode().get(nodeId));
  }

  /** Deep Work: recalcula as horas de foco dos tópicos afetados (null é ignorado). */
  public void refreshFocus(UUID userId, UUID... nodeIds) {
    for (UUID id : nodeIds) {
      if (id != null && catalog.byNode(id).isPresent()) {
        repository.recomputeFocus(userId, id);
      }
    }
  }

  /** Cockpit: roadmap principal e o próximo tópico (null se não segue nenhum). Uma consulta. */
  public CockpitRoadmap cockpit(UUID userId) {
    return repository
        .primaryWithProgress(userId)
        .flatMap(
            row ->
                catalog
                    .byId(row.roadmapId())
                    .map(
                        r -> {
                          RoadmapProgressCalculator.Summary s =
                              RoadmapProgressCalculator.summarize(
                                  r.trail(), row.progress(), row.weeklyGoalHours(), today());
                          UUID current =
                              resolveCurrent(
                                  r, row.currentNodeId(), row.progress(), openPredicate(userId, r));
                          NodeContent node = current == null ? null : r.nodesById().get(current);
                          StageContent stage =
                              current == null ? null : r.stageByNode().get(current);
                          return new CockpitRoadmap(
                              r.slug(),
                              r.title(),
                              s.percent(),
                              s.doneTopics(),
                              s.totalTopics(),
                              current,
                              node == null ? null : node.title(),
                              stage == null ? null : stage.title(),
                              stage == null ? null : stage.position());
                        }))
        .orElse(null);
  }

  // detalhes -------------------------------------------------------------------------

  private RoadmapDetail buildDetail(UUID userId, RoadmapContent r) {
    Optional<Follow> follow = repository.follow(userId, r.id());
    Map<UUID, ProgressRow> rows = repository.progress(userId, r.id());
    Map<UUID, NodeProgress> progress = toProgress(rows);
    Predicate<NodeContent> open = openPredicate(userId, r);
    UUID current =
        follow.map(f -> resolveCurrent(r, f.currentNodeId(), progress, open)).orElse(null);
    Integer goal = follow.map(Follow::weeklyGoalHours).orElse(null);
    Summary summary =
        Summary.from(RoadmapProgressCalculator.summarize(r.trail(), progress, goal, today()));

    List<Stage> stages = new ArrayList<>();
    for (StageContent s : r.stages()) {
      boolean locked = !access.canOpen(userId, r, s);
      List<Node> nodes = new ArrayList<>();
      int topics = 0;
      int done = 0;
      for (NodeContent n : s.allNodes()) {
        ProgressRow p = rows.get(n.id());
        ProgressStatus status = p == null ? ProgressStatus.TODO : p.status();
        if (!n.optional()) {
          topics++;
          if (status == ProgressStatus.DONE) {
            done++;
          }
        }
        nodes.add(
            new Node(
                n.id(),
                n.parentId(),
                n.kind(),
                n.position(),
                n.title(),
                locked ? null : n.description(),
                n.optional(),
                n.estimatedHours(),
                n.side(),
                locked
                    ? List.of()
                    : n.resources().stream()
                        .map(x -> new Resource(x.id(), x.title(), x.url(), x.kind()))
                        .toList(),
                status,
                p == null ? BigDecimal.ZERO.setScale(2) : p.hoursSpent(),
                p == null ? BigDecimal.ZERO.setScale(2) : p.manualHours(),
                p == null
                    ? BigDecimal.ZERO.setScale(2)
                    : BigDecimal.valueOf(p.focusSeconds())
                        .divide(SECONDS_PER_HOUR, 2, RoundingMode.HALF_UP),
                p == null ? null : p.completedAt(),
                n.id().equals(current)));
      }
      stages.add(
          new Stage(
              s.id(),
              s.position(),
              s.title(),
              s.description(),
              s.access(),
              locked,
              topics,
              done,
              nodes));
    }
    return new RoadmapDetail(
        r.id(),
        r.slug(),
        r.title(),
        r.description(),
        r.area(),
        r.language(),
        r.access(),
        r.version(),
        r.sample(),
        access.premiumEnabled(),
        follow
            .map(f -> new Following(f.startedAt(), f.primary(), f.weeklyGoalHours(), current))
            .orElse(null),
        summary,
        stages);
  }

  /** Tópico atual guardado, se ainda vale (existe, não concluído, aberto); senão, o próximo. */
  private static UUID resolveCurrent(
      RoadmapContent r,
      UUID stored,
      Map<UUID, NodeProgress> progress,
      Predicate<NodeContent> open) {
    if (stored != null) {
      NodeContent n = r.nodesById().get(stored);
      if (n != null && !progress.getOrDefault(stored, NodeProgress.EMPTY).done() && open.test(n)) {
        return stored;
      }
    }
    return RoadmapProgressCalculator.next(r.trail(), null, progress, open);
  }

  private void ensureFollowing(UUID userId, RoadmapContent r) {
    List<Follow> follows = repository.follows(userId);
    if (follows.stream().anyMatch(f -> f.roadmapId().equals(r.id()))) {
      return;
    }
    Map<UUID, NodeProgress> progress = toProgress(repository.progress(userId, r.id()));
    UUID first =
        RoadmapProgressCalculator.next(r.trail(), null, progress, openPredicate(userId, r));
    repository.insertFollow(userId, r.id(), clock.instant(), first);
    if (follows.stream().noneMatch(Follow::primary)) {
      repository.makePrimary(userId, r.id());
    }
  }

  private Predicate<NodeContent> openPredicate(UUID userId, RoadmapContent r) {
    Map<UUID, Boolean> byStage = new HashMap<>();
    for (StageContent s : r.stages()) {
      byStage.put(s.id(), access.canOpen(userId, r, s));
    }
    return n -> byStage.getOrDefault(n.stageId(), false);
  }

  private RoadmapContent find(String slug) {
    return catalog.bySlug(slug).orElseThrow(() -> ApiException.notFound("Roadmap"));
  }

  private LocalDate today() {
    return LocalDate.now(clock.withZone(ZoneOffset.UTC));
  }

  private static Map<UUID, NodeProgress> toProgress(Map<UUID, ProgressRow> rows) {
    Map<UUID, NodeProgress> out = new HashMap<>();
    rows.forEach((id, row) -> out.put(id, row.toProgress()));
    return out;
  }

  /** Texto puro de uma linha ou parágrafo: sem HTML ({@code <} ou {@code >}) nem controle. */
  private static String plainText(String field, String value) {
    if (value == null) {
      return null;
    }
    if (value.indexOf('<') >= 0 || value.indexOf('>') >= 0) {
      throw ApiException.invalidField(field, "must be plain text (no HTML)");
    }
    String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\n\\t]]", "").trim();
    return cleaned.isEmpty() ? null : cleaned;
  }

  /** Minúsculas e sem acentos, para a busca ("historia" acha "História"). */
  static String fold(String value) {
    return Normalizer.normalize(value, Normalizer.Form.NFD)
        .replaceAll("\\p{M}", "")
        .toLowerCase(Locale.ROOT);
  }
}

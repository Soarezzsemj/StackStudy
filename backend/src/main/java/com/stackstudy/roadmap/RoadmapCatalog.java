package com.stackstudy.roadmap;

import com.stackstudy.roadmap.RoadmapEnums.Access;
import com.stackstudy.roadmap.RoadmapEnums.NodeKind;
import com.stackstudy.roadmap.RoadmapEnums.ResourceKind;
import com.stackstudy.roadmap.RoadmapEnums.Side;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Conteúdo dos roadmaps em memória. Ele é estático e versionado por migration (só muda com deploy),
 * então é lido uma vez por instância (4 consultas) e servido daqui sem tocar no banco. Só roadmaps
 * ativos entram.
 */
@Component
public class RoadmapCatalog {

  public record ResourceContent(UUID id, String title, String url, ResourceKind kind) {}

  /**
   * @param subs subtópicos (só nos MAIN), na ordem
   */
  public record NodeContent(
      UUID id,
      UUID stageId,
      UUID parentId,
      int position,
      String title,
      String description,
      NodeKind kind,
      boolean optional,
      BigDecimal estimatedHours,
      Side side,
      List<ResourceContent> resources,
      List<NodeContent> subs) {}

  /**
   * @param nodes tópicos principais na ordem (cada um com os subtópicos)
   */
  public record StageContent(
      UUID id,
      int position,
      String title,
      String description,
      Access access,
      List<NodeContent> nodes) {

    /** Todos os nós da etapa, na ordem da trilha: tópico e depois os subtópicos dele. */
    public List<NodeContent> allNodes() {
      List<NodeContent> out = new ArrayList<>();
      for (NodeContent main : nodes) {
        out.add(main);
        out.addAll(main.subs());
      }
      return out;
    }
  }

  /**
   * @param trail todos os nós na ordem da trilha (etapa → tópico → subtópicos)
   */
  public record RoadmapContent(
      UUID id,
      String slug,
      String title,
      String description,
      String area,
      String language,
      int position,
      Access access,
      int version,
      boolean sample,
      List<StageContent> stages,
      List<NodeContent> trail,
      Map<UUID, NodeContent> nodesById,
      Map<UUID, StageContent> stageByNode) {

    /** Nós que contam no progresso (os opcionais ficam de fora do percentual). */
    public List<NodeContent> required() {
      return trail.stream().filter(n -> !n.optional()).toList();
    }
  }

  /** Índices do catálogo inteiro. */
  public record Snapshot(
      List<RoadmapContent> roadmaps,
      Map<String, RoadmapContent> bySlug,
      Map<UUID, RoadmapContent> byNode) {}

  private final JdbcTemplate jdbc;
  private final AtomicReference<Snapshot> cache = new AtomicReference<>();

  public RoadmapCatalog(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  public List<RoadmapContent> all() {
    return snapshot().roadmaps();
  }

  public Optional<RoadmapContent> bySlug(String slug) {
    return Optional.ofNullable(snapshot().bySlug().get(slug));
  }

  public Optional<RoadmapContent> byId(UUID id) {
    return snapshot().roadmaps().stream().filter(r -> r.id().equals(id)).findFirst();
  }

  /** Roadmap (ativo) dono do nó. */
  public Optional<RoadmapContent> byNode(UUID nodeId) {
    return Optional.ofNullable(snapshot().byNode().get(nodeId));
  }

  /** Descarta o cache (testes que inserem conteúdo; em produção o conteúdo só muda com deploy). */
  public void reload() {
    cache.set(null);
  }

  private Snapshot snapshot() {
    Snapshot current = cache.get();
    if (current == null) {
      // Duas leituras simultâneas no primeiro acesso só carregam duas vezes (sem trava).
      current = load();
      cache.set(current);
    }
    return current;
  }

  private record Row(
      UUID id,
      UUID stageId,
      UUID parentId,
      int position,
      String title,
      String description,
      NodeKind kind,
      boolean optional,
      BigDecimal hours,
      Side side) {}

  private record StageRow(
      UUID id, UUID roadmapId, int position, String title, String description, Access access) {}

  private Snapshot load() {
    List<RoadmapContent> base =
        jdbc.query(
            "SELECT id, slug, title, description, area, language, position, access, version, sample"
                + " FROM roadmaps WHERE active ORDER BY position, title",
            (rs, i) ->
                new RoadmapContent(
                    rs.getObject("id", UUID.class),
                    rs.getString("slug"),
                    rs.getString("title"),
                    rs.getString("description"),
                    rs.getString("area"),
                    rs.getString("language"),
                    rs.getInt("position"),
                    Access.valueOf(rs.getString("access")),
                    rs.getInt("version"),
                    rs.getBoolean("sample"),
                    List.of(),
                    List.of(),
                    Map.of(),
                    Map.of()));
    List<StageRow> stages =
        jdbc.query(
            "SELECT s.id, s.roadmap_id, s.position, s.title, s.description, s.access"
                + " FROM roadmap_stages s JOIN roadmaps r ON r.id = s.roadmap_id"
                + " WHERE r.active ORDER BY s.position",
            (rs, i) ->
                new StageRow(
                    rs.getObject("id", UUID.class),
                    rs.getObject("roadmap_id", UUID.class),
                    rs.getInt("position"),
                    rs.getString("title"),
                    rs.getString("description"),
                    Access.valueOf(rs.getString("access"))));
    List<Row> nodes =
        jdbc.query(
            "SELECT n.id, n.stage_id, n.parent_id, n.position, n.title, n.description, n.kind,"
                + " n.optional, n.estimated_hours, n.side FROM roadmap_nodes n"
                + " JOIN roadmap_stages s ON s.id = n.stage_id"
                + " JOIN roadmaps r ON r.id = s.roadmap_id WHERE r.active"
                + " ORDER BY n.position, n.title",
            (rs, i) -> {
              String side = rs.getString("side");
              return new Row(
                  rs.getObject("id", UUID.class),
                  rs.getObject("stage_id", UUID.class),
                  rs.getObject("parent_id", UUID.class),
                  rs.getInt("position"),
                  rs.getString("title"),
                  rs.getString("description"),
                  NodeKind.valueOf(rs.getString("kind")),
                  rs.getBoolean("optional"),
                  rs.getBigDecimal("estimated_hours"),
                  side == null ? null : Side.valueOf(side));
            });
    Map<UUID, List<ResourceContent>> resources = new LinkedHashMap<>();
    jdbc.query(
        "SELECT x.id, x.node_id, x.title, x.url, x.kind FROM roadmap_node_resources x"
            + " ORDER BY x.position, x.title",
        rs -> {
          resources
              .computeIfAbsent(rs.getObject("node_id", UUID.class), k -> new ArrayList<>())
              .add(
                  new ResourceContent(
                      rs.getObject("id", UUID.class),
                      rs.getString("title"),
                      rs.getString("url"),
                      ResourceKind.valueOf(rs.getString("kind"))));
        });

    // Subtópicos por pai e principais por etapa (as listas já vêm ordenadas por posição).
    Map<UUID, List<NodeContent>> subsByParent = new LinkedHashMap<>();
    for (Row r : nodes) {
      if (r.kind() == NodeKind.SUB) {
        subsByParent
            .computeIfAbsent(r.parentId(), k -> new ArrayList<>())
            .add(toNode(r, resources, List.of()));
      }
    }
    Map<UUID, List<NodeContent>> mainsByStage = new LinkedHashMap<>();
    for (Row r : nodes) {
      if (r.kind() == NodeKind.MAIN) {
        mainsByStage
            .computeIfAbsent(r.stageId(), k -> new ArrayList<>())
            .add(toNode(r, resources, List.copyOf(subsByParent.getOrDefault(r.id(), List.of()))));
      }
    }

    List<RoadmapContent> out = new ArrayList<>();
    Map<String, RoadmapContent> bySlug = new LinkedHashMap<>();
    Map<UUID, RoadmapContent> byNode = new LinkedHashMap<>();
    for (RoadmapContent r : base) {
      List<StageContent> rs =
          stages.stream()
              .filter(s -> s.roadmapId().equals(r.id()))
              .sorted(Comparator.comparingInt(StageRow::position))
              .map(
                  s ->
                      new StageContent(
                          s.id(),
                          s.position(),
                          s.title(),
                          s.description(),
                          s.access(),
                          List.copyOf(mainsByStage.getOrDefault(s.id(), List.of()))))
              .toList();
      List<NodeContent> trail = new ArrayList<>();
      Map<UUID, NodeContent> nodesById = new LinkedHashMap<>();
      Map<UUID, StageContent> stageByNode = new LinkedHashMap<>();
      for (StageContent s : rs) {
        for (NodeContent n : s.allNodes()) {
          trail.add(n);
          nodesById.put(n.id(), n);
          stageByNode.put(n.id(), s);
        }
      }
      RoadmapContent full =
          new RoadmapContent(
              r.id(),
              r.slug(),
              r.title(),
              r.description(),
              r.area(),
              r.language(),
              r.position(),
              r.access(),
              r.version(),
              r.sample(),
              rs,
              List.copyOf(trail),
              Map.copyOf(nodesById),
              Map.copyOf(stageByNode));
      out.add(full);
      bySlug.put(full.slug(), full);
      for (UUID nodeId : nodesById.keySet()) {
        byNode.put(nodeId, full);
      }
    }
    return new Snapshot(List.copyOf(out), Map.copyOf(bySlug), Map.copyOf(byNode));
  }

  private static NodeContent toNode(
      Row r, Map<UUID, List<ResourceContent>> resources, List<NodeContent> subs) {
    return new NodeContent(
        r.id(),
        r.stageId(),
        r.parentId(),
        r.position(),
        r.title(),
        r.description(),
        r.kind(),
        r.optional(),
        r.hours(),
        r.side(),
        List.copyOf(resources.getOrDefault(r.id(), List.of())),
        subs);
  }
}

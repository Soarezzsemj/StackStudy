package com.stackstudy.room;

import com.stackstudy.room.RoomRepository.MemberRow;
import com.stackstudy.room.RoomRepository.RoomRow;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Cache em memória de sala + membros + bloqueios, para que presença e heartbeat não toquem no
 * banco. Carrega sob demanda (2 consultas por sala, 1 por usuário nos bloqueios) e é invalidado em
 * toda escrita que muda a sala, os membros ou os bloqueios. Vale por instância (plano grátis: uma
 * só; com várias, ver DEPLOY.md "Salas de estudo").
 */
@Component
public class RoomDirectory {

  /** Sala ativa com os membros (incluindo removidos, marcados como banned). */
  public record Room(RoomRow row, Map<UUID, MemberRow> members) {

    public Optional<MemberRow> member(UUID userId) {
      MemberRow m = members.get(userId);
      return m == null || m.banned() ? Optional.empty() : Optional.of(m);
    }

    public List<MemberRow> activeMembers() {
      return members.values().stream().filter(m -> !m.banned()).toList();
    }
  }

  private static final int MAX_ROOMS = 2_000;
  private static final int MAX_USERS = 20_000;

  private final RoomRepository repository;
  private final Map<UUID, Room> rooms =
      Collections.synchronizedMap(
          new LinkedHashMap<>(64, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<UUID, Room> eldest) {
              return size() > MAX_ROOMS;
            }
          });
  private final Map<UUID, Set<UUID>> blocks = new ConcurrentHashMap<>();

  public RoomDirectory(RoomRepository repository) {
    this.repository = repository;
  }

  /** Sala ativa (do cache ou do banco). Inativa ou inexistente = vazio. */
  public Optional<Room> room(UUID roomId) {
    Room cached = rooms.get(roomId);
    if (cached != null) {
      return Optional.of(cached);
    }
    Optional<RoomRow> row = repository.room(roomId).filter(RoomRow::active);
    if (row.isEmpty()) {
      return Optional.empty();
    }
    Map<UUID, MemberRow> members = new LinkedHashMap<>();
    for (MemberRow m : repository.members(roomId)) {
      members.put(m.userId(), m);
    }
    Room room = new Room(row.get(), Collections.unmodifiableMap(members));
    rooms.put(roomId, room);
    return Optional.of(room);
  }

  public void evict(UUID roomId) {
    rooms.remove(roomId);
  }

  /** Quem a pessoa bloqueou e quem a bloqueou. */
  public Set<UUID> blockRelations(UUID userId) {
    Set<UUID> cached = blocks.get(userId);
    if (cached != null) {
      return cached;
    }
    if (blocks.size() >= MAX_USERS) {
      blocks.clear();
    }
    Set<UUID> loaded = Set.copyOf(repository.blockRelations(userId));
    blocks.put(userId, loaded);
    return loaded;
  }

  public boolean blockedBetween(UUID a, UUID b) {
    return blockRelations(a).contains(b);
  }

  public void evictBlocks(UUID a, UUID b) {
    blocks.remove(a);
    blocks.remove(b);
  }

  /** Testes que mexem no banco por fora. */
  public void clear() {
    rooms.clear();
    blocks.clear();
  }
}

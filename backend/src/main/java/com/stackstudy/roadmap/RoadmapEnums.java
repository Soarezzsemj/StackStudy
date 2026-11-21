package com.stackstudy.roadmap;

/** Enums dos roadmaps (os mesmos valores dos CHECKs da V10). */
public final class RoadmapEnums {

  private RoadmapEnums() {}

  /** Grátis ou Premium. Enquanto ROADMAPS_PREMIUM_ENABLED=false, tudo abre. */
  public enum Access {
    FREE,
    PREMIUM
  }

  /** MAIN = tópico da trilha (centro); SUB = subtópico ligado a um MAIN (numa lateral). */
  public enum NodeKind {
    MAIN,
    SUB
  }

  public enum Side {
    LEFT,
    RIGHT
  }

  public enum ProgressStatus {
    TODO,
    DOING,
    DONE
  }

  public enum ResourceKind {
    ARTICLE,
    VIDEO,
    BOOK,
    COURSE,
    EXERCISE,
    OTHER
  }
}

package vn.unicom.thi360.exam.domain;

/**
 * How a section treats MAPID: {@code ALL} takes the whole subject and compiles nothing (the rules stay in the
 * JSON), {@code MATRIX} compiles {@link MapidRule}s. A section without a mode keeps the legacy scope and quotas.
 */
public enum MapidMode {
    ALL,
    MATRIX
}

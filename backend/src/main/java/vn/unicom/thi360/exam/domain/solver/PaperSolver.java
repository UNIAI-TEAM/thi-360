package vn.unicom.thi360.exam.domain.solver;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.function.Function;

/**
 * Bộ giải sinh đề của module exam (spec 5.4, D15): dùng chung cho kiểm tra cấu hình, xem trước và sinh đề.
 *
 * <p>Đầu vào là một đường lựa chọn (các khối bắt buộc cộng một lựa chọn của mỗi phần CHOICE), không phải
 * từng khối riêng. Một câu, một câu con chỉ được dùng một lần trên cả đường; bộ câu hỏi và Điền khuyết
 * luôn lấy nguyên vẹn và không được vượt chỉ tiêu.
 *
 * <p>Thuật toán: trường hợp đơn giản (mọi khối chỉ có ứng viên một đơn vị và chỉ tiêu một chiều, không
 * năng lực, nhóm MAPID hay khối chùm) giải chính xác bằng một mạng luồng cho cả đường; mọi trường hợp
 * khác dùng quay lui có cắt nhánh. Thứ tự duyệt cố định ở chế độ {@link SolveMode.Check}; xáo theo hạt
 * giống ở chế độ {@link SolveMode.Generate}.
 *
 * <p>Thuần Java: không Spring, không DB. Hạn thời gian tính cho cả lời gọi (đọc ứng viên, liệt kê đường,
 * giải) và được kiểm qua {@link Clock} truyền vào để test được.
 */
public final class PaperSolver {

    private final SolverLimits limits;
    private final Clock clock;

    public PaperSolver() {
        this(SolverLimits.defaults(), Clock.systemUTC());
    }

    public PaperSolver(SolverLimits limits, Clock clock) {
        this.limits = Objects.requireNonNull(limits);
        this.clock = Objects.requireNonNull(clock);
    }

    /**
     * Giải một đường với ứng viên đã có sẵn, song song với {@code path.blocks()}.
     */
    public PathResult solve(SolvePath path, List<List<PathCandidate>> candidatesByBlock, SolveMode mode) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(mode);
        if (candidatesByBlock == null || candidatesByBlock.size() != path.blocks().size()) {
            throw new IllegalArgumentException("candidatesByBlock must be parallel to path.blocks()");
        }
        Map<BlockQuota, List<PathCandidate>> byBlock = new HashMap<>();
        for (int i = 0; i < path.blocks().size(); i++) {
            byBlock.put(path.blocks().get(i), candidatesByBlock.get(i));
        }
        return solve(path, byBlock::get, mode);
    }

    /**
     * Giải một đường, lấy ứng viên từng khối qua {@code candidatesFor} (được gọi một lần mỗi khối).
     */
    public PathResult solve(SolvePath path, Function<BlockQuota, List<PathCandidate>> candidatesFor,
            SolveMode mode) {
        Objects.requireNonNull(path);
        Objects.requireNonNull(mode);
        Instant deadline = clock.instant().plus(limits.timeout());
        Runnable check = () -> {
            if (!clock.instant().isBefore(deadline)) {
                throw new DeadlineHit();
            }
        };
        return solvePath(path, memoized(candidatesFor), mode, check);
    }

    /**
     * Giải mọi đường lựa chọn của cấu trúc (liệt kê kiểu lặp, tối đa {@code limits.maxPaths}).
     */
    public SolveOutcome solveAll(List<SolvePart> parts,
            Function<BlockQuota, List<PathCandidate>> candidatesFor, SolveMode mode) {
        Objects.requireNonNull(parts);
        Objects.requireNonNull(mode);
        Instant deadline = clock.instant().plus(limits.timeout());
        Runnable check = () -> {
            if (!clock.instant().isBefore(deadline)) {
                throw new DeadlineHit();
            }
        };
        Function<BlockQuota, List<PathCandidate>> fetch = memoized(candidatesFor);
        PathEnumerator enumerator = new PathEnumerator(parts);
        long total = enumerator.total();
        if (total == 0) {
            return new SolveOutcome(SolveStatus.INVALID, null, List.of());
        }
        if (total > limits.maxPaths()) {
            return new SolveOutcome(SolveStatus.UNCONFIRMED, SolveReason.PATH_LIMIT_EXCEEDED, List.of());
        }
        List<PathResult> results = new ArrayList<>((int) Math.min(total, 4096));
        for (long i = 0; i < total; i++) {
            results.add(solvePath(enumerator.pathAt(i), fetch, mode, check));
        }
        return new SolveOutcome(aggregate(results), null, results);
    }

    /**
     * Giải mọi đường lựa chọn với ứng viên lấy theo {@code blockId}.
     */
    public SolveOutcome solveAll(List<SolvePart> parts, Map<String, List<PathCandidate>> candidatesByBlock,
            SolveMode mode) {
        Objects.requireNonNull(candidatesByBlock);
        return solveAll(parts,
                b -> Objects.requireNonNullElse(candidatesByBlock.get(b.blockId()), List.of()), mode);
    }

    // ---- nội bộ ----

    private PathResult solvePath(SolvePath path, Function<BlockQuota, List<PathCandidate>> fetch,
            SolveMode mode, Runnable checkDeadline) {
        try {
            checkDeadline.run();
            validateDistinctBlocks(path);
            Random rng = (mode instanceof SolveMode.Generate g) ? new Random(g.seed()) : null;
            List<BlockContext> contexts = new ArrayList<>(path.blocks().size());
            for (BlockQuota block : path.blocks()) {
                List<PathCandidate> raw = fetch.apply(block);
                if (raw == null) {
                    raw = List.of();
                }
                if (raw.size() > limits.maxCandidatesPerBlock()) {
                    return PathResult.unconfirmed(path, SolveReason.CANDIDATE_LIMIT_EXCEEDED,
                            block.blockId());
                }
                List<PathCandidate> ordered = (rng != null)
                        ? shuffled(raw, rng)
                        : raw;
                BlockContext ctx = Prep.prepare(block, ordered);
                String insufficient = insufficientReason(ctx);
                if (insufficient != null) {
                    return PathResult.invalid(path, SolveReason.INSUFFICIENT_SOURCE, block.blockId());
                }
                contexts.add(ctx);
                checkDeadline.run();
            }
            checkDeadline.run();
            if (Prep.isSimpleFlowCase(contexts)) {
                List<List<Integer>> picked = new MaxFlow(checkDeadline).solve(contexts);
                if (picked == null) {
                    return PathResult.invalid(path, SolveReason.NO_JOINT_SOLUTION, null);
                }
                return PathResult.valid(path, witness(path, contexts, picked));
            }
            List<BlockContext> searchOrder = mostConstrainedFirst(contexts);
            PathSearch search = new PathSearch(searchOrder, checkDeadline);
            if (!search.solve()) {
                return PathResult.invalid(path, SolveReason.NO_JOINT_SOLUTION, null);
            }
            return PathResult.valid(path, witnessFromSearch(path, contexts, searchOrder, search.witness()));
        } catch (DeadlineHit e) {
            return PathResult.unconfirmed(path, SolveReason.DEADLINE_EXCEEDED, null);
        }
    }

    /**
     * Kiểm tra nguồn thiếu rõ ràng của một khối trước khi giải (spec 5.4): tổng chỉ tiêu ma trận không
     * khớp số đơn vị của khối, tổng nguồn nhỏ hơn chỉ tiêu, hoặc nguồn của một chỉ tiêu nhỏ hơn quota.
     *
     * @return mô tả thiếu, hoặc null khi nguồn đủ để thử giải
     */
    private String insufficientReason(BlockContext ctx) {
        BlockQuota block = ctx.quota();
        if (ctx.primaryCount() > 0) {
            long primarySum = 0;
            for (int r = 0; r < ctx.primaryCount(); r++) {
                primarySum += ctx.reqs().get(r).quota();
            }
            if (primarySum != (long) block.questionCount() * ctx.primaryDim()) {
                return "quota-sum " + primarySum + " != questionCount " + block.questionCount()
                        + " x dim " + ctx.primaryDim();
            }
        }
        long supplyUnits = 0;
        boolean[] usable = ctx.usable();
        int[] provided = ctx.unitsProvided();
        int n = ctx.cands().size();
        for (int i = 0; i < n; i++) {
            if (usable[i]) {
                supplyUnits += provided[i];
            }
        }
        if (supplyUnits < block.questionCount()) {
            return "supply " + supplyUnits + " < questionCount " + block.questionCount();
        }
        int[][] contrib = ctx.contrib();
        for (int r = 0; r < ctx.reqs().size(); r++) {
            int supply = 0;
            for (int i = 0; i < n; i++) {
                if (usable[i]) {
                    supply += contrib[i][r];
                }
            }
            if (supply < ctx.reqs().get(r).quota()) {
                return "supply " + supply + " < quota " + ctx.reqs().get(r).quota()
                        + " at req " + ctx.reqs().get(r).key();
            }
        }
        return null;
    }

    /** Thứ tự giải: khối chặt nhất trước (ít đơn vị ứng viên so với nhu cầu). */
    private List<BlockContext> mostConstrainedFirst(List<BlockContext> contexts) {
        record Tightness(BlockContext ctx, double ratio, int usable) {
        }
        List<Tightness> scored = new ArrayList<>(contexts.size());
        for (BlockContext ctx : contexts) {
            long units = 0;
            int usable = 0;
            for (int i = 0; i < ctx.cands().size(); i++) {
                if (ctx.usable()[i]) {
                    units += ctx.unitsProvided()[i];
                    usable++;
                }
            }
            double ratio = units / (double) Math.max(ctx.quota().questionCount(), 1);
            scored.add(new Tightness(ctx, ratio, usable));
        }
        scored.sort(Comparator.comparingDouble(Tightness::ratio).thenComparingInt(Tightness::usable));
        List<BlockContext> out = new ArrayList<>(contexts.size());
        for (Tightness t : scored) {
            out.add(t.ctx());
        }
        return out;
    }

    private List<BlockWitness> witness(SolvePath path, List<BlockContext> contexts,
            List<List<Integer>> pickedByBlock) {
        List<BlockWitness> out = new ArrayList<>(contexts.size());
        for (int b = 0; b < contexts.size(); b++) {
            BlockContext ctx = contexts.get(b);
            List<QuestionPick> picks = new ArrayList<>();
            for (int i : pickedByBlock.get(b)) {
                PathCandidate c = ctx.cands().get(i);
                picks.add(new QuestionPick(c.questionId(), c.versionNo()));
            }
            out.add(new BlockWitness(ctx.quota().blockId(), picks));
        }
        return out;
    }

    /** Ánh xạ kết quả tìm kiếm (theo thứ tự giải) về thứ tự khối của đường. */
    private List<BlockWitness> witnessFromSearch(SolvePath path, List<BlockContext> pathOrder,
            List<BlockContext> searchOrder, List<List<Integer>> pickedBySearchBlock) {
        List<BlockWitness> out = new ArrayList<>(pathOrder.size());
        List<BlockWitness> bySearch = new ArrayList<>(searchOrder.size());
        for (int b = 0; b < searchOrder.size(); b++) {
            BlockContext ctx = searchOrder.get(b);
            List<QuestionPick> picks = new ArrayList<>();
            for (int i : pickedBySearchBlock.get(b)) {
                PathCandidate c = ctx.cands().get(i);
                picks.add(new QuestionPick(c.questionId(), c.versionNo()));
            }
            bySearch.add(new BlockWitness(ctx.quota().blockId(), picks));
        }
        Map<String, BlockWitness> byId = new HashMap<>();
        for (BlockWitness w : bySearch) {
            byId.put(w.blockId(), w);
        }
        for (BlockQuota q : path.blocks()) {
            out.add(byId.get(q.blockId()));
        }
        return out;
    }

    private static Function<BlockQuota, List<PathCandidate>> memoized(
            Function<BlockQuota, List<PathCandidate>> fetch) {
        Map<String, List<PathCandidate>> cache = new HashMap<>();
        return b -> cache.computeIfAbsent(b.blockId(), id -> fetch.apply(b));
    }

    private static List<PathCandidate> shuffled(List<PathCandidate> raw, Random rng) {
        List<PathCandidate> copy = new ArrayList<>(raw);
        Collections.shuffle(copy, rng);
        return copy;
    }

    private static void validateDistinctBlocks(SolvePath path) {
        Set<String> seen = new HashSet<>();
        for (BlockQuota b : path.blocks()) {
            if (!seen.add(b.blockId())) {
                throw new IllegalArgumentException("duplicate blockId on path: " + b.blockId());
            }
        }
    }

    private static SolveStatus aggregate(List<PathResult> results) {
        SolveStatus worst = SolveStatus.VALID;
        for (PathResult r : results) {
            switch (r.status()) {
                case INVALID -> {
                    return SolveStatus.INVALID;
                }
                case UNCONFIRMED -> worst = SolveStatus.UNCONFIRMED;
                case VALID -> {
                }
            }
        }
        return worst;
    }
}

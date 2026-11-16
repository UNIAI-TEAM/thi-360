package vn.unicom.thi360.exam.domain.solver;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Chuẩn bị một khối cho việc giải: dịch {@link BlockQuota} thành chỉ tiêu nội bộ và lọc ứng viên.
 *
 * <p>Quy tắc lọc (spec 5.4): ứng viên phải qua được điều kiện lọc của khối (điểm mỗi đơn vị bắt buộc,
 * quy tắc chấm mong đợi, kích thước bộ khi khối là khối chùm); mọi đơn vị đếm của nó phải khớp ít nhất
 * một chỉ tiêu trong <b>mỗi chiều bắt buộc</b> của ma trận — COMBINED: một ô; SEPARATE: từng chiều có khai
 * báo (một đơn vị được đếm đồng thời vào chỉ tiêu lớp và chỉ tiêu độ khó). Đơn vị không được đếm vào chỗ
 * vượt ngoài ma trận nên đơn vị không khớp chiều nào thì ứng viên mất giá trị. Ứng viên cũng không được
 * đóng góp vào chỉ tiêu nào có quota 0, và không được trùng khóa trong chính nó.
 */
final class Prep {

    private Prep() {
    }

    /** Dịch ma trận và các chỉ tiêu bổ sung của khối thành mảng chỉ tiêu nội bộ. */
    static List<Requirement> requirements(BlockQuota block) {
        List<Requirement> reqs = new ArrayList<>();
        switch (block.matrix()) {
            case MatrixQuota.Combined combined -> {
                for (CellQuota cell : combined.cells()) {
                    reqs.add(Requirement.combinedCell(cell));
                }
            }
            case MatrixQuota.Separate separate -> {
                for (DimensionQuota g : separate.grades()) {
                    reqs.add(Requirement.separateGrade(g));
                }
                for (DimensionQuota d : separate.difficulties()) {
                    reqs.add(Requirement.separateDifficulty(d));
                }
            }
        }
        for (CompetencyQuota c : block.competencies()) {
            reqs.add(Requirement.competency(c));
        }
        for (MapidGroupQuota g : block.mapidGroups()) {
            reqs.add(Requirement.mapidGroup(g));
        }
        return reqs;
    }

    /** Số chỉ tiêu đầu thuộc ma trận (trước năng lực và nhóm MAPID). */
    static int primaryCount(BlockQuota block) {
        return switch (block.matrix()) {
            case MatrixQuota.Combined combined -> combined.cells().size();
            case MatrixQuota.Separate separate -> separate.grades().size() + separate.difficulties().size();
        };
    }

    /**
     * Số chiều bắt buộc một đơn vị được phép khớp trong ma trận.
     * COMBINED: 1; SEPARATE có cả hai chiều: 2; SEPARATE một chiều: 1.
     */
    static int primaryDim(BlockQuota block) {
        return switch (block.matrix()) {
            case MatrixQuota.Combined ignored -> 1;
            case MatrixQuota.Separate separate -> {
                int dims = 0;
                if (!separate.grades().isEmpty()) {
                    dims++;
                }
                if (!separate.difficulties().isEmpty()) {
                    dims++;
                }
                yield Math.max(dims, 1);
            }
        };
    }

    /**
     * Nhóm chỉ tiêu mà mọi đơn vị đếm phải khớp ít nhất một phần tử: với COMBINED là nhóm ô; với SEPARATE
     * là từng chiều có khai báo. Khối không có chiều chính (chỉ chỉ tiêu bổ sung) yêu cầu khớp ít nhất
     * một chỉ tiêu bất kỳ.
     */
    static int[][] requiredGroups(BlockQuota block, List<Requirement> reqs, int primaryCount) {
        List<int[]> groups = new ArrayList<>(2);
        switch (block.matrix()) {
            case MatrixQuota.Combined ignored -> {
                if (primaryCount > 0) {
                    groups.add(positive(reqs, 0, primaryCount));
                }
            }
            case MatrixQuota.Separate separate -> {
                int g = separate.grades().size();
                int d = separate.difficulties().size();
                if (g > 0) {
                    groups.add(positive(reqs, 0, g));
                }
                if (d > 0) {
                    groups.add(positive(reqs, g, g + d));
                }
            }
        }
        if (groups.isEmpty() && !reqs.isEmpty()) {
            groups.add(positive(reqs, 0, reqs.size()));
        }
        return groups.toArray(int[][]::new);
    }

    /** Chỉ số chỉ tiêu có quota &gt; 0 trong khoảng; quota 0 chỉ để loại, không để khớp chiều. */
    private static int[] positive(List<Requirement> reqs, int from, int to) {
        List<Integer> out = new ArrayList<>(to - from);
        for (int r = from; r < to; r++) {
            if (reqs.get(r).quota() > 0) {
                out.add(r);
            }
        }
        int[] res = new int[out.size()];
        for (int i = 0; i < res.length; i++) {
            res[i] = out.get(i);
        }
        return res;
    }

    /**
     * Dựng {@link BlockContext}: tính vector đóng góp của từng ứng viên và đánh dấu usable.
     *
     * <p>Giữ nguyên thứ tự {@code candidates} (đã được xáo từ trước nếu cần); ứng viên không usable vẫn
     * nằm trong mảng nhưng cờ usable = false. {@code deadlineCheck} được gọi định kỳ trong vòng lặp lọc
     * để hạn thời gian được tôn trọng cả khi danh sách ứng viên rất lớn.
     */
    static BlockContext prepare(BlockQuota block, List<PathCandidate> candidates,
            Runnable deadlineCheck) {
        List<Requirement> reqs = requirements(block);
        int rCount = reqs.size();
        int n = candidates.size();
        int[][] contrib = new int[n][];
        int[] unitsProvided = new int[n];
        String[][] keys = new String[n][];
        boolean[] usable = new boolean[n];
        BigDecimal requiredPoints = block.requiredPointsPerUnit();
        String expectedScoring = block.expectedScoringRule();
        Integer setSize = block.setCount() != null ? block.setSize() : null;
        int[][] requiredGroups = requiredGroups(block, reqs, primaryCount(block));

        for (int i = 0; i < n; i++) {
            if ((i & 0x3FF) == 0) {
                deadlineCheck.run();
            }
            PathCandidate c = candidates.get(i);
            int[] vec = new int[rCount];
            contrib[i] = vec;
            keys[i] = keysOf(c);
            usable[i] = false;
            unitsProvided[i] = c.units().size();

            if (c.units().isEmpty()) {
                continue;
            }
            if (setSize != null && c.units().size() != setSize) {
                continue;
            }
            if (expectedScoring != null && !expectedScoring.equals(c.scoringRule())) {
                continue;
            }
            if (requiredPoints != null) {
                boolean pointsOk = true;
                for (CountingUnit u : c.units()) {
                    if (u.points() == null || u.points().compareTo(requiredPoints) != 0) {
                        pointsOk = false;
                        break;
                    }
                }
                if (!pointsOk) {
                    continue;
                }
            }
            if (hasInternalKeyCollision(keys[i])) {
                continue;
            }

            boolean allUnitsHit = true;
            for (CountingUnit u : c.units()) {
                for (int r = 0; r < rCount; r++) {
                    if (reqs.get(r).matcher().test(u)) {
                        vec[r]++;
                    }
                }
                for (int[] group : requiredGroups) {
                    boolean covered = false;
                    for (int r : group) {
                        if (reqs.get(r).matcher().test(u)) {
                            covered = true;
                            break;
                        }
                    }
                    if (!covered) {
                        allUnitsHit = false;
                        break;
                    }
                }
            }
            boolean hitsZeroQuota = false;
            for (int r = 0; r < rCount; r++) {
                if (vec[r] > 0 && reqs.get(r).quota() == 0) {
                    hitsZeroQuota = true;
                    break;
                }
            }
            usable[i] = allUnitsHit && !hitsZeroQuota;
        }
        return new BlockContext(block, reqs, primaryCount(block), primaryDim(block), candidates,
                contrib, unitsProvided, keys, usable);
    }

    /** Khóa duy nhất của ứng viên: questionId + mọi childId khác null của các đơn vị. */
    static String[] keysOf(PathCandidate c) {
        List<String> out = new ArrayList<>(c.units().size() + 1);
        out.add(c.questionId());
        for (CountingUnit u : c.units()) {
            if (u.childId() != null) {
                out.add(u.childId());
            }
        }
        return out.toArray(String[]::new);
    }

    private static boolean hasInternalKeyCollision(String[] keys) {
        Set<String> seen = new HashSet<>(keys.length * 2);
        for (String k : keys) {
            if (!seen.add(k)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Trường hợp đơn giản giải được bằng mạng luồng (spec 5.4): mọi khối chỉ có chỉ tiêu COMBINED hoặc
     * một chiều SEPARATE, không năng lực, không nhóm MAPID, không khối chùm, và mọi ứng viên usable
     * có đúng một đơn vị đếm.
     */
    static boolean isSimpleFlowCase(List<BlockContext> blocks) {
        for (BlockContext b : blocks) {
            if (!(b.quota().matrix() instanceof MatrixQuota.Combined)
                    && !(b.quota().matrix() instanceof MatrixQuota.Separate sep
                            && (sep.grades().isEmpty() || sep.difficulties().isEmpty()))) {
                return false;
            }
            if (b.quota().setCount() != null || !b.quota().competencies().isEmpty()
                    || !b.quota().mapidGroups().isEmpty()) {
                return false;
            }
            List<PathCandidate> cands = b.cands();
            for (int i = 0; i < cands.size(); i++) {
                if (b.usable()[i] && cands.get(i).units().size() != 1) {
                    return false;
                }
            }
        }
        return true;
    }
}

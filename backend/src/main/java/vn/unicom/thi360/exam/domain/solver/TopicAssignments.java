package vn.unicom.thi360.exam.domain.solver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Assigns each selected counting unit to at most one quota per scope. */
final class TopicAssignments {
    private TopicAssignments() {}

    record Pick(int group, int candidate) {}
    private record Unit(Pick pick, int index, String groupId, CountingUnit value) {}
    private record Scope(String level, String id, List<TopicQuota> quotas) {}

    static boolean hasQuotas(Prep.SubproblemContext ctx) {
        for (SectionOnPath section : ctx.sections()) {
            if (!section.subjectQuota().topicQuotas().isEmpty()
                    || !section.subjectQuota().examQuotas().isEmpty()) return true;
        }
        for (Prep.ResolvedGroup resolved : ctx.groups()) {
            GroupQuota group = resolved.group();
            if (!group.topicQuotas().isEmpty()
                    || (group.slotParent() != null && !group.slotParent().topicQuotas().isEmpty())) return true;
        }
        return false;
    }

    /**
     * Chữ ký lớp đóng góp theo chủ đề của một ứng viên trong nhóm g (BE4B-R1-01): định danh phạm
     * vi (section + phạm vi chủ đề riêng của nhóm, tức id nhóm hoặc nhóm cha của slot) cộng với
     * mẫu bit khớp từng chỉ tiêu trên từng đơn vị. Hai phạm vi khác nhau phải khác lớp kể cả khi
     * mẫu bit giống nhau, vì nguồn tính vào chỉ tiêu tối thiểu của mỗi phạm vi là riêng — nếu
     * gộp, trạng thái thất bại của một cách phân phối giữa các phạm vi sẽ cắt nhầm cách phân phối
     * khác có cùng tập khóa đã chọn. {@code assign()} cũng dùng cùng cặp phạm vi này để gom đơn
     * vị vào từng scope.
     */
    static String signature(Prep.SubproblemContext ctx, int g, PathCandidate candidate) {
        GroupQuota group = ctx.groups().get(g).group();
        String id = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
        SectionOnPath section = ctx.sections().get(ctx.sectionOf()[g]);
        List<TopicQuota> sectionQuotas = section.subjectQuota().topicQuotas();
        List<TopicQuota> own = group.slotParent() == null ? group.topicQuotas() : group.slotParent().topicQuotas();
        StringBuilder signature = new StringBuilder();
        signature.append(section.sectionId()).append('@').append(id);
        for (CountingUnit unit : candidate.units()) {
            signature.append('|');
            for (TopicQuota quota : sectionQuotas) signature.append(quota.matches(id, unit) ? '1' : '0');
            signature.append('/');
            for (TopicQuota quota : own) signature.append(quota.matches(id, unit) ? '1' : '0');
            if (!section.subjectQuota().examQuotas().isEmpty()) {
                signature.append(':').append(unit.gradeCode()).append(':').append(unit.difficultyCode());
            }
        }
        return signature.toString();
    }

    static int eligibilityScore(Prep.SubproblemContext ctx, int g, PathCandidate candidate) {
        GroupQuota group = ctx.groups().get(g).group();
        String id = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
        List<TopicQuota> section = ctx.sections().get(ctx.sectionOf()[g]).subjectQuota().topicQuotas();
        List<TopicQuota> own = group.slotParent() == null ? group.topicQuotas() : group.slotParent().topicQuotas();
        int score = 0;
        for (CountingUnit unit : candidate.units()) {
            for (TopicQuota quota : section) if (quota.count() > 0 && quota.matches(id, unit)) score++;
            for (TopicQuota quota : own) if (quota.count() > 0 && quota.matches(id, unit)) score++;
        }
        return score;
    }

    /** Null means the selected units cannot meet all quotas. */
    static Map<Pick, List<TopicAssignment>> assign(Prep.SubproblemContext ctx,
            List<List<Integer>> selected, Runnable deadline) {
        if (!examQuotasMet(ctx, selected, deadline)) return null;
        Map<Scope, List<Unit>> scopes = new LinkedHashMap<>();
        for (int s = 0; s < ctx.sections().size(); s++) {
            SectionOnPath section = ctx.sections().get(s);
            if (!section.subjectQuota().topicQuotas().isEmpty()) {
                scopes.put(new Scope("SECTION", section.sectionId(), section.subjectQuota().topicQuotas()),
                        new ArrayList<>());
            }
        }
        for (int g = 0; g < ctx.groups().size(); g++) {
            GroupQuota group = ctx.groups().get(g).group();
            String id = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
            List<TopicQuota> quotas = group.slotParent() == null ? group.topicQuotas() : group.slotParent().topicQuotas();
            Scope own = new Scope("GROUP", id, quotas);
            Scope section = new Scope("SECTION", ctx.sections().get(ctx.sectionOf()[g]).sectionId(),
                    ctx.sections().get(ctx.sectionOf()[g]).subjectQuota().topicQuotas());
            if (!quotas.isEmpty()) scopes.computeIfAbsent(own, ignored -> new ArrayList<>());
            for (int i : selected.get(g)) {
                PathCandidate candidate = ctx.cands().get(g).get(i);
                for (int u = 0; u < candidate.units().size(); u++) {
                    Unit unit = new Unit(new Pick(g, i), u, id, candidate.units().get(u));
                    if (scopes.containsKey(section)) scopes.get(section).add(unit);
                    if (!quotas.isEmpty()) scopes.get(own).add(unit);
                }
            }
        }
        return solveScopes(scopes, deadline);
    }

    static Map<Pick, List<TopicAssignment>> reassign(SectionOnPath section, SubjectWitness witness,
            Map<String, PathCandidate> candidates, QuestionPick dropped) {
        boolean hasTopics = !section.subjectQuota().topicQuotas().isEmpty()
                || section.groups().stream().anyMatch(group -> !group.topicQuotas().isEmpty()
                        || (group.slotParent() != null && !group.slotParent().topicQuotas().isEmpty()));
        if (!hasTopics) return Map.of();
        Map<String, GroupQuota> groups = new HashMap<>();
        for (GroupQuota group : section.groups()) groups.put(group.groupId(), group);
        Map<Scope, List<Unit>> scopes = new LinkedHashMap<>();
        Scope sectionScope = new Scope("SECTION", section.sectionId(), section.subjectQuota().topicQuotas());
        if (!sectionScope.quotas().isEmpty()) scopes.put(sectionScope, new ArrayList<>());
        for (int g = 0; g < witness.groups().size(); g++) {
            GroupWitness groupWitness = witness.groups().get(g);
            GroupQuota group = groups.get(groupWitness.groupId());
            String id = group.slotParent() == null ? group.groupId() : group.slotParent().parentGroupId();
            List<TopicQuota> quotas = group.slotParent() == null ? group.topicQuotas() : group.slotParent().topicQuotas();
            Scope own = new Scope("GROUP", id, quotas);
            if (!quotas.isEmpty()) scopes.computeIfAbsent(own, ignored -> new ArrayList<>());
            for (int i = 0; i < groupWitness.picks().size(); i++) {
                QuestionPick pick = groupWitness.picks().get(i);
                if (pick.equals(dropped)) continue;
                PathCandidate candidate = candidates.get(pick.questionId());
                for (int u = 0; u < candidate.units().size(); u++) {
                    Unit unit = new Unit(new Pick(g, i), u, id, candidate.units().get(u));
                    if (scopes.containsKey(sectionScope)) scopes.get(sectionScope).add(unit);
                    if (!quotas.isEmpty()) scopes.get(own).add(unit);
                }
            }
        }
        return solveScopes(scopes, () -> {});
    }

    private static Map<Pick, List<TopicAssignment>> solveScopes(Map<Scope, List<Unit>> scopes,
            Runnable deadline) {
        return solveTopicScopes(scopes, deadline);
    }

    private static boolean examQuotasMet(Prep.SubproblemContext ctx, List<List<Integer>> selected,
            Runnable deadline) {
        List<ExamDimensionQuota> quotas = ctx.sections().stream()
                .flatMap(s -> s.subjectQuota().examQuotas().stream()).distinct().toList();
        if (quotas.isEmpty()) return true;
        List<CountingUnit> units = new ArrayList<>();
        for (int g = 0; g < ctx.groups().size(); g++) {
            for (int i : selected.get(g)) units.addAll(ctx.cands().get(g).get(i).units());
        }
        for (ExamDimensionQuota quota : quotas) {
            int n = quota.buckets().size();
            int[] counts = new int[n];
            java.math.BigDecimal[] fractions = new java.math.BigDecimal[n];
            java.math.BigDecimal sum = java.math.BigDecimal.ZERO;
            int floors = 0;
            for (int i = 0; i < n; i++) {
                var bucket = quota.buckets().get(i);
                if ((bucket.count() == null) == (bucket.percent() == null)) return false;
                java.math.BigDecimal value = bucket.count() != null
                        ? java.math.BigDecimal.valueOf(bucket.count())
                        : bucket.percent().multiply(java.math.BigDecimal.valueOf(units.size())).movePointLeft(2);
                counts[i] = value.intValue();
                fractions[i] = value.subtract(java.math.BigDecimal.valueOf(counts[i]));
                sum = sum.add(value);
                floors += counts[i];
            }
            int target = sum.setScale(0, java.math.RoundingMode.HALF_UP).intValue();
            if (target != units.size()) return false;
            List<Integer> order = new ArrayList<>();
            for (int i = 0; i < n; i++) order.add(i);
            order.sort((a, b) -> fractions[b].compareTo(fractions[a]));
            for (int i = 0; i < target - floors; i++) counts[order.get(i)]++;
            int sink = 1 + units.size() + n;
            MaxFlow.Dinic flow = new MaxFlow.Dinic(sink + 1);
            for (int i = 0; i < units.size(); i++) {
                if ((i & 1023) == 0) deadline.run();
                flow.addEdge(0, 1 + i, 1);
                String code = quota.dimension() == ExamDimensionQuota.Dimension.GRADE
                        ? units.get(i).gradeCode() : units.get(i).difficultyCode();
                for (int b = 0; b < n; b++) {
                    if (code != null && quota.buckets().get(b).codes().contains(code)) {
                        flow.addEdge(1 + i, 1 + units.size() + b, 1);
                    }
                }
            }
            for (int b = 0; b < n; b++) flow.addEdge(1 + units.size() + b, sink, counts[b]);
            if (flow.maxFlow(0, sink, deadline) != units.size()) return false;
        }
        return true;
    }

    private static Map<Pick, List<TopicAssignment>> solveTopicScopes(Map<Scope, List<Unit>> scopes,
            Runnable deadline) {
        Map<Pick, List<TopicAssignment>> result = new HashMap<>();
        for (var entry : scopes.entrySet()) {
            deadline.run();
            Scope scope = entry.getKey();
            List<Unit> units = entry.getValue();
            int source = 0, unitBase = 1, quotaBase = unitBase + units.size();
            int sink = quotaBase + scope.quotas().size();
            MaxFlow.Dinic flow = new MaxFlow.Dinic(sink + 1);
            int total = 0;
            for (int q = 0; q < scope.quotas().size(); q++) {
                int count = scope.quotas().get(q).count();
                total += count;
                flow.addEdge(quotaBase + q, sink, count);
            }
            List<int[]> edges = new ArrayList<>();
            for (int u = 0; u < units.size(); u++) {
                if ((u & 1023) == 0) deadline.run();
                flow.addEdge(source, unitBase + u, 1);
                for (int q = 0; q < scope.quotas().size(); q++) {
                    if (scope.quotas().get(q).matches(units.get(u).groupId(), units.get(u).value())) {
                        edges.add(new int[]{u, q, flow.addEdge(unitBase + u, quotaBase + q, 1)});
                    }
                }
            }
            if (flow.maxFlow(source, sink, deadline) != total) return null;
            for (int[] edge : edges) {
                if (flow.flow(edge[2]) == 0) continue;
                Unit unit = units.get(edge[0]);
                result.computeIfAbsent(unit.pick(), ignored -> new ArrayList<>()).add(
                        new TopicAssignment(scope.level(), scope.id(), scope.quotas().get(edge[1]).id(), unit.index()));
            }
        }
        return result;
    }
}
